package org.springframework.samples.petclinic.mail;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MailOutboxService {

	private final MailOutboxRepository repository;

	private final Clock clock;

	private final TransactionTemplate separate;

	public MailOutboxService(MailOutboxRepository repository, Clock clock, PlatformTransactionManager manager) {
		this.repository = repository;
		this.clock = clock;
		this.separate = new TransactionTemplate(manager);
		this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public void enqueue(MailKind kind, String recipient, ComposedMail mail) {
		save(kind, recipient, mail, null);
	}

	public boolean enqueueOnce(MailKind kind, String recipient, ComposedMail mail, String key) {
		try {
			return Boolean.TRUE.equals(this.separate.execute(status -> {
				if (this.repository.existsByDedupeKey(key))
					return false;
				save(kind, recipient, mail, key);
				this.repository.flush();
				return true;
			}));
		}
		catch (DataIntegrityViolationException ex) {
			return false;
		}
	}

	private void save(MailKind kind, String recipient, ComposedMail mail, String key) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		MailOutbox row = new MailOutbox();
		row.setKind(kind);
		row.setRecipient(recipient);
		row.setSubject(mail.subject());
		row.setBodyText(mail.text());
		row.setBodyHtml(mail.html());
		row.setDedupeKey(key);
		row.setStatus(MailStatus.PENDING);
		row.setCreatedAt(now);
		row.setNextAttemptAt(now);
		this.repository.save(row);
	}

}
