package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "vetcore.scheduling.enabled=false")
@AutoConfigureMockMvc
class EmailLoginTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	org.springframework.samples.petclinic.mail.MailOutboxRepository outbox;

	AppUser user;

	@BeforeEach
	void setup() {
		user = new AppUser();
		user.setUsername("l" + System.nanoTime() + "@x.co");
		user.setPassword(encoder.encode("password123"));
		user.setDisplayName("Test");
		user.setCreatedAt(LocalDateTime.now());
		user = users.saveAndFlush(user);
	}

	void pending() {
		UserEmail email = new UserEmail();
		email.setUserId(user.getId());
		email.setPendingEmail(user.getUsername());
		emails.saveAndFlush(email);
	}

	@Test
	void unverifiedWithCorrectPasswordIsIdentified() throws Exception {
		pending();
		mvc.perform(formLogin().user(user.getUsername()).password("password123"))
			.andExpect(redirectedUrl("/login?unverified"));
	}

	@Test
	void wrongPasswordDoesNotRevealStatus() throws Exception {
		pending();
		mvc.perform(formLogin().user(user.getUsername()).password("wrong")).andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void legacyAndStaffCanLogin() throws Exception {
		mvc.perform(formLogin().user(user.getUsername()).password("password123"))
			.andExpect(redirectedUrl("/reservations"));
		user.setRole(AppUser.ROLE_STAFF);
		users.saveAndFlush(user);
		pending();
		mvc.perform(formLogin().user(user.getUsername()).password("password123"))
			.andExpect(redirectedUrl("/reservations"));
	}

	@Test
	void verifiedUserCanLogin() throws Exception {
		pending();
		UserEmail email = emails.findById(user.getId()).orElseThrow();
		email.setVerifiedEmail(user.getUsername());
		email.setPendingEmail(null);
		emails.saveAndFlush(email);
		mvc.perform(formLogin().user(user.getUsername()).password("password123"))
			.andExpect(redirectedUrl("/reservations"));
	}

	@Test
	void resendResponsesAreIndistinguishable() throws Exception {
		pending();
		mvc.perform(post("/verify-email/resend").with(csrf()).param("email", user.getUsername()))
			.andExpect(redirectedUrl("/verify-email/resend?sent"));
		mvc.perform(post("/verify-email/resend").with(csrf()).param("email", "none@x.co"))
			.andExpect(redirectedUrl("/verify-email/resend?sent"));
	}

	@Test
	void resendLimitedToThreePerHour() throws Exception {
		pending();
		long before = outbox.count();
		for (int i = 0; i < 4; i++)
			mvc.perform(post("/verify-email/resend").with(csrf()).param("email", user.getUsername()))
				.andExpect(redirectedUrl("/verify-email/resend?sent"));
		assertThat(outbox.count() - before).isEqualTo(3);
	}

}
