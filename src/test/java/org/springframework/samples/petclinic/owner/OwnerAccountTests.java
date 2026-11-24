package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.account.AppUser;
import org.springframework.samples.petclinic.account.AppUserRepository;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The link between a login account and its owner record.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnerAccountTests {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	MockMvc mockMvc;

	@Autowired
	OwnerRepository owners;

	@Autowired
	AppUserRepository users;

	private AppUser saveUser() {
		AppUser account = new AppUser();
		account.setUsername("link_" + SEQUENCE.incrementAndGet());
		account.setPassword("not-a-real-hash");
		account.setDisplayName("Before");
		account.setCreatedAt(LocalDateTime.now());
		return this.users.saveAndFlush(account);
	}

	private Owner newOwner() {
		Owner owner = new Owner();
		owner.setFirstName("Link");
		owner.setLastName("Tester");
		owner.setAddress("1 Test Road");
		owner.setCity("Seoul");
		owner.setTelephone("0101234567");
		return owner;
	}

	@Test
	void ownerIsFoundByItsAccountUsername() {
		AppUser account = saveUser();
		Owner owner = newOwner();
		owner.setAccount(account);
		this.owners.saveAndFlush(owner);

		assertThat(this.owners.findByAccountUsername(account.getUsername())).get()
			.extracting(Owner::getId)
			.isEqualTo(owner.getId());
		assertThat(this.owners.findByAccountUsername("no_such_user")).isEmpty();
	}

	@Test
	void oneAccountCannotOwnTwoOwnerRecords() {
		AppUser account = saveUser();
		Owner first = newOwner();
		first.setAccount(account);
		this.owners.saveAndFlush(first);

		Owner second = newOwner();
		second.setAccount(account);
		assertThatThrownBy(() -> this.owners.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void seedOwnersHaveNoAccount() {
		assertThat(this.owners.findById(1).orElseThrow().getAccount()).isNull();
	}

	@Test
	void staffOwnerFormCannotTouchTheLinkedAccount() throws Exception {
		AppUser account = saveUser();
		Owner owner = newOwner();
		owner.setAccount(account);
		Integer ownerId = this.owners.saveAndFlush(owner).getId();

		this.mockMvc
			.perform(post("/owners/{id}/edit", ownerId).with(csrf())
				.with(user("staff").roles("STAFF"))
				.param("firstName", "A")
				.param("lastName", "B")
				.param("address", "C")
				.param("city", "D")
				.param("telephone", "0101234567")
				.param("account.displayName", "Hacked")
				.param("account.role", "STAFF"))
			.andExpect(status().is3xxRedirection());

		AppUser reloaded = this.users.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getDisplayName()).isEqualTo("Before");
		assertThat(reloaded.getRole()).isEqualTo("USER");
		assertThat(this.owners.findByAccountUsername(account.getUsername())).get()
			.extracting(Owner::getFirstName)
			.isEqualTo("A");
	}

}
