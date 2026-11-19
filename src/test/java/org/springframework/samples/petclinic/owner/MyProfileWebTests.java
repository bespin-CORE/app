package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.account.AppUserRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What an ordinary user can do with their own owner record and pets, and what stays
 * reserved for staff, through the whole application with security switched on.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyProfileWebTests {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	MockMvc mockMvc;

	@Autowired
	OwnerRepository owners;

	@Autowired
	AppUserRepository users;

	private String signUp() throws Exception {
		String username = "my" + SEQUENCE.incrementAndGet() + "@ex.com";
		this.mockMvc
			.perform(post("/signup").with(csrf())
				.param("username", username)
				.param("password", "password123")
				.param("email", username + "@example.com")
				.param("displayName", "My Tester"))
			.andExpect(redirectedUrl("/login"));
		return username;
	}

	private ResultActions saveProfile(String username, String telephone) throws Exception {
		return this.mockMvc.perform(post("/my/profile/edit").with(csrf())
			.with(user(username))
			.param("firstName", "길동")
			.param("lastName", "홍")
			.param("address", "세종대로 1")
			.param("city", "서울")
			.param("telephone", telephone));
	}

	@Test
	void myPagesNeedLogin() throws Exception {
		for (String path : List.of("/my/profile", "/my/profile/edit", "/my/pets/new", "/my/pets/1/edit")) {
			this.mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
	}

	@Test
	void ordinaryUserCannotOpenStaffOwnerPages() throws Exception {
		String username = signUp();
		for (String path : List.of("/owners", "/owners/find", "/owners/1", "/owners/1/edit", "/owners/new",
				"/owners/1/pets/new", "/owners/1/pets/1/edit", "/owners/1/pets/1/visits/new")) {
			this.mockMvc.perform(get(path).with(user(username))).andExpect(status().isForbidden());
		}
		this.mockMvc
			.perform(post("/owners/1/pets/1/visits/new").with(csrf()).with(user(username)).param("description", "x"))
			.andExpect(status().isForbidden());
	}

	@Test
	void firstVisitLeadsToTheForm() throws Exception {
		String username = signUp();
		this.mockMvc.perform(get("/my/profile").with(user(username))).andExpect(redirectedUrl("/my/profile/edit"));
		this.mockMvc.perform(get("/my/profile/edit").with(user(username)))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}

	@Test
	void staffWithoutProfileIsTreatedLikeAnyUser() throws Exception {
		String username = signUp();
		this.mockMvc.perform(get("/my/profile").with(user(username).roles("STAFF")))
			.andExpect(redirectedUrl("/my/profile/edit"));
	}

	@Test
	void savingCreatesThenUpdatesTheSameOwner() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678").andExpect(redirectedUrl("/my/profile"));
		Owner created = this.owners.findByAccountUsername(username).orElseThrow();
		assertThat(created.getCity()).isEqualTo("서울");

		saveProfile(username, "0212345678").andExpect(redirectedUrl("/my/profile"));
		Owner updated = this.owners.findByAccountUsername(username).orElseThrow();
		assertThat(updated.getId()).isEqualTo(created.getId());
		assertThat(updated.getTelephone()).isEqualTo("0212345678");

		this.mockMvc.perform(get("/my/profile/edit").with(user(username)))
			.andExpect(model().attribute("owner", hasProperty("address", is("세종대로 1"))));
		this.mockMvc.perform(get("/my/profile").with(user(username)))
			.andExpect(status().isOk())
			.andExpect(view().name("my/profile"))
			.andExpect(content().string(containsString("세종대로 1")))
			.andExpect(content().string(not(containsString("/visits/new"))))
			.andExpect(content().string(not(containsString("/owners/"))));
	}

	@Test
	void hyphenatedTelephoneComesBackAsAFormError() throws Exception {
		String username = signUp();
		saveProfile(username, "010-1234-5678").andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"));
		assertThat(this.owners.findByAccountUsername(username)).isEmpty();
	}

	@Test
	void smuggledIdAndAccountFieldsAreIgnored() throws Exception {
		String username = signUp();
		String victim = signUp();
		Integer victimUserId = this.users.findByUsername(victim).orElseThrow().getId();
		long before = this.owners.count();

		this.mockMvc
			.perform(post("/my/profile/edit").with(csrf())
				.with(user(username))
				.param("firstName", "A")
				.param("lastName", "B")
				.param("address", "C")
				.param("city", "D")
				.param("telephone", "01012345678")
				.param("id", "1")
				.param("account.id", victimUserId.toString())
				.param("account.role", "STAFF"))
			.andExpect(redirectedUrl("/my/profile"));

		assertThat(this.owners.count()).isEqualTo(before + 1);
		assertThat(this.owners.findById(1).orElseThrow().getFirstName()).isEqualTo("George");
		assertThat(this.owners.findByAccountUsername(victim)).isEmpty();
		assertThat(this.owners.findByAccountUsername(username)).isPresent();
		assertThat(this.users.findByUsername(username).orElseThrow().getRole()).isEqualTo("USER");
	}

	@Test
	void postWithoutCsrfChangesNothing() throws Exception {
		String username = signUp();
		this.mockMvc
			.perform(post("/my/profile/edit").with(user(username))
				.param("firstName", "A")
				.param("lastName", "B")
				.param("address", "C")
				.param("city", "D")
				.param("telephone", "01012345678"))
			.andExpect(status().isForbidden());
		assertThat(this.owners.findByAccountUsername(username)).isEmpty();
	}

	@Test
	void navigationShowsMyProfileOnlyWhenLoggedIn() throws Exception {
		this.mockMvc.perform(get("/")).andExpect(content().string(not(containsString("/my/profile"))));
		this.mockMvc.perform(get("/").with(user(signUp())))
			.andExpect(content().string(containsString("/my/profile")))
			.andExpect(content().string(not(containsString("/owners/find"))));
	}

	private ResultActions addPet(String username, String name) throws Exception {
		return this.mockMvc.perform(post("/my/pets/new").with(csrf())
			.with(user(username))
			.param("name", name)
			.param("birthDate", "2020-01-15")
			.param("type", "hamster"));
	}

	private Integer petIdOf(String username, String name) {
		return this.owners.findByAccountUsername(username).orElseThrow().getPet(name).getId();
	}

	@Test
	void petPagesRequireAProfileFirst() throws Exception {
		String username = signUp();
		this.mockMvc.perform(get("/my/pets/new").with(user(username))).andExpect(redirectedUrl("/my/profile/edit"));
		addPet(username, "콩이").andExpect(redirectedUrl("/my/profile/edit"));
		this.mockMvc.perform(get("/my/pets/1/edit").with(user(username))).andExpect(redirectedUrl("/my/profile/edit"));
	}

	@Test
	void userAddsAndEditsTheirOwnPet() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678");
		this.mockMvc.perform(get("/my/pets/new").with(user(username)))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdatePetForm"));
		addPet(username, "콩이").andExpect(redirectedUrl("/my/profile"));
		Integer petId = petIdOf(username, "콩이");

		this.mockMvc.perform(get("/my/pets/{id}/edit", petId).with(user(username)))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdatePetForm"))
			.andExpect(model().attribute("pet", hasProperty("name", is("콩이"))));
		this.mockMvc
			.perform(post("/my/pets/{id}/edit", petId).with(csrf())
				.with(user(username))
				.param("name", "콩순이")
				.param("birthDate", "2020-01-15")
				.param("type", "hamster"))
			.andExpect(redirectedUrl("/my/profile"));

		Owner owner = this.owners.findByAccountUsername(username).orElseThrow();
		assertThat(owner.getPets()).hasSize(1);
		assertThat(owner.getPets().get(0).getName()).isEqualTo("콩순이");
		assertThat(owner.getPets().get(0).getId()).isEqualTo(petId);
		this.mockMvc.perform(get("/my/profile").with(user(username)))
			.andExpect(content().string(containsString("콩순이")));
	}

	@Test
	void anotherUsersPetIsNotFoundAndStaysUnchanged() throws Exception {
		String owner = signUp();
		saveProfile(owner, "01012345678");
		addPet(owner, "보리");
		Integer othersPetId = petIdOf(owner, "보리");
		String intruder = signUp();
		saveProfile(intruder, "01087654321");

		// 1 is a pet of a seed owner who has no account
		for (Integer petId : List.of(othersPetId, 1, 999999)) {
			this.mockMvc.perform(get("/my/pets/{id}/edit", petId).with(user(intruder)))
				.andExpect(status().isNotFound());
			this.mockMvc
				.perform(post("/my/pets/{id}/edit", petId).with(csrf())
					.with(user(intruder))
					.param("name", "탈취")
					.param("birthDate", "2020-01-15")
					.param("type", "hamster"))
				.andExpect(status().isNotFound());
		}

		assertThat(petIdOf(owner, "보리")).isEqualTo(othersPetId);
		assertThat(this.owners.findById(1).orElseThrow().getPet(1).getName()).isEqualTo("Leo");
		assertThat(this.owners.findByAccountUsername(intruder).orElseThrow().getPets()).isEmpty();
	}

	@Test
	void duplicateNameAndFutureBirthDateAreFormErrors() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678");
		addPet(username, "콩이");
		addPet(username, "콩이").andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("pet", "name"));
		this.mockMvc
			.perform(post("/my/pets/new").with(csrf())
				.with(user(username))
				.param("name", "미래")
				.param("birthDate", LocalDate.now().plusDays(1).toString())
				.param("type", "hamster"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("pet", "birthDate"));
		assertThat(this.owners.findByAccountUsername(username).orElseThrow().getPets()).hasSize(1);
	}

	@Test
	void renamingAPetToASiblingsNameIsAFormError() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678");
		addPet(username, "콩이");
		addPet(username, "보리");
		Integer petId = petIdOf(username, "보리");
		this.mockMvc
			.perform(post("/my/pets/{id}/edit", petId).with(csrf())
				.with(user(username))
				.param("name", "콩이")
				.param("birthDate", "2020-01-15")
				.param("type", "hamster"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("pet", "name"));
		assertThat(petIdOf(username, "보리")).isEqualTo(petId);
	}

	@Test
	void petFormCannotMoveAPetOrSetItsId() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678");
		this.mockMvc
			.perform(post("/my/pets/new").with(csrf())
				.with(user(username))
				.param("name", "콩이")
				.param("birthDate", "2020-01-15")
				.param("type", "hamster")
				.param("id", "1"))
			.andExpect(redirectedUrl("/my/profile"));
		assertThat(this.owners.findById(1).orElseThrow().getPet(1).getName()).isEqualTo("Leo");
		assertThat(petIdOf(username, "콩이")).isNotEqualTo(1);
	}

	@Test
	void staffSeesAUsersOwnerRecordAndCanAddAVisit() throws Exception {
		String username = signUp();
		saveProfile(username, "01012345678");
		addPet(username, "콩이");
		Integer ownerId = this.owners.findByAccountUsername(username).orElseThrow().getId();
		Integer petId = petIdOf(username, "콩이");

		this.mockMvc.perform(get("/owners/{id}", ownerId).with(user("staff").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("콩이")));
		this.mockMvc
			.perform(post("/owners/{o}/pets/{p}/visits/new", ownerId, petId).with(csrf())
				.with(user("staff").roles("STAFF"))
				// the staff visit form only accepts a date after today
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "정기 검진"))
			.andExpect(status().is3xxRedirection());
		this.mockMvc.perform(get("/my/profile").with(user(username)))
			.andExpect(content().string(containsString("정기 검진")));
	}

}
