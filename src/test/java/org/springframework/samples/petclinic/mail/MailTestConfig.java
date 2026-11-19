package org.springframework.samples.petclinic.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MailTestConfig {

	@Bean
	@Primary
	RecordingMailSender recordingMailSender() {
		return new RecordingMailSender();
	}

}
