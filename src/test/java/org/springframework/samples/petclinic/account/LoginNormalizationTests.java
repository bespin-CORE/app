package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "test.login-normalization=true" })
@AutoConfigureMockMvc
class LoginNormalizationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	PasswordEncoder encoder;

	private AppUser createUser(String username) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPassword(encoder.encode("password123"));
		user.setDisplayName("Test");
		user.setCreatedAt(LocalDateTime.now());
		return users.saveAndFlush(user);
	}

	@Test
	void loginIgnoresCaseAndSpacesForEmailIds() throws Exception {
		AppUser user = createUser("hong@naver.com");
		UserEmail email = new UserEmail();
		email.setUserId(user.getId());
		email.setVerifiedEmail(user.getUsername());
		emails.saveAndFlush(email);
		mvc.perform(formLogin().user(" Hong@Naver.COM ").password("password123"))
			.andExpect(authenticated().withUsername("hong@naver.com"));
	}

	@Test
	void legacyUsernameLoginUnchanged() throws Exception {
		createUser("LegacyCase");
		mvc.perform(formLogin().user("LegacyCase").password("password123"))
			.andExpect(authenticated().withUsername("LegacyCase"));
	}

}
