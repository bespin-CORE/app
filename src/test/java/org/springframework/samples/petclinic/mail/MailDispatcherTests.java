package org.springframework.samples.petclinic.mail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"spring.mail.username=test@example.com", "spring.mail.password=x" })
@Import({ MailTestConfig.class, MailDispatcherTests.FixedClock.class })
class MailDispatcherTests {

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		}

	}

	@Autowired
	MailOutboxService outbox;

	@Autowired
	MailOutboxRepository repository;

	@Autowired
	MailDispatcher dispatcher;

	@Autowired
	RecordingMailSender sender;

	@Autowired
	Clock clock;

	@BeforeEach
	void clean() {
		repository.deleteAll();
		sender.reset();
	}

	private void enqueue() {
		outbox.enqueue(MailKind.VERIFY_EMAIL, "target@example.com", new ComposedMail("subject", "text", "<p>text</p>"));
	}

	@Test
	void sendsPendingAndMarksSent() throws Exception {
		enqueue();
		assertThat(dispatcher.dispatchDue()).isEqualTo(1);
		assertThat(sender.sent()).hasSize(1);
		assertThat(sender.sent().get(0).getSubject()).isEqualTo("subject");
		assertThat(sender.sent().get(0).getAllRecipients()[0].toString()).isEqualTo("target@example.com");
		assertThat(repository.findAll().get(0).getStatus()).isEqualTo(MailStatus.SENT);
	}

	@Test
	void failureSchedulesRetry() {
		enqueue();
		sender.failNext(1);
		dispatcher.dispatchDue();
		MailOutbox row = repository.findAll().get(0);
		assertThat(row.getStatus()).isEqualTo(MailStatus.PENDING);
		assertThat(row.getAttempts()).isEqualTo(1);
		assertThat(row.getNextAttemptAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(1));
		assertThat(row.getLastError()).isEqualTo("MailSendException");
	}

	@Test
	void givesUpAfterFiveAttempts() {
		enqueue();
		sender.failNext(5);
		for (int i = 0; i < 5; i++) {
			MailOutbox row = repository.findAll().get(0);
			row.setNextAttemptAt(LocalDateTime.now(clock));
			repository.saveAndFlush(row);
			dispatcher.dispatchDue();
		}
		assertThat(repository.findAll().get(0).getStatus()).isEqualTo(MailStatus.FAILED);
	}

	@Test
	void oneFailureDoesNotStopBatch() {
		enqueue();
		enqueue();
		enqueue();
		sender.failNext(1);
		assertThat(dispatcher.dispatchDue()).isEqualTo(2);
		assertThat(sender.sent()).hasSize(2);
	}

	@Test
	void expiredLockIsRetaken() {
		enqueue();
		MailOutbox row = repository.findAll().get(0);
		row.setStatus(MailStatus.SENDING);
		row.setLockedUntil(LocalDateTime.now(clock).minusSeconds(1));
		repository.saveAndFlush(row);
		assertThat(dispatcher.dispatchDue()).isEqualTo(1);
	}

	@Test
	void enqueueOnceDeduplicates() {
		ComposedMail mail = new ComposedMail("a", "b", "c");
		assertThat(outbox.enqueueOnce(MailKind.VERIFY_EMAIL, "a@example.com", mail, "key")).isTrue();
		assertThat(outbox.enqueueOnce(MailKind.VERIFY_EMAIL, "a@example.com", mail, "key")).isFalse();
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void purgeClearsOldLinkBodies() {
		enqueue();
		dispatcher.dispatchDue();
		MailOutbox row = repository.findAll().get(0);
		row.setSentAt(LocalDateTime.now(clock).minusDays(8));
		repository.saveAndFlush(row);
		outbox.enqueue(MailKind.RESERVATION_BOOKED, "a@example.com", new ComposedMail("a", "b", "c"));
		dispatcher.dispatchDue();
		assertThat(dispatcher.purgeSentLinkBodies()).isEqualTo(1);
		assertThat(repository.findById(row.getId()).orElseThrow().getBodyText()).isNull();
		assertThat(repository.findAll()
			.stream()
			.filter(m -> m.getKind() == MailKind.RESERVATION_BOOKED)
			.findFirst()
			.orElseThrow()
			.getBodyText()).isEqualTo("b");
	}

	@Test
	void enqueueOnceDoesNotHideOtherConstraintErrors() {
		assertThatThrownBy(() -> outbox.enqueueOnce(MailKind.VERIFY_EMAIL, "a@example.com",
				new ComposedMail("a".repeat(201), "b", "c"), "new-key"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
