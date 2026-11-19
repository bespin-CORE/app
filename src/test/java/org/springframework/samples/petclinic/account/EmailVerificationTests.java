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
		user.setUsername("emailtest" + System.nanoTime());
		user.setDisplayName("Test");
		user.setPassword("encoded");
		user.setCreatedAt(LocalDateTime.now());
		user = users.saveAndFlush(user);
	}

	private String token() {
		String body = outbox.findAll().get(outbox.findAll().size() - 1).getBodyText();
		return body.substring(body.indexOf("token=") + 6).trim();
	}

	@Test
	void normalizesEmail() {
		emails.requestVerification(user, " User@Example.COM ");
		assertThat(emails.state(user.getId()).pending()).isEqualTo("user@example.com");
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
		emails.requestVerification(user, "a@example.com");
		String raw = token();
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.VERIFIED);
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.INVALID);
		assertThat(emails.verifiedEmail(user.getId())).contains("a@example.com");
	}

	@Test
	void expiredTokenIsInvalid() {
		emails.requestVerification(user, "a@example.com");
		String raw = token();
		AccountToken row = tokens.findAll().get(0);
		row.setExpiresAt(LocalDateTime.now().minusDays(1));
		tokens.saveAndFlush(row);
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.INVALID);
	}

	@Test
	void takenAddressIsRejected() {
		emails.requestVerification(user, "a@example.com");
		assertThat(emails.verify(token())).isEqualTo(UserEmailService.VerifyResult.VERIFIED);
		AppUser other = new AppUser();
		other.setUsername("other" + System.nanoTime());
		other.setDisplayName("Other");
		other.setPassword("encoded");
		other.setCreatedAt(LocalDateTime.now());
		other = users.saveAndFlush(other);
		emails.requestVerification(other, "a@example.com");
		assertThat(emails.verify(token())).isEqualTo(UserEmailService.VerifyResult.TAKEN);
		assertThat(emails.verifiedEmail(other.getId())).isEmpty();
	}

	@Test
	void resendLimitedToThreePerHour() {
		for (int i = 0; i < 3; i++)
			emails.requestVerification(user, "a@example.com");
		assertThatThrownBy(() -> emails.requestVerification(user, "a@example.com"))
			.isInstanceOf(UserEmailService.VerificationRateLimitedException.class);
		assertThat(outbox.count()).isEqualTo(3);
	}

	@Test
	void tokenNotStoredInPlain() {
		emails.requestVerification(user, "a@example.com");
		String raw = token();
		assertThat(tokens.findAll().get(0).getTokenHash()).isNotEqualTo(raw).hasSize(64);
	}

	@Test
	void changingEmailKeepsOldUntilVerified() {
		emails.requestVerification(user, "a@example.com");
		emails.verify(token());
		emails.requestVerification(user, "b@example.com");
		assertThat(emails.state(user.getId()))
			.isEqualTo(new UserEmailService.EmailState("a@example.com", "b@example.com"));
	}

	@Test
	void summaryMailAfterVerification() {
		TreatmentSchedule schedule = new TreatmentSchedule();
		schedule.setHospital(hospitals.findById(1).orElseThrow());
		schedule.setTreatmentName("Dental");
		schedule.setTreatmentDate(LocalDateTime.now(clock).toLocalDate().plusDays(2));
		schedule.setStartTime(LocalTime.NOON);
		schedule.setCapacity(1);
		schedule.setBookingOpensAt(LocalDateTime.now(clock).minusDays(1));
		schedule = schedules.saveAndFlush(schedule);
		reservations.book(user.getUsername(), schedule.getId(), "Pet", null);
		emails.requestVerification(user, "summary@example.com");
		String raw = token();
		assertThat(emails.verify(raw)).isEqualTo(UserEmailService.VerifyResult.VERIFIED);
		assertThat(outbox.findAll()).extracting(MailOutbox::getKind).contains(MailKind.RESERVATION_SUMMARY);
		assertThat(outbox.findAll()
			.stream()
			.filter(m -> m.getKind() == MailKind.RESERVATION_SUMMARY)
			.findFirst()
			.orElseThrow()
			.getBodyText()).contains("Dental");
	}

	@Test
	void signupRequiresEmail() throws Exception {
		String name = "signup" + System.nanoTime();
		mvc.perform(post("/signup").with(csrf())
			.param("username", name)
			.param("password", "password123")
			.param("displayName", "Test")).andExpect(model().attributeHasFieldErrors("signupForm", "email"));
		mvc.perform(post("/signup").with(csrf())
			.param("username", name)
			.param("password", "password123")
			.param("displayName", "Test")
			.param("email", "signup@example.com")).andExpect(redirectedUrl("/login"));
		assertThat(outbox.findAll()).extracting(MailOutbox::getRecipient).containsExactly("signup@example.com");
	}

}
