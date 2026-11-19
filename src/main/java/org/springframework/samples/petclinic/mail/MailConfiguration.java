package org.springframework.samples.petclinic.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

	MailConfiguration(MailProperties properties, @Value("${spring.mail.username:}") String username,
			@Value("${spring.mail.password:}") String password) {
		if (properties.enabled() && (username.isBlank() || password.isBlank())) {
			throw new IllegalStateException("Mail credentials must be configured when mail is enabled");
		}
	}

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(name = "vetcore.scheduling.enabled", havingValue = "true", matchIfMissing = true)
	static class SchedulingConfiguration {

	}

}
