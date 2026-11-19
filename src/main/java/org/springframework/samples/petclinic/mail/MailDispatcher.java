package org.springframework.samples.petclinic.mail;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.mail.internet.MimeMessage;

@Component
public class MailDispatcher {

	private final MailOutboxRepository repository;

	private final MailProperties properties;

	private final JavaMailSender sender;

	private final Clock clock;

	private final String from;

	private final TransactionTemplate transactions;

	public MailDispatcher(MailOutboxRepository repository, MailProperties properties, JavaMailSender sender,
			Clock clock, @Value("${spring.mail.username:}") String username, PlatformTransactionManager manager) {
		this.repository = repository;
		this.properties = properties;
		this.sender = sender;
		this.clock = clock;
		this.from = properties.from().isBlank() ? username : properties.from();
		this.transactions = new TransactionTemplate(manager);
	}

	@Scheduled(fixedDelayString = "${vetcore.mail.dispatch-interval:5s}")
	public int dispatchDue() {
		LocalDateTime now = LocalDateTime.now(this.clock);
		List<MailOutbox> rows = this.repository.findDue(now, PageRequest.of(0, this.properties.batchSize()));
		int sent = 0;
		for (MailOutbox candidate : rows) {
			Integer id = candidate.getId();
			if (this.repository.claim(id, now, now.plusMinutes(2)) != 1)
				continue;
			if (!this.properties.enabled()) {
				update(id, row -> row.setStatus(MailStatus.SKIPPED));
				continue;
			}
			try {
				MimeMessage message = this.sender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message,
						MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
				helper.setFrom(this.from, "VetCore");
				helper.setTo(candidate.getRecipient());
				helper.setSubject(candidate.getSubject());
				helper.setText(candidate.getBodyText(), candidate.getBodyHtml());
				this.sender.send(message);
				update(id, row -> {
					row.setStatus(MailStatus.SENT);
					row.setSentAt(LocalDateTime.now(this.clock));
					row.setLockedUntil(null);
				});
				sent++;
			}
			catch (Exception ex) {
				update(id, row -> {
					int attempts = row.getAttempts() + 1;
					row.setAttempts(attempts);
					row.setLastError(ex.getClass().getSimpleName());
					row.setLockedUntil(null);
					if (attempts >= this.properties.maxAttempts())
						row.setStatus(MailStatus.FAILED);
					else {
						row.setStatus(MailStatus.PENDING);
						long[] delays = { 1, 5, 15, 60 };
						row.setNextAttemptAt(
								LocalDateTime.now(this.clock).plusMinutes(delays[Math.min(attempts - 1, 3)]));
					}
				});
			}
		}
		return sent;
	}

	private void update(Integer id, java.util.function.Consumer<MailOutbox> action) {
		this.transactions.executeWithoutResult(status -> {
			MailOutbox row = this.repository.findById(id).orElseThrow();
			action.accept(row);
			this.repository.save(row);
		});
	}

	@Scheduled(fixedDelayString = "P1D")
	public int purgeSentLinkBodies() {
		return this.repository.purgeLinkBodies(LocalDateTime.now(this.clock).minusDays(7));
	}

}
