package org.springframework.samples.petclinic.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

	@Test
	void normalizesAndRegistersEmailUsername() throws Exception {
		mvc.perform(post("/signup").with(csrf())
			.param("username", " Hong@Naver.com ")
			.param("password", "password123")
			.param("displayName", "Test")).andExpect(redirectedUrl("/login"));
		var user = users.findByUsername("hong@naver.com").orElseThrow();
		assertThat(emails.findById(user.getId()).orElseThrow().getPendingEmail()).isEqualTo("hong@naver.com");
	}

	@Test
	void rejectsInvalidUsernames() throws Exception {
		for (String value : new String[] { "abcdefghijklmnopqrs@ex.com", "missing-at", "a..b@x.com", ".a@x.com",
				"a@x.c", "x<y@z.com" })
			mvc.perform(post("/signup").with(csrf())
				.param("username", value)
				.param("password", "password123")
				.param("displayName", "Test")).andExpect(model().attributeHasFieldErrors("signupForm", "username"));
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

}
