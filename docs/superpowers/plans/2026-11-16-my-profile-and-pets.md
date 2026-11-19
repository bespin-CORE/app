# 일반 사용자 "내 정보·반려동물" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인한 일반 사용자가 `/my/**`에서 본인 보호자 정보와 반려동물을 등록·수정하고 진료 기록을 조회하며, `/owners/**`는 직원 전용으로 유지한다.

**Architecture:** 새 테이블 `owner_accounts`로 `owners`와 `users`를 일대일 연결하고 `Owner.account`로 매핑한다. 새 `MyProfileController`는 주소에 보호자 번호를 받지 않고 `Principal`의 사용자명으로 본인 `Owner`를 조회한다. 폼은 새 객체에 허용 필드만 바인딩한 뒤 조회한 엔티티로 복사한다.

**Tech Stack:** Java 17, Spring Boot(MVC, Security, Data JPA), Thymeleaf, H2(테스트)·MySQL 8.0(임시 서버), Maven(`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-11-16-my-profile-and-pets-design.md`

## Global Constraints

- 모든 경로는 저장소 루트 기준이다. 명령은 `app/`에서 실행한다.
- 원본 패키지 `org.springframework.samples.petclinic`을 유지한다. 새 클래스는 `owner` 패키지에 둔다.
- `/owners/**`, `/admin/**`는 `STAFF` 전용 그대로다. 기존 테스트(`OwnerControllerTests`, `PetControllerTests`, `VisitControllerTests`, `ReservationWebTests`)가 통과해야 한다.
- 새 메시지 키는 `app/src/main/resources/messages/`의 11개 파일 모두에 추가한다(`I18nPropertiesSyncTest`). 한국어·영어 문구는 이 계획의 값을 쓰고, 나머지 언어는 영어 문구를 넣는다.
- 스키마 변경은 `db/h2`, `db/mysql`, `db/postgres`의 `schema.sql` 세 곳에 모두 반영한다. 기존 테이블에 `ALTER`를 추가하지 않는다.
- 소스에 코드 생략 표기나 새 TODO를 남기지 않는다. 비밀번호·토큰을 출력하지 않는다.
- **커밋하지 않는다.** 작업 공간 전체가 미추적 상태이며 커밋은 사용자 지시가 있을 때만 한다. 각 작업은 테스트 통과로 끝난다.
- 임시 서버 배포, 인프라·DB 직접 변경은 하지 않는다.

## Review Focus

1. 하이픈이 든 전화번호(`010-1234-5678`) 입력 → 500이 아니라 폼 오류로 돌아온다. (Task 2)
2. 본인 반려동물과 같은 이름으로 또 등록 → 500이 아니라 `name` 필드 오류. (Task 4)
3. 같은 계정으로 보호자 정보를 두 번 생성(중복 제출) → 두 번째는 오류 화면 없이 `/my/profile`로 이동하고 보호자는 하나만 남는다. (Task 1 저장소 테스트, Task 3 컨트롤러)
4. 보호자 정보가 없는 직원 계정이 `/my/profile` 접근 → 일반 사용자와 같게 입력 폼으로 이동. (Task 3)
5. CSRF 토큰 없는 `POST /my/**` → 403이고 데이터가 바뀌지 않는다. (Task 3)

## File Structure

| 파일 | 역할 |
| --- | --- |
| `app/src/main/resources/db/{h2,mysql,postgres}/schema.sql` (수정) | `owner_accounts` 테이블 |
| `app/src/main/java/.../owner/Owner.java` (수정) | `account` 필드, 전화번호 규칙 |
| `app/src/main/java/.../owner/OwnerRepository.java` (수정) | `findByAccountUsername` |
| `app/src/main/java/.../owner/{Owner,Pet,Visit}Controller.java` (수정) | 바인더에 `account` 차단만 추가 |
| `app/src/main/java/.../owner/MyProfileController.java` (생성) | `/my/**` 전체 |
| `app/src/main/java/.../account/SecurityConfig.java` (수정) | `/my/**` 로그인 필수 명시 |
| `app/src/main/resources/templates/my/profile.html` (생성) | 본인 정보·반려동물·진료 기록 화면 |
| `app/src/main/resources/templates/fragments/layout.html` (수정) | "내 정보" 메뉴, `my` 메뉴 키 |
| `app/src/main/resources/messages/messages*.properties` (수정) | 새 키, 전화번호 문구 |
| `app/src/test/java/.../owner/OwnerAccountTests.java` (생성) | 연결 매핑·제약 |
| `app/src/test/java/.../owner/MyProfileWebTests.java` (생성) | `/my/**` 화면·접근 통제 |
| `docs/feature-spec.md`, `docs/db-design.md`, `docs/handoff.md` (수정) | 문서 갱신 |

---

### Task 1: 계정-보호자 연결(`owner_accounts`)과 직원 폼 보호

**Files:**
- Modify: `app/src/main/resources/db/h2/schema.sql`, `db/mysql/schema.sql`, `db/postgres/schema.sql`
- Modify: `app/src/main/java/org/springframework/samples/petclinic/owner/Owner.java`, `OwnerRepository.java`, `OwnerController.java:61`, `PetController.java:91,97`, `VisitController.java:53`
- Test: `app/src/test/java/org/springframework/samples/petclinic/owner/OwnerAccountTests.java`

**Interfaces:**
- Produces: `Owner.getAccount(): AppUser`, `Owner.setAccount(AppUser)`, `OwnerRepository.findByAccountUsername(String username): Optional<Owner>`

- [ ] **Step 1: 실패하는 테스트 작성** — `OwnerAccountTests`(`@SpringBootTest`, `@AutoConfigureMockMvc`, 방식은 `ReservationWebTests` 참고). `AppUser`는 `AppUserRepository`로 직접 저장한다(고유 사용자명, `role=USER`, `createdAt` 설정).

```java
@Test
void ownerIsFoundByItsAccountUsername() {
	AppUser user = saveUser();
	Owner owner = newOwner(); // firstName, lastName, address, city, telephone "0101234567"
	owner.setAccount(user);
	this.owners.saveAndFlush(owner);
	assertThat(this.owners.findByAccountUsername(user.getUsername())).get()
		.extracting(Owner::getId).isEqualTo(owner.getId());
	assertThat(this.owners.findByAccountUsername("no_such_user")).isEmpty();
}

@Test
void oneAccountCannotOwnTwoOwnerRecords() {
	AppUser user = saveUser();
	Owner first = newOwner();
	first.setAccount(user);
	this.owners.saveAndFlush(first);
	Owner second = newOwner();
	second.setAccount(user);
	assertThatThrownBy(() -> this.owners.saveAndFlush(second))
		.isInstanceOf(DataIntegrityViolationException.class);
}

@Test
void seedOwnersHaveNoAccount() {
	assertThat(this.owners.findById(1).orElseThrow().getAccount()).isNull();
}

@Test
void staffOwnerFormCannotTouchTheLinkedAccount() throws Exception {
	AppUser user = saveUser(); // displayName "Before"
	Owner owner = newOwner();
	owner.setAccount(user);
	Integer ownerId = this.owners.saveAndFlush(owner).getId();
	this.mockMvc.perform(post("/owners/{id}/edit", ownerId).with(csrf()).with(user("staff").roles("STAFF"))
			.param("firstName", "A").param("lastName", "B").param("address", "C").param("city", "D")
			.param("telephone", "0101234567")
			.param("account.displayName", "Hacked").param("account.role", "STAFF"))
		.andExpect(status().is3xxRedirection());
	AppUser reloaded = this.users.findById(user.getId()).orElseThrow();
	assertThat(reloaded.getDisplayName()).isEqualTo("Before");
	assertThat(reloaded.getRole()).isEqualTo("USER");
	assertThat(this.owners.findById(ownerId).orElseThrow().getAccount().getId()).isEqualTo(user.getId());
}
```

- [ ] **Step 2: 실패 확인** — `cd app && ./mvnw test -Dtest=OwnerAccountTests`. 예상: `setAccount` 미정의로 컴파일 실패.

- [ ] **Step 3: 스키마 추가** — 세 `schema.sql`에서 `users`와 `owners` 테이블 정의 뒤에 추가한다. H2 파일은 맨 위 `DROP` 목록의 `users`·`owners`보다 앞에 `DROP TABLE owner_accounts IF EXISTS;`도 넣는다.

| DB | 정의 |
| --- | --- |
| h2 | `CREATE TABLE owner_accounts (owner_id INTEGER NOT NULL PRIMARY KEY, user_id INTEGER NOT NULL);` + `ALTER TABLE … ADD CONSTRAINT fk_owner_accounts_owner FOREIGN KEY (owner_id) REFERENCES owners (id)`, `fk_owner_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)`, `uk_owner_accounts_user UNIQUE (user_id)` |
| mysql | `CREATE TABLE IF NOT EXISTS owner_accounts (owner_id INT(4) UNSIGNED NOT NULL PRIMARY KEY, user_id INT(4) UNSIGNED NOT NULL, UNIQUE KEY uk_owner_accounts_user (user_id), FOREIGN KEY (owner_id) REFERENCES owners(id), FOREIGN KEY (user_id) REFERENCES users(id)) engine=InnoDB;` |
| postgres | `CREATE TABLE IF NOT EXISTS owner_accounts (owner_id INT NOT NULL PRIMARY KEY REFERENCES owners (id), user_id INT NOT NULL UNIQUE REFERENCES users (id));` |

- [ ] **Step 4: 매핑 구현** — `Owner`에 필드와 getter/setter를 추가한다.

```java
@OneToOne(fetch = FetchType.LAZY)
@JoinTable(name = "owner_accounts", joinColumns = @JoinColumn(name = "owner_id"),
		inverseJoinColumns = @JoinColumn(name = "user_id", unique = true))
private AppUser account;
```

`OwnerRepository`에 `Optional<Owner> findByAccountUsername(String username);`을 추가한다.

- [ ] **Step 5: 직원 폼 바인더 보호** — 세 컨트롤러의 `setDisallowedFields("id", "*.id")` 네 군데를 `setDisallowedFields("id", "*.id", "account", "account.*")`로 바꾼다. 다른 동작은 바꾸지 않는다.

- [ ] **Step 6: 통과 확인** — `./mvnw test -Dtest='OwnerAccountTests,OwnerControllerTests,PetControllerTests,VisitControllerTests,ClinicServiceTests'`. 예상: 실패 0. `PetClinicRuntimeHints`나 AOT 관련 테스트가 새 필드로 실패하면 그 내용을 보고한다.

---

### Task 2: 전화번호 10~11자리 허용

**Files:**
- Modify: `app/src/main/java/org/springframework/samples/petclinic/owner/Owner.java` (`telephone`의 `@Pattern`)
- Modify: `app/src/main/resources/messages/messages*.properties` (`telephone.invalid`)
- Test: `app/src/test/java/org/springframework/samples/petclinic/model/ValidatorTests.java`(추가), 기존 `OwnerControllerTests`

- [ ] **Step 1: 실패하는 테스트 작성** — `ValidatorTests`에 추가. `Validator`는 그 파일의 기존 생성 방식을 쓴다.

```java
@ParameterizedTest
@ValueSource(strings = { "0212345678", "01012345678" })
void ownerTelephoneAcceptsTenOrElevenDigits(String telephone) {
	assertThat(violationsOnTelephone(telephone)).isEmpty();
}

@ParameterizedTest
@ValueSource(strings = { "012345678", "010123456789", "010-1234-5678", "0101234567a" })
void ownerTelephoneRejectsOtherShapes(String telephone) {
	assertThat(violationsOnTelephone(telephone)).hasSize(1);
}
```

`violationsOnTelephone`은 나머지 필드가 유효한 `Owner`를 만들어 `telephone` 속성의 위반만 돌려준다.

- [ ] **Step 2: 실패 확인** — `./mvnw test -Dtest=ValidatorTests`. 예상: `01012345678`에서 실패.

- [ ] **Step 3: 구현** — 패턴을 `\\d{10,11}`로 바꾼다. `telephone.invalid` 문구: ko `전화번호는 하이픈 없이 숫자 10~11자리로 입력해 주세요`, en `Telephone must be 10 or 11 digits without hyphens`. 나머지 9개 파일은 해당 언어의 기존 문장에서 숫자 표현만 "10 or 11"에 맞게 고치고, 확실하지 않으면 영어 문구를 넣는다.

- [ ] **Step 4: 통과 확인** — `./mvnw test -Dtest='ValidatorTests,OwnerControllerTests,I18nPropertiesSyncTest'`. 예상: 실패 0. 기존 테스트가 "10자리" 문구나 11자리 거부를 단정하고 있으면 새 규칙에 맞게 고치고 무엇을 고쳤는지 보고한다.

---

### Task 3: `/my/profile` 조회·등록·수정과 메뉴

**Files:**
- Create: `app/src/main/java/org/springframework/samples/petclinic/owner/MyProfileController.java`
- Create: `app/src/main/resources/templates/my/profile.html`
- Modify: `app/src/main/java/org/springframework/samples/petclinic/account/SecurityConfig.java`
- Modify: `app/src/main/resources/templates/fragments/layout.html` (15, 47, 76행 부근)
- Modify: `app/src/main/resources/messages/messages*.properties`
- Test: `app/src/test/java/org/springframework/samples/petclinic/owner/MyProfileWebTests.java`

**Interfaces:**
- Consumes: `OwnerRepository.findByAccountUsername`, `Owner.setAccount`, `AppUserRepository.findByUsername`
- Produces: `MyProfileController`의 `Optional<Owner> currentOwner(Principal principal)`(Task 4가 같은 클래스에서 사용), 플래시 속성 `messageKey`, 메시지 키 `myProfile`, `myProfile.required`, `myProfile.saved`

**동작 규칙:**
- `GET /my/profile`: 보호자 있으면 뷰 `my/profile`(모델 `owner`), 없으면 `redirect:/my/profile/edit`.
- `GET /my/profile/edit`: 뷰 `owners/createOrUpdateOwnerForm`. 모델 `owner`는 기존 값 또는 빈 `new Owner()`.
- `POST /my/profile/edit`: `@Valid @ModelAttribute("owner") Owner form`은 **항상 새 객체**로 바인딩한다(이 컨트롤러에는 엔티티를 불러오는 `@ModelAttribute` 메서드를 두지 않는다). `@InitBinder("owner")`에서 `setAllowedFields("firstName", "lastName", "address", "city", "telephone")`. 오류면 폼 뷰, 성공이면 조회한 본인 `Owner`(없으면 새로 만들어 `setAccount(현재 AppUser)`)에 다섯 필드를 복사해 `saveAndFlush`, 플래시 `messageKey=myProfile.saved`, `redirect:/my/profile`.
- 생성 중 `DataIntegrityViolationException`(동시 중복 생성)은 잡아서 `redirect:/my/profile`.
- `SecurityConfig`: `/reservations/**` 줄을 `.requestMatchers("/reservations/**", "/my/**")`로 바꾼다.
- `layout.html`: `myReservations` 메뉴 다음에 `th:if="${currentUsername != null}"`로 `navItem ('/my/profile', 'my', 'user', #{myProfile})`를 추가한다. 15행·76행의 `menu == 'owners' or …` 조건에 `menu == 'my'`를 더한다.
- `my/profile.html`: `owners/ownerDetails.html`을 바탕으로 하되 레이아웃 메뉴 키는 `'my'`, 제목은 `#{myProfile}`, 링크는 `@{/my/profile/edit}`, `@{/my/pets/new}`, `@{/my/pets/{id}/edit(id=${pet.id})}`만 둔다. 진료 추가 링크와 `owner.id`가 들어간 링크는 없다. 플래시는 `th:if="${messageKey}" th:text="#{${messageKey}}"`로 보여 준다. 원본의 `hideMessages` 스크립트는 넣지 않는다.
- 메시지: `myProfile` ko `내 정보` / en `My Profile`; `myProfile.required` ko `보호자 정보를 먼저 입력해 주세요` / en `Please enter your owner information first`; `myProfile.saved` ko `내 정보가 저장되었습니다` / en `Your information has been saved`.

- [ ] **Step 1: 실패하는 테스트 작성** — `MyProfileWebTests`(`@SpringBootTest`, `@AutoConfigureMockMvc`). `signUp()`은 `ReservationWebTests`와 같은 방식으로 `/signup`을 호출해 사용자명을 돌려준다. 로그인은 `.with(user(username))`.

```java
private ResultActions saveProfile(String username, String telephone) throws Exception {
	return this.mockMvc.perform(post("/my/profile/edit").with(csrf()).with(user(username))
		.param("firstName", "길동").param("lastName", "홍").param("address", "세종대로 1")
		.param("city", "서울").param("telephone", telephone));
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
	this.mockMvc.perform(post("/owners/1/pets/1/visits/new").with(csrf()).with(user(username))
		.param("description", "x")).andExpect(status().isForbidden());
}

@Test
void firstVisitLeadsToTheForm() throws Exception {
	String username = signUp();
	this.mockMvc.perform(get("/my/profile").with(user(username))).andExpect(redirectedUrl("/my/profile/edit"));
	this.mockMvc.perform(get("/my/profile/edit").with(user(username)))
		.andExpect(status().isOk()).andExpect(view().name("owners/createOrUpdateOwnerForm"));
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
	this.mockMvc.perform(get("/my/profile").with(user(username)))
		.andExpect(status().isOk()).andExpect(view().name("my/profile"))
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
	this.mockMvc.perform(post("/my/profile/edit").with(csrf()).with(user(username))
			.param("firstName", "A").param("lastName", "B").param("address", "C").param("city", "D")
			.param("telephone", "01012345678")
			.param("id", "1").param("account.id", victimUserId.toString()).param("account.role", "STAFF"))
		.andExpect(redirectedUrl("/my/profile"));
	assertThat(this.owners.count()).isEqualTo(before + 1);
	assertThat(this.owners.findById(1).orElseThrow().getFirstName()).isEqualTo("George");
	assertThat(this.owners.findByAccountUsername(victim)).isEmpty();
	assertThat(this.users.findByUsername(username).orElseThrow().getRole()).isEqualTo("USER");
}

@Test
void postWithoutCsrfChangesNothing() throws Exception {
	String username = signUp();
	this.mockMvc.perform(post("/my/profile/edit").with(user(username))
			.param("firstName", "A").param("lastName", "B").param("address", "C").param("city", "D")
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
```

- [ ] **Step 2: 실패 확인** — `./mvnw test -Dtest=MyProfileWebTests`. 예상: `/my/profile`이 404여서 실패(`myPagesNeedLogin`과 `ordinaryUserCannotOpenStaffOwnerPages`는 이미 통과할 수 있다).

- [ ] **Step 3: 구현** — 위 "동작 규칙"대로 컨트롤러, 보안 설정, 템플릿, 레이아웃, 메시지 11개 파일을 작성한다.

- [ ] **Step 4: 통과 확인** — `./mvnw test -Dtest='MyProfileWebTests,I18nPropertiesSyncTest,ReservationWebTests,OwnerControllerTests'`. 예상: 실패 0.

---

### Task 4: `/my/pets` 등록·수정

**Files:**
- Modify: `app/src/main/java/org/springframework/samples/petclinic/owner/MyProfileController.java`
- Modify: `app/src/main/resources/messages/messages*.properties`
- Test: `app/src/test/java/org/springframework/samples/petclinic/owner/MyProfileWebTests.java`(추가)

**Interfaces:**
- Consumes: Task 3의 `currentOwner(Principal)`, 기존 `PetValidator`, `PetTypeRepository`, `Owner.addPet`, `Owner.getPet(Integer)`, `Owner.getPet(String, boolean)`
- Produces: 메시지 키 `myPet.saved`

**동작 규칙:**
- 네 경로 모두 보호자 정보가 없으면 플래시 `messageKey=myProfile.required`와 함께 `redirect:/my/profile/edit`.
- 뷰는 `pets/createOrUpdatePetForm`. 모델에 `owner`(본인), `pet`, `types`(`PetController.populatePetTypes`와 같은 조회)를 넣는다.
- `@InitBinder("pet")`: `PetValidator` 설정, `setAllowedFields("name", "birthDate", "type")`. `pet`은 항상 새 객체로 바인딩한다.
- `{petId}`는 `owner.getPet(petId)`로만 찾는다. `null`이면 `ResponseStatusException(HttpStatus.NOT_FOUND)`.
- 검증은 `PetController`와 같다: 본인 반려동물 중 같은 이름(대소문자 무시, 수정 시 자기 자신 제외)이면 `rejectValue("name", "duplicate", "already exists")`, 생일이 오늘 이후면 `rejectValue("birthDate", "typeMismatch.birthDate")`. 저장 중 `unique_owner_pet_name` 위반도 같은 `name` 오류로 돌려준다. 두 컨트롤러에서 같은 코드가 반복되면 `owner` 패키지의 패키지 전용 도우미로 뽑아 `PetController`도 그것을 쓰게 하되, `PetController`의 동작은 바꾸지 않는다.
- 수정은 조회한 기존 `Pet`에 `name`, `birthDate`, `type`만 복사한다. 수정 폼 GET의 모델 `pet`은 기존 값이 채워져 있어야 한다.
- 성공: 플래시 `messageKey=myPet.saved`, `redirect:/my/profile`.
- 메시지: `myPet.saved` ko `반려동물 정보가 저장되었습니다` / en `Your pet has been saved`.

- [ ] **Step 1: 실패하는 테스트 작성** — `MyProfileWebTests`에 추가. 종류 값은 시드 데이터의 `hamster`를 쓴다(`PetTypeFormatter`가 이름으로 변환).

```java
private ResultActions addPet(String username, String name) throws Exception {
	return this.mockMvc.perform(post("/my/pets/new").with(csrf()).with(user(username))
		.param("name", name).param("birthDate", "2020-01-15").param("type", "hamster"));
}

private Integer petIdOf(String username, String name) {
	return this.owners.findByAccountUsername(username).orElseThrow().getPet(name).getId();
}

@Test
void petPagesRequireAProfileFirst() throws Exception {
	String username = signUp();
	this.mockMvc.perform(get("/my/pets/new").with(user(username))).andExpect(redirectedUrl("/my/profile/edit"));
	addPet(username, "콩이").andExpect(redirectedUrl("/my/profile/edit"));
}

@Test
void userAddsAndEditsTheirOwnPet() throws Exception {
	String username = signUp();
	saveProfile(username, "01012345678");
	addPet(username, "콩이").andExpect(redirectedUrl("/my/profile"));
	Integer petId = petIdOf(username, "콩이");
	this.mockMvc.perform(get("/my/pets/{id}/edit", petId).with(user(username)))
		.andExpect(status().isOk()).andExpect(view().name("pets/createOrUpdatePetForm"))
		.andExpect(model().attribute("pet", hasProperty("name", is("콩이"))));
	this.mockMvc.perform(post("/my/pets/{id}/edit", petId).with(csrf()).with(user(username))
			.param("name", "콩순이").param("birthDate", "2020-01-15").param("type", "hamster"))
		.andExpect(redirectedUrl("/my/profile"));
	Owner owner = this.owners.findByAccountUsername(username).orElseThrow();
	assertThat(owner.getPets()).hasSize(1);
	assertThat(owner.getPets().get(0).getName()).isEqualTo("콩순이");
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
	for (Integer petId : List.of(othersPetId, 1, 999999)) { // 1 = 시드 보호자의 반려동물
		this.mockMvc.perform(get("/my/pets/{id}/edit", petId).with(user(intruder)))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(post("/my/pets/{id}/edit", petId).with(csrf()).with(user(intruder))
				.param("name", "탈취").param("birthDate", "2020-01-15").param("type", "hamster"))
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
	addPet(username, "콩이").andExpect(status().isOk())
		.andExpect(model().attributeHasFieldErrors("pet", "name"));
	this.mockMvc.perform(post("/my/pets/new").with(csrf()).with(user(username))
			.param("name", "미래").param("birthDate", LocalDate.now().plusDays(1).toString()).param("type", "hamster"))
		.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("pet", "birthDate"));
	assertThat(this.owners.findByAccountUsername(username).orElseThrow().getPets()).hasSize(1);
}

@Test
void petFormCannotMoveAPetOrSetItsId() throws Exception {
	String username = signUp();
	saveProfile(username, "01012345678");
	this.mockMvc.perform(post("/my/pets/new").with(csrf()).with(user(username))
			.param("name", "콩이").param("birthDate", "2020-01-15").param("type", "hamster").param("id", "1"))
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
		.andExpect(status().isOk()).andExpect(content().string(containsString("콩이")));
	this.mockMvc.perform(post("/owners/{o}/pets/{p}/visits/new", ownerId, petId).with(csrf())
			.with(user("staff").roles("STAFF")).param("date", LocalDate.now().toString())
			.param("description", "정기 검진"))
		.andExpect(status().is3xxRedirection());
	this.mockMvc.perform(get("/my/profile").with(user(username)))
		.andExpect(content().string(containsString("정기 검진")));
}
```

- [ ] **Step 2: 실패 확인** — `./mvnw test -Dtest=MyProfileWebTests`. 예상: `/my/pets/new`가 404여서 새 테스트 실패.

- [ ] **Step 3: 구현** — 위 "동작 규칙"대로 `MyProfileController`에 네 핸들러와 `myPet.saved` 메시지를 추가한다.

- [ ] **Step 4: 통과 확인** — `./mvnw test -Dtest='MyProfileWebTests,PetControllerTests,I18nPropertiesSyncTest'`. 예상: 실패 0.

---

### Task 5: 전체 검증과 문서 갱신

**Files:**
- Modify: `docs/feature-spec.md`, `docs/db-design.md`, `docs/handoff.md`

- [ ] **Step 1: 전체 테스트** — `cd app && ./mvnw test`. 예상: 실패 0. 총 건수·실패·건너뜀 수를 기록한다(이전 기준 103건, 실패 0, 건너뜀 2).

- [ ] **Step 2: Gradle 검사 시도** — `./gradlew clean check`. 통과·실패·실행 불가 중 무엇이었는지와 실패 시 원인 요약을 기록한다. Gradle 설정은 이 작업에서 고치지 않는다.

- [ ] **Step 3: 문서 갱신**
  - `docs/feature-spec.md`: "내 정보" 기능과 역할별 권한 표(설계 문서의 목표 표)를 해당 장에 추가한다.
  - `docs/db-design.md`: `owner_accounts` 테이블(컬럼, 제약, 새 테이블로 둔 이유)과 전화번호 규칙 변경을 추가한다.
  - `docs/handoff.md`: 앱 구현 범위에 이 기능을 추가하고, Step 1·2의 검증 결과와 미검증 항목(실제 MySQL에서 `owner_accounts` 자동 생성, 임시 서버 미배포, 브라우저 한글·모바일 화면)을 적는다.

- [ ] **Step 4: 변경 확인** — `git status --short`로 의도하지 않은 파일(빌드 산출물, `.env`)이 새로 잡히지 않았는지 확인한다. 커밋은 하지 않는다.
