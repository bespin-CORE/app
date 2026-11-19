package org.springframework.samples.petclinic.mail;

import java.util.ArrayList;
import java.util.List;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public class RecordingMailSender extends JavaMailSenderImpl {

	private final List<MimeMessage> messages = new ArrayList<>();

	private int failures;

	@Override
	public void send(MimeMessage... messages) {
		if (this.failures > 0) {
			this.failures--;
			throw new MailSendException("Simulated failure");
		}
		this.messages.addAll(List.of(messages));
	}

	public List<MimeMessage> sent() {
		return List.copyOf(this.messages);
	}

	public void failNext(int count) {
		this.failures = count;
	}

	public void reset() {
		this.messages.clear();
		this.failures = 0;
	}

}
