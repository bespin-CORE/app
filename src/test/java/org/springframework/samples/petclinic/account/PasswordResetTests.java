package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.mail.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"spring.mail.username=test@example.com", "spring.mail.password=x" })
@AutoConfigureMockMvc
@Import(MailTestConfig.class)
class PasswordResetTests {

	@Autowired
	PasswordResetService service;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	AccountTokenRepository tokens;

	@Autowired
	MailOutboxRepository outbox;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	MockMvc mvc;

	@Autowired
	Clock clock;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	AppUser user;

	String address;

	@BeforeEach
	void setup() {
		outbox.deleteAll();
		tokens.deleteAll();
		emails.deleteAll();
		user = new AppUser();
		user.setUsername("reset" + System.nanoTime());
		user.setPassword(encoder.encode("oldpassword"));
		user.setDisplayName("Test");
		user.setCreatedAt(LocalDateTime.now(clock));
		user = users.saveAndFlush(user);
		address = user.getUsername() + "@example.com";
		UserEmail email = new UserEmail();
		email.setUserId(user.getId());
		email.setVerifiedEmail(address);
		emails.saveAndFlush(email);
	}

	String token() {
		String text = outbox.findAll().get(outbox.findAll().size() - 1).getBodyText();
		return text.substring(text.indexOf("token=") + 6).trim();
	}

	@Test
	void sameResponseForKnownAndUnknown() throws Exception {
		mvc.perform(post("/password/forgot").with(csrf()).param("email", address))
			.andExpect(redirectedUrl("/password/forgot?sent"));
		mvc.perform(post("/password/forgot").with(csrf()).param("email", "unknown@example.com"))
			.andExpect(redirectedUrl("/password/forgot?sent"));
		assertThat(outbox.count()).isEqualTo(1);
	}

	@Test
	void matchesNormalizedEmail() {
		service.requestReset("  " + address.toUpperCase() + "  ");
		assertThat(outbox.count()).isEqualTo(1);
	}

	@Test
	void limitedToThreePerHour() {
		for (int i = 0; i < 4; i++)
			service.requestReset(address);
		assertThat(outbox.count()).isEqualTo(3);
	}

	@Test
	void resetChangesPasswordOnce() {
		service.requestReset(address);
		String raw = token();
		assertThat(service.reset(raw, "newpassword")).isTrue();
		assertThat(encoder.matches("newpassword", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
		assertThat(service.reset(raw, "newpassword")).isFalse();
	}

	@Test
	void expiresAfter30Minutes() {
		service.requestReset(address);
		String raw = token();
		AccountToken row = tokens.findAll().get(0);
		row.setExpiresAt(LocalDateTime.now(clock).minusMinutes(1));
		tokens.saveAndFlush(row);
		assertThat(service.reset(raw, "newpassword")).isFalse();
	}

	@Test
	void existingSessionsAreEnded() {
		String id = createSession(sessions, user.getUsername());
		assertThat(sessions.findById(id)).isNotNull();
		service.requestReset(address);
		assertThat(service.reset(token(), "newpassword")).isTrue();
		assertThat(sessions.findById(id)).isNull();
	}

	@Test
	void unverifiedEmailGetsNothing() {
		UserEmail row = emails.findById(user.getId()).orElseThrow();
		row.setVerifiedEmail(null);
		row.setPendingEmail(address);
		emails.saveAndFlush(row);
		service.requestReset(address);
		assertThat(outbox.count()).isZero();
	}

	private static <S extends Session> String createSession(FindByIndexNameSessionRepository<S> repository,
			String username) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
		repository.save(session);
		return session.getId();
	}

}
