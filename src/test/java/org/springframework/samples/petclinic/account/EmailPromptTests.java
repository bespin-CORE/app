package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = "vetcore.scheduling.enabled=false")
@AutoConfigureMockMvc
class EmailPromptTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	AppUser account;

	String username;

	@BeforeEach
	void setup() {
		AppUser user = new AppUser();
		username = "prompt" + System.nanoTime();
		user.setUsername(username);
		user.setPassword("encoded");
		user.setDisplayName("Test");
		user.setCreatedAt(LocalDateTime.now());
		account = users.saveAndFlush(user);
	}

	@Test
	void shownForUserWithoutVerifiedEmail() throws Exception {
		mvc.perform(get("/").with(user(username))).andExpect(content().string(containsString("id=\"email-prompt\"")));
	}

	@Test
	void hiddenForAnonymous() throws Exception {
		mvc.perform(get("/")).andExpect(content().string(not(containsString("id=\"email-prompt\""))));
	}

	@Test
	void hiddenForVerifiedUser() throws Exception {
		UserEmail email = new UserEmail();
		email.setUserId(account.getId());
		email.setVerifiedEmail(username + "@example.com");
		emails.saveAndFlush(email);
		mvc.perform(get("/").with(user(username)))
			.andExpect(content().string(not(containsString("id=\"email-prompt\""))));
	}

	@Test
	void hiddenOnEmailPage() throws Exception {
		mvc.perform(get("/my/email").with(user(username)))
			.andExpect(content().string(not(containsString("id=\"email-prompt\""))));
	}

	@Test
	void dismissHidesForSession() throws Exception {
		var cookie = mvc.perform(post("/my/email/prompt/dismiss").with(user(username)).with(csrf()))
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
		mvc.perform(get("/").cookie(cookie).with(user(username)))
			.andExpect(content().string(not(containsString("id=\"email-prompt\""))));
		mvc.perform(get("/").session(new MockHttpSession()).with(user(username)))
			.andExpect(content().string(containsString("id=\"email-prompt\"")));
	}

}
