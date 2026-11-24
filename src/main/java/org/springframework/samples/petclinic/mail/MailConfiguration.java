package org.springframework.samples.petclinic.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
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
		if (properties.enabled() && (!properties.hasValidBaseUrl() || properties.resolvedDomain().isBlank())) {
			throw new IllegalStateException("Mail base URL and domain must be valid when mail is enabled");
		}
	}

	@Bean
	static BeanPostProcessor mailSenderDomainCustomizer(ObjectProvider<MailProperties> properties) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String name) {
				if (bean instanceof JavaMailSenderImpl sender) {
					String domain = properties.getObject().resolvedDomain();
					if (!domain.isBlank())
						sender.getSession().getProperties().setProperty("mail.smtp.localhost", domain);
				}
				return bean;
			}
		};
	}

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(name = "vetcore.scheduling.enabled", havingValue = "true", matchIfMissing = true)
	static class SchedulingConfiguration {

	}

}
