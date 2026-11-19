package org.springframework.samples.petclinic.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "vetcore.scheduling.enabled=false")
@AutoConfigureMockMvc
class EmailSignupTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	AccountService accounts;

	@Test
	void normalizesAndRegistersEmailUsername() throws Exception {
		mvc.perform(post("/signup").with(csrf())
			.param("username", " Hong@Naver.com ")
			.param("password", "password123")
			.param("displayName", "Test")).andExpect(redirectedUrl("/login"));
		var user = users.findByUsername("hong@naver.com").orElseThrow();
		var email = emails.findById(user.getId()).orElseThrow();
		assertThat(email.getPendingEmail()).isEqualTo("hong@naver.com");
		email.setVerifiedEmail(user.getUsername());
		email.setPendingEmail(null);
		emails.saveAndFlush(email);
		mvc.perform(formLogin().user("hong@naver.com").password("password123"))
			.andExpect(redirectedUrl("/reservations"));
	}

	@Test
	void rejectsInvalidUsernames() throws Exception {
		for (String value : new String[] { "abcdefghijklmnopqrs@ex.com", "missing-at", "a..b@x.com", ".a@x.com",
				"a@x.c", "x<y@z.com", "a@-x.com", "a@x-.com" })
			mvc.perform(post("/signup").with(csrf())
				.param("username", value)
				.param("password", "password123")
				.param("displayName", "Test")).andExpect(model().attributeHasFieldErrors("signupForm", "username"));
	}

	@Test
	void validatesEmailDomainHyphenRules() {
		assertThat(EmailAddresses.isValid("a@-x.com")).isFalse();
		assertThat(EmailAddresses.isValid("a@x-.com")).isFalse();
		assertThat(EmailAddresses.isValid("a@x-y.com")).isTrue();
	}

	@Test
	void rejectsPasswordExceedingBcryptByteLimit() throws Exception {
		String korean25 = "가".repeat(25);
		mvc.perform(post("/signup").with(csrf())
			.param("username", "bytes75@example.com")
			.param("password", korean25)
			.param("displayName", "Test"))
			.andExpect(status().isOk())
			.andExpect(view().name("account/signup"))
			.andExpect(model().attributeHasFieldErrors("signupForm", "password"));
		assertThat(users.findByUsername("bytes75@example.com")).isEmpty();
	}

	@Test
	void duplicateUsernameIsFormError() throws Exception {
		var request = post("/signup").with(csrf())
			.param("username", "dupe@example.com")
			.param("password", "password123")
			.param("displayName", "Test");
		mvc.perform(request).andExpect(redirectedUrl("/login"));
		mvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("signupForm", "username"));
	}

	@Test
	void concurrentDuplicateRegistrationRollsBackCleanly() throws Exception {
		SignupForm form = new SignupForm();
		form.setUsername("race" + System.nanoTime() % 100000000 + "@x.co");
		form.setPassword("password123");
		form.setDisplayName("Test");
		var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		var start = new java.util.concurrent.CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Boolean> register = () -> {
				start.await();
				try {
					accounts.register(form);
					return true;
				}
				catch (AccountService.DuplicateUsernameException ex) {
					return false;
				}
			};
			var a = executor.submit(register);
			var b = executor.submit(register);
			start.countDown();
			assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
			assertThat(users.findByUsername(form.getUsername())).isPresent();
		}
		finally {
			executor.shutdownNow();
		}
	}

}
