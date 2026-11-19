package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.LocalTime;
import org.springframework.samples.petclinic.reservation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.mail.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"spring.mail.username=test@example.com", "spring.mail.password=x" })
@Import(MailTestConfig.class)
@AutoConfigureMockMvc
class EmailVerificationTests {

	@Autowired
	UserEmailService emails;

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emailRows;

	@Autowired
	AccountTokenRepository tokens;

	@Autowired
	MailOutboxRepository outbox;

	@Autowired
	ReservationService reservations;

	@Autowired
	TreatmentScheduleRepository schedules;

	@Autowired
	PartnerHospitalRepository hospitals;

	@Autowired
	Clock clock;

	private AppUser user;

	@BeforeEach
	void setup() {
		outbox.deleteAll();
		tokens.deleteAll();
		emailRows.deleteAll();
		user = new AppUser();
		user.setUsername("v" + System.nanoTime() + "@x.co");
		user.setDisplayName("Test");
		user.setPassword("encoded");
		user.setCreatedAt(LocalDateTime.now());
		user = users.saveAndFlush(user);
	}

	private String token() {
		String body = outbox.findAll().get(outbox.findAll().size() - 1).getBodyText();
		return body.substring(body.indexOf("token=") + 6).split("\\s+", 2)[0];
	}

	@Test
	void normalizesEmail() {
		emails.requestVerification(user, " " + user.getUsername().toUpperCase(java.util.Locale.ROOT) + " ");
		assertThat(emailRows.findById(user.getId()).orElseThrow().getPendingEmail()).isEqualTo(user.getUsername());
	}

	@Test
	void rejectsHeaderInjection() {
		assertThat(EmailAddresses.isValid("a@b.com\r\nBcc: x@y.com")).isFalse();
		assertThatThrownBy(() -> emails.requestVerification(user, "a@b.com\r\nBcc: x@y.com"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(outbox.count()).isZero();
	}

	@Test
	void verifyLinkVerifiesOnce() {
		emails.requestVerification(user, user.getUsername());
		String raw = token();
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.VERIFIED);
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.INVALID);
		assertThat(emails.verifiedEmail(user.getId())).contains(user.getUsername());
	}

	@Test
	void expiredTokenIsInvalid() {
		emails.requestVerification(user, user.getUsername());
		String raw = token();
		AccountToken row = tokens.findAll().get(0);
		row.setExpiresAt(LocalDateTime.now().minusDays(1));
		tokens.saveAndFlush(row);
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.INVALID);
	}

	@Test
	void resendLimitedToThreePerHour() {
		for (int i = 0; i < 3; i++)
			emails.requestVerification(user, user.getUsername());
		assertThatThrownBy(() -> emails.requestVerification(user, user.getUsername()))
			.isInstanceOf(UserEmailService.VerificationRateLimitedException.class);
		assertThat(outbox.count()).isEqualTo(3);
	}

	@Test
	void tokenNotStoredInPlain() {
		emails.requestVerification(user, user.getUsername());
		String raw = token();
		assertThat(tokens.findAll().get(0).getTokenHash()).isNotEqualTo(raw).hasSize(64);
	}

}
