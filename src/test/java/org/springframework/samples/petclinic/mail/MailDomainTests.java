package org.springframework.samples.petclinic.mail;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class MailDomainTests {

	private MailProperties properties(String base, String domain) {
		return new MailProperties(true, base, "", 20, Duration.ofSeconds(5), 5, domain);
	}

	@Test
	void domainDerivedFromBaseUrl() {
		assertThat(properties("https://example.org:8443/x", "").resolvedDomain()).isEqualTo("example.org");
	}

	@Test
	void explicitDomainOverridesBaseUrl() {
		assertThat(properties("https://example.org/x", "mail.example.net").resolvedDomain())
			.isEqualTo("mail.example.net");
	}

	@Test
	void invalidBaseUrlFailsStartupWhenEnabled() {
		new ApplicationContextRunner().withUserConfiguration(MailConfiguration.class)
			.withPropertyValues("vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
					"vetcore.mail.base-url=not-an-absolute-url", "vetcore.mail.domain=mail.example.org",
					"spring.mail.username=test@example.com", "spring.mail.password=x")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
					.hasStackTraceContaining("Mail base URL and domain must be valid");
			});
	}

}
