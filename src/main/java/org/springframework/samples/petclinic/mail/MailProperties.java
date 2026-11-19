package org.springframework.samples.petclinic.mail;

import java.time.Duration;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("vetcore.mail")
public record MailProperties(@DefaultValue("false") boolean enabled,
		@DefaultValue("http://localhost:8080") String baseUrl, @DefaultValue("") String from,
		@DefaultValue("20") int batchSize, @DefaultValue("5s") Duration dispatchInterval,
		@DefaultValue("5") int maxAttempts, @DefaultValue("") String domain) {
	public String resolvedDomain() {
		if (domain != null && !domain.isBlank())
			return domain.trim();
		try {
			String host = URI.create(baseUrl).getHost();
			return host == null ? "" : host;
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return "";
		}
	}

	public boolean hasValidBaseUrl() {
		try {
			URI uri = URI.create(baseUrl);
			return uri.isAbsolute()
					&& ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null && !uri.getHost().isBlank();
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return false;
		}
	}
}
