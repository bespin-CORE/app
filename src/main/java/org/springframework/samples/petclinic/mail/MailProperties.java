package org.springframework.samples.petclinic.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("vetcore.mail")
public record MailProperties(@DefaultValue("false") boolean enabled,
		@DefaultValue("http://localhost:8080") String baseUrl, @DefaultValue("") String from,
		@DefaultValue("20") int batchSize, @DefaultValue("5s") Duration dispatchInterval,
		@DefaultValue("5") int maxAttempts) {
}
