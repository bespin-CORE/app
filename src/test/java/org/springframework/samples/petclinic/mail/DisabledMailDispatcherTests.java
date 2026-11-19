package org.springframework.samples.petclinic.mail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=false" })
@Import(MailTestConfig.class)
class DisabledMailDispatcherTests {

	@Autowired
	MailOutboxService outbox;

	@Autowired
	MailOutboxRepository repository;

	@Autowired
	MailDispatcher dispatcher;

	@Autowired
	RecordingMailSender sender;

	@Test
	void disabledMarksSkipped() {
		repository.deleteAll();
		sender.reset();
		outbox.enqueue(MailKind.VERIFY_EMAIL, "a@example.com", new ComposedMail("a", "b", "c"));
		assertThat(dispatcher.dispatchDue()).isZero();
		assertThat(sender.sent()).isEmpty();
		assertThat(repository.findAll().get(0).getStatus()).isEqualTo(MailStatus.SKIPPED);
	}

}
