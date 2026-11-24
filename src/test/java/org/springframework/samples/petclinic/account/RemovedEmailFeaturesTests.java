package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.mail.MailKind;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "vetcore.scheduling.enabled=false")
@AutoConfigureMockMvc
class RemovedEmailFeaturesTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AppUserRepository users;

	@Test
	void emailPageIsRemoved() throws Exception {
		AppUser account = new AppUser();
		account.setUsername("legacy" + System.nanoTime());
		account.setPassword("encoded");
		account.setDisplayName("Test");
		account.setCreatedAt(LocalDateTime.now());
		users.saveAndFlush(account);
		mvc.perform(get("/my/email").with(user(account.getUsername()))).andExpect(status().isNotFound());
	}

	@Test
	void summaryKindIsRemoved() {
		assertThat(java.util.Arrays.stream(MailKind.values()).map(Enum::name)).doesNotContain("RESERVATION_SUMMARY");
	}

}
