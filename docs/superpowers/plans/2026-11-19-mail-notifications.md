# 메일 알림·이메일 인증·비밀번호 재설정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 예약 확인·사전 알림 메일, 이메일 등록·소유 확인, 비밀번호 재설정 링크를 Gmail SMTP와 발송 대기 테이블(outbox)로 구현한다.

**Architecture:** 업무 트랜잭션 안에서 완성된 메일을 `mail_outbox`에 넣고, 주기 작업이 조건부 UPDATE로 행을 집어 `JavaMailSender`로 보낸다. 이메일·토큰은 새 테이블 `user_emails`, `account_tokens`에 둔다. 기존 테이블은 바꾸지 않는다.

**Tech Stack:** Java 17, Spring Boot 4.1(MVC, Security, Data JPA, Mail, Scheduling, Session JDBC), Thymeleaf, H2(테스트)·MySQL 8.4.

**Spec:** `docs/superpowers/specs/2026-11-19-mail-notifications-design.md`

## Global Constraints

- 저장소 `bespin-CORE/app` 사본(`/home/qorgh529/orca/workspaces/final-project/petclinic-final-team`). 경로는 이 루트 기준.
- 시작 전에 이슈를 만들고, `feat/2-owner-self-service`에서 브랜치 `feat/<이슈번호>-mail-notifications`를 딴다. 작업마다 커밋하고, push·PR은 사용자 확인 후에 한다.
- 새 패키지 `org.springframework.samples.petclinic.mail`. 계정 관련은 기존 `account`, 예약 관련은 `reservation`.
- 새 의존성은 `spring-boot-starter-mail` 하나뿐(`pom.xml`, `build.gradle` 둘 다). 테스트 의존성 추가 금지.
- 스키마: 새 테이블 3개만, h2·mysql·postgres `schema.sql` 모두. 기존 테이블 `ALTER` 금지.
- 메시지 키는 11개 `messages*.properties` 모두(ko·en 번역, 나머지는 영어). 메일 템플릿 HTML도 `I18nPropertiesSyncTest` 규칙(문구는 `#{}`)을 따른다.
- 시각은 `Clock` 빈. 메일은 한국어 고정(`Locale.KOREAN`).
- 로그에 토큰 원문·앱 비밀번호를 남기지 않고, 이메일은 `ab***@domain`으로 마스킹.
- 각 작업 검증: `./mvnw -o test -Dtest=<테스트>`. 마지막 작업에서 `./mvnw -o test`와 JDK 17 `./gradlew clean check` 전체.
- 코드 포맷: 커밋 전 `./mvnw -o spring-javaformat:apply`.

## Review Focus

1. 이메일 앞뒤 공백·대문자(` User@Example.COM `) → 저장·조회 모두 `user@example.com`으로 정규화. (Task 3, 재설정 조회는 Task 6)
2. 인증 링크를 두 번 클릭(또는 메일 보안 스캐너가 먼저 열기) → 두 번째는 500이 아니라 "유효하지 않은 링크" 화면. (Task 3)
3. 줄바꿈이 섞인 이메일(`a@b.com\r\nBcc: x@y.com`) → 형식 오류로 거부, 메일 헤더에 들어가지 않음. (Task 3)
4. 발송 중 한 건이 예외 → 같은 배치의 나머지는 계속 발송. (Task 2)
5. 반려동물 이름에 HTML(`<b>콩</b>`) → HTML 본문에서 이스케이프. (Task 4)

## File Structure

| 파일 | 역할 |
| --- | --- |
| `pom.xml`, `build.gradle` | mail 의존성 |
| `src/main/resources/application.properties` | `spring.mail.*`, `vetcore.mail.*` |
| `src/main/resources/db/{h2,mysql,postgres}/schema.sql` | 새 테이블 3개 |
| `mail/MailKind.java`, `MailStatus.java`, `MailOutbox.java`, `MailOutboxRepository.java` | 대기 행 |
| `mail/MailOutboxService.java` | 넣기 |
| `mail/MailComposer.java`, `ComposedMail.java` | 템플릿으로 제목·본문 생성 |
| `mail/MailDispatcher.java` | 집기·발송·재시도 |
| `mail/MailProperties.java`, `MailConfiguration.java` | 설정 바인딩, 스케줄 등록, 시작 검사 |
| `account/UserEmail.java`, `UserEmailRepository.java`, `UserEmailService.java`, `EmailAddresses.java` | 이메일·인증 |
| `account/AccountToken.java`, `TokenPurpose.java`, `AccountTokenRepository.java`, `AccountTokenService.java` | 토큰 |
| `account/EmailController.java` | `/my/email`, `/verify-email`, 팝업 닫기 |
| `account/PasswordResetService.java`, `PasswordResetController.java` | 재설정 |
| `account/SignupForm.java`, `AccountController.java`, `SecurityConfig.java`, `CurrentUserAdvice.java` (수정) | 가입 이메일, 공개 경로, 팝업 |
| `reservation/ReservationMailer.java`, `ReminderJob.java` | 예약 메일, 사전 알림 |
| `reservation/ReservationService.java`, `ReservationController.java`, `ReservationRepository.java` (수정) | 훅, 예약 직후 단계, 알림 조회 |
| `templates/mail/*.html`, `templates/mail-text/*.txt` | 메일 템플릿 |
| `templates/account/email.html`, `verifyResult.html`, `forgot.html`, `reset.html`; `login.html`, `signup.html`, `fragments/layout.html` (수정) | 화면 |
| 테스트: `src/test/java/.../mail/RecordingMailSender.java`, `MailTestConfig.java`, 작업별 테스트 | |

---

### Task 1: 의존성·설정·스키마·엔티티

**Files:** 위 표의 의존성, 설정, 스키마, `MailKind`, `MailStatus`, `MailOutbox(+Repository)`, `UserEmail(+Repository)`, `AccountToken(+Repository)`, `TokenPurpose`, `MailProperties`, `MailConfiguration`. Test: `src/test/java/.../mail/MailSchemaTests.java`, `RecordingMailSender.java`, `MailTestConfig.java`.

**Interfaces:**
- Produces:
  - `enum MailKind { RESERVATION_BOOKED, RESERVATION_CHANGED, RESERVATION_CANCELLED, RESERVATION_REMINDER, RESERVATION_SUMMARY, VERIFY_EMAIL, RESET_PASSWORD }`
  - `enum MailStatus { PENDING, SENDING, SENT, FAILED, SKIPPED }`, `enum TokenPurpose { VERIFY_EMAIL, RESET_PASSWORD }`
  - 엔티티 필드는 설계 문서 "데이터" 표 그대로(카멜 표기). `UserEmail`의 PK는 `userId`(`users.id`), `MailOutbox`·`AccountToken`은 `BaseEntity` 상속.
  - `UserEmailRepository extends JpaRepository<UserEmail,Integer>`: `Optional<UserEmail> findByVerifiedEmail(String)`
  - `AccountTokenRepository`: `Optional<AccountToken> findByTokenHashAndPurpose(String, TokenPurpose)`, `int markUsed(Integer id, LocalDateTime now)`(조건: `usedAt IS NULL AND expiresAt > now`), `int invalidateUnused(Integer userId, TokenPurpose, LocalDateTime now)`(미사용을 `usedAt=now`로), `long countByUserIdAndPurposeAndCreatedAtAfter(Integer, TokenPurpose, LocalDateTime)`
  - `MailOutboxRepository`: `List<MailOutbox> findDue(LocalDateTime now, Pageable)`(설계의 PENDING·만료된 SENDING 조건, `id` 오름차순), `int claim(Integer id, LocalDateTime now, LocalDateTime lockedUntil)`(같은 조건일 때만 `SENDING`), `boolean existsByDedupeKey(String)`
  - `@ConfigurationProperties("vetcore.mail") record MailProperties(boolean enabled, String baseUrl, String from, int batchSize, Duration dispatchInterval, int maxAttempts)` 기본값: `false`, `http://localhost:8080`, 빈 값(빈 값이면 `spring.mail.username`), `20`, `5s`, `5`
  - `vetcore.scheduling.enabled`(기본 `true`)가 `true`일 때만 `@EnableScheduling` 설정이 켜진다. 테스트 리소스에서 `false`.
  - 시작 검사: `vetcore.mail.enabled=true`인데 `spring.mail.username` 또는 `password`가 비면 기동 실패.
  - 설정: `spring.mail.host=smtp.gmail.com`, `port=587`, `username=${MAIL_USERNAME:}`, `password=${MAIL_PASSWORD:}`, `properties.mail.smtp.auth=true`, `starttls.enable=true`, `starttls.required=true`, `connectiontimeout`·`timeout`·`writetimeout=10000`; `vetcore.mail.enabled=${VETCORE_MAIL_ENABLED:false}`, `base-url=${VETCORE_MAIL_BASE_URL:http://localhost:8080}`.
  - 테스트 지원: `RecordingMailSender extends JavaMailSenderImpl`가 `send(MimeMessage...)`를 기록만 하고(`List<MimeMessage> sent()`, `void failNext(int n)`, `void reset()`), `MailTestConfig`(`@TestConfiguration`, `@Primary`)로 등록. 테스트 속성: `vetcore.mail.enabled=true`, `spring.mail.username=test@example.com`, `spring.mail.password=x`, `vetcore.scheduling.enabled=false`.

- [ ] **Step 1: 실패하는 테스트** — `MailSchemaTests`(`@SpringBootTest`, `@Import(MailTestConfig.class)`):
  - `verifiedEmailIsUnique`: 두 `UserEmail`에 같은 `verifiedEmail` 저장 → `DataIntegrityViolationException`; `verifiedEmail=null` 두 행은 성공.
  - `dedupeKeyIsUnique`: 같은 `dedupeKey` 두 `MailOutbox` → 예외; `null` 두 행은 성공.
  - `tokenHashIsUnique`: 같은 `tokenHash` 두 `AccountToken` → 예외.
  - `claimIsExclusive`: `PENDING` 행에 `claim` 두 번 → 첫 번째 1, 두 번째 0.
  - `markUsedOnlyOnce`: 유효 토큰 `markUsed` 두 번 → 1, 0. 만료 토큰 → 0.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=MailSchemaTests` → 컴파일 실패.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로. 스키마 컬럼 크기: 이메일 254, `token_hash` 64, `subject` 200, `last_error` 500, 본문은 `TEXT`(h2 `CLOB`).
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS, `./mvnw -o test -Dtest='I18nPropertiesSyncTest,ReservationWebTests'` PASS.
- [ ] **Step 5: 커밋** — `chore: 메일 의존성, 설정과 메일·이메일·토큰 테이블 추가`

### Task 2: 발송 대기열과 발송 작업

**Files:** Create `mail/MailOutboxService.java`, `mail/MailComposer.java`, `mail/ComposedMail.java`, `mail/MailDispatcher.java`. Test `mail/MailDispatcherTests.java`.

**Interfaces:**
- Consumes: Task 1 전체.
- Produces:
  - `record ComposedMail(String subject, String text, String html)`
  - `MailComposer.compose(MailKind kind, Map<String,Object> vars) -> ComposedMail` — HTML은 `templates/mail/{kind 소문자-하이픈}.html`, 텍스트는 `templates/mail-text/{같은 이름}.txt`(TEXT 모드 리졸버 추가), 제목은 메시지 키 `mail.subject.{kind}`. `Locale.KOREAN`. vars에 `baseUrl` 자동 추가.
  - `MailOutboxService.enqueue(MailKind kind, String recipient, ComposedMail mail) -> void` (현재 트랜잭션에 참여)
  - `MailOutboxService.enqueueOnce(MailKind kind, String recipient, ComposedMail mail, String dedupeKey) -> boolean` — 새 트랜잭션(`REQUIRES_NEW`), 이미 있으면 `false`(존재 확인 후 삽입, 경합 시 UNIQUE 위반을 잡아 `false`)
  - `MailDispatcher.dispatchDue() -> int` (보낸 건수). 스케줄: `fixedDelayString="${vetcore.mail.dispatch-interval:5s}"`. 보내는 사람은 `"VetCore" <from>`, 본문은 텍스트·HTML 두 가지(multipart/alternative), UTF-8.
  - 재시도 간격(시도 횟수 1~4 뒤): 1분, 5분, 15분, 1시간. `attempts >= maxAttempts`면 `FAILED`. `locked_until = now + 2분`.
  - `SENT` 후 7일 지난 `VERIFY_EMAIL`·`RESET_PASSWORD` 본문을 비우는 `MailDispatcher.purgeSentLinkBodies() -> int`(하루 1회 스케줄).

- [ ] **Step 1: 실패하는 테스트** — `MailDispatcherTests`(`Clock`은 고정 시계 빈으로 교체):
  - `sendsPendingAndMarksSent`: 1행 넣고 `dispatchDue()` → 1, `RecordingMailSender.sent()` 1건(받는 사람·제목 일치), 상태 `SENT`.
  - `failureSchedulesRetry`: `failNext(1)` → 상태 `PENDING`, `attempts=1`, `nextAttemptAt=now+1분`, `lastError`에 비밀번호 문자열 없음.
  - `givesUpAfterFiveAttempts`: 5번 실패 → `FAILED`.
  - `disabledMarksSkipped`: `enabled=false` 속성의 별도 테스트 클래스 또는 프로퍼티 교체 → `SKIPPED`, 발송 0.
  - `oneFailureDoesNotStopBatch` (Review Focus 4): 3행 중 첫 행만 실패 → 나머지 2건 `SENT`.
  - `expiredLockIsRetaken`: `SENDING`이고 `lockedUntil` 지난 행 → 다시 발송.
  - `enqueueOnceDeduplicates`: 같은 키로 두 번 → `true`, `false`, 행 1개.
  - `purgeClearsOldLinkBodies`: 8일 전 `SENT` 인증 메일 본문 비움, 예약 메일은 유지.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=MailDispatcherTests` FAIL(컴파일).
- [ ] **Step 3: 구현** — 행마다 `claim` 후 발송·상태 저장을 행 단위 트랜잭션으로. 템플릿 하나(`mail/verify-email` 정도)는 Task 3에서 만들므로 이 작업 테스트는 `ComposedMail`을 직접 만든다.
- [ ] **Step 4: 통과 확인** — PASS.
- [ ] **Step 5: 커밋** — `feat: 메일 발송 대기열과 재시도 발송 작업`

### Task 3: 이메일 등록·소유 확인과 가입 이메일

**Files:** Create `account/EmailAddresses.java`, `AccountTokenService.java`, `UserEmailService.java`, `EmailController.java`, `templates/account/email.html`, `verifyResult.html`, `templates/mail/verify-email.html`, `reservation-summary.html` + 같은 이름 `.txt`. Modify `SignupForm`, `AccountController`, `signup.html`, `SecurityConfig`(`/verify-email` 공개), messages. Test `account/EmailVerificationTests.java`.

**Interfaces:**
- Consumes: Task 2 `MailComposer`, `MailOutboxService.enqueue`.
- Produces:
  - `EmailAddresses.normalize(String) -> String`(trim, 소문자), `EmailAddresses.isValid(String) -> boolean`(254자 이하, `@` 하나, 공백·제어문자·줄바꿈 없음, 도메인에 점), `EmailAddresses.mask(String) -> String`
  - `AccountTokenService.issue(AppUser user, TokenPurpose purpose, String email, Duration ttl) -> String`(원문 반환, 해시 저장, 같은 용도의 미사용 토큰 무효화), `Optional<AccountToken> findValid(String raw, TokenPurpose purpose)`, `Optional<AccountToken> consume(String raw, TokenPurpose purpose)`(조건부 `markUsed`가 1일 때만), `long issuedSince(Integer userId, TokenPurpose purpose, LocalDateTime since)`
  - `UserEmailService.verifiedEmail(Integer userId) -> Optional<String>`, `UserEmailService.state(Integer userId) -> EmailState`(`record EmailState(String verified, String pending)`), `UserEmailService.requestVerification(AppUser user, String rawEmail) -> void`(정규화·검증 실패 시 `IllegalArgumentException`, 1시간 3회 초과 시 `VerificationRateLimitedException`; `pendingEmail` 저장, 토큰 24시간, `VERIFY_EMAIL` 메일 넣기, 링크 `{baseUrl}/verify-email?token=…`), `UserEmailService.verify(String rawToken) -> VerifyResult`(`enum VerifyResult { VERIFIED, INVALID, TAKEN }`; 성공 시 `verifiedEmail`·`verifiedAt` 저장, `pendingEmail=null`, 앞으로의 `BOOKED` 예약이 있으면 `RESERVATION_SUMMARY` 넣기)
  - 화면: `GET /my/email`(뷰 `account/email`, 모델 `emailState`, `after`), `POST /my/email`(필드 `email`, 성공 시 플래시 `messageKey=email.verificationSent`, `redirect:/my/email`(+`?after=booking` 유지)), `POST /my/email/resend`, `GET /verify-email?token=`(뷰 `account/verifyResult`, 모델 `result`)
  - 가입: `SignupForm.email`(필수), 가입 성공 시 `requestVerification` 호출, 완료 메시지 키 `signup.doneVerify`.

- [ ] **Step 1: 실패하는 테스트** — `EmailVerificationTests`(`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(MailTestConfig.class)`):
  - `signupRequiresEmail`: 이메일 없이 가입 → 폼 오류 `email`. 정상 가입 → outbox `VERIFY_EMAIL` 1행, 받는 사람이 입력 주소.
  - `normalizesEmail` (Review Focus 1): ` User@Example.COM ` → `pendingEmail = user@example.com`.
  - `rejectsHeaderInjection` (Review Focus 3): `a@b.com\r\nBcc: x@y.com` → 폼 오류, outbox 0행.
  - `verifyLinkVerifiesOnce` (Review Focus 2): outbox 본문에서 토큰을 꺼내 `GET /verify-email` → `VERIFIED`, 두 번째 → `INVALID`(200 화면).
  - `expiredTokenIsInvalid`: 시계를 25시간 뒤로 → `INVALID`.
  - `takenAddressIsRejected`: A가 인증한 주소를 B가 인증 → `TAKEN`, B의 `verifiedEmail` 비어 있음.
  - `resendLimitedToThreePerHour`: 4번째 다시 보내기 → 오류 메시지, outbox 증가 없음.
  - `tokenNotStoredInPlain`: `account_tokens`에 원문 토큰 문자열 없음.
  - `summaryMailAfterVerification`: 미인증 상태로 예약 1건 → 인증 → `RESERVATION_SUMMARY` 1행.
  - `changingEmailKeepsOldUntilVerified`: 인증 후 새 주소 입력 → `verifiedEmail`은 이전 주소 유지, `pendingEmail`은 새 주소.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=EmailVerificationTests` FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로. 기존 가입 테스트(`ReservationWebTests.signUp` 등)에 `email` 파라미터를 추가한다(사용자마다 다른 주소).
- [ ] **Step 4: 통과 확인** — `./mvnw -o test -Dtest='EmailVerificationTests,ReservationWebTests,MyProfileWebTests,I18nPropertiesSyncTest'` PASS.
- [ ] **Step 5: 커밋** — `feat: 이메일 등록과 소유 확인, 가입 시 이메일 필수`

### Task 4: 예약 메일과 예약 직후 이메일 단계

**Files:** Create `reservation/ReservationMailer.java`, 템플릿 `mail/reservation-booked|changed|cancelled.html` + `.txt`. Modify `ReservationService`, `ReservationController`, `templates/account/email.html`(`after=booking` 상단 안내), messages. Test `reservation/ReservationMailTests.java`.

**Interfaces:**
- Consumes: `UserEmailService.verifiedEmail`, `MailComposer`, `MailOutboxService.enqueue`.
- Produces: `ReservationMailer.reservationBooked(Integer reservationId)`, `reservationChanged(Integer reservationId)`, `reservationCancelled(Integer reservationId)` — 인증 이메일이 없으면 아무것도 하지 않음. 본문 변수: 병원명, 치료명, 치료일, 시작 시각, 반려동물 이름, 상태, `{baseUrl}/reservations`.
- `ReservationService.book/change/cancel` 성공 경로 끝에서(같은 트랜잭션) 위 메서드 호출. `book`의 재예약 경로 포함.
- `ReservationController.book`: 성공 후 인증 이메일이 없으면 `redirect:/my/email?after=booking`(플래시 `reservation.booked` 유지), 있으면 기존대로.

- [ ] **Step 1: 실패하는 테스트** — `ReservationMailTests`:
  - `bookingQueuesMailForVerifiedUser`: 인증 사용자 예약 → `RESERVATION_BOOKED` 1행, 본문에 치료명·병원명.
  - `noMailWithoutVerifiedEmail`: 미인증 예약 → 0행.
  - `changeAndCancelQueueMail`: 변경 → `RESERVATION_CHANGED`, 취소 → `RESERVATION_CANCELLED`.
  - `mailFailureDoesNotFailBooking`: `failNext(10)` 후 예약·`dispatchDue()` → 예약 `BOOKED` 유지.
  - `failedBookingQueuesNothing`: 정원 0 일정 예약 → 오류, outbox 0행(트랜잭션 롤백).
  - `petNameIsEscapedInHtml` (Review Focus 5): 반려동물 이름 `<b>콩</b>` → `bodyHtml`에 `&lt;b&gt;` 포함, `<b>콩</b>` 없음.
  - `unverifiedUserGoesToEmailStepAfterBooking`: 미인증 예약 POST → `redirectedUrl("/my/email?after=booking")`; 인증 사용자 → `/reservations`.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=ReservationMailTests` FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로. 테스트용 반려동물 등록은 `ReservationWebTests.registerPets` 방식 재사용.
- [ ] **Step 4: 통과 확인** — `./mvnw -o test -Dtest='ReservationMailTests,ReservationWebTests,ReservationServiceTests'` PASS.
- [ ] **Step 5: 커밋** — `feat: 예약 확인·변경·취소 메일과 예약 직후 이메일 등록 단계`

### Task 5: 사전 알림

**Files:** Create `reservation/ReminderJob.java`, 템플릿 `mail/reservation-reminder.html` + `.txt`. Modify `ReservationRepository`. Test `reservation/ReminderJobTests.java`.

**Interfaces:**
- Consumes: `MailOutboxService.enqueueOnce`, `UserEmailService.verifiedEmail`, `MailComposer`.
- Produces: `ReminderJob.enqueueDueReminders() -> int`(새로 넣은 건수), 스케줄 15분 간격. `ReservationRepository.findBookedOnDates(Collection<LocalDate> dates) -> List<Reservation>`(일정·병원·사용자 함께 로딩). 대상: `now >= (치료일-1) 18:00` 이고 `now < 치료일+시작 시각`. `dedupeKey = "REMINDER:" + reservationId + ":" + treatmentDate`.

- [ ] **Step 1: 실패하는 테스트** — `ReminderJobTests`(고정 시계):
  - `notBefore1800DayBefore`: 전날 17:59 → 0.
  - `onceAfter1800`: 전날 18:00 → 1, 다시 실행 → 0, outbox 1행.
  - `lateRunStillSends`: 치료 당일 시작 1시간 전 → 1.
  - `noneAfterStart`: 시작 시각 이후 → 0.
  - `skipsCancelledAndUnverified`: 취소 예약·미인증 사용자 → 0.
  - `movedDateGetsNewReminder`: 알림 후 다른 날짜로 변경 → 그 날짜 전날 18:00에 1건 추가.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=ReminderJobTests` FAIL.
- [ ] **Step 3: 구현** — 날짜 후보는 오늘·내일만 조회하고 시각 조건은 Java에서 거른다.
- [ ] **Step 4: 통과 확인** — PASS.
- [ ] **Step 5: 커밋** — `feat: 치료일 전날 사전 알림 메일`

### Task 6: 비밀번호 재설정

**Files:** Create `account/PasswordResetService.java`, `PasswordResetController.java`, `templates/account/forgot.html`, `reset.html`, `mail/reset-password.html` + `.txt`. Modify `login.html`(링크 `id="forgot-password"`), `SecurityConfig`(`/password/**` 공개), messages. Test `account/PasswordResetTests.java`.

**Interfaces:**
- Consumes: `AccountTokenService`, `EmailAddresses`, `UserEmailRepository.findByVerifiedEmail`, `MailOutboxService.enqueue`, `PasswordEncoder`, `FindByIndexNameSessionRepository<? extends Session>`.
- Produces:
  - `PasswordResetService.requestReset(String rawEmail) -> void`(정규화, 인증 주소 일치·1시간 3회 이하일 때만 토큰 30분과 메일; 그 외 조용히 종료)
  - `PasswordResetService.reset(String rawToken, String newPassword) -> boolean`(토큰 소비 성공 시 비밀번호 변경, 같은 사용자 미사용 재설정 토큰 무효화, `findByPrincipalName(username)`의 세션 모두 삭제)
  - 화면: `GET/POST /password/forgot`(POST 후 항상 `redirect:/password/forgot?sent`), `GET /password/reset?token=`(유효하면 폼 `account/reset`, 아니면 `account/verifyResult` 형식의 무효 안내), `POST /password/reset`(필드 `token`, `password`, `confirmPassword`; 8~64자, 일치 검사; 성공 시 플래시 `password.resetDone`, `redirect:/login`).

- [ ] **Step 1: 실패하는 테스트** — `PasswordResetTests`:
  - `sameResponseForKnownAndUnknown`: 등록 주소·미등록 주소 POST → 둘 다 `redirectedUrl("/password/forgot?sent")`; outbox는 등록 주소만 1행.
  - `matchesNormalizedEmail` (Review Focus 1): 인증 주소 `user@example.com`, 요청 ` USER@example.com ` → 1행.
  - `limitedToThreePerHour`: 4번째 요청 → outbox 증가 없음, 응답 동일.
  - `resetChangesPasswordOnce`: 토큰으로 변경 → 새 비밀번호로 `formLogin` 성공, 옛 비밀번호 실패; 같은 토큰 재사용 → 무효 안내.
  - `expiresAfter30Minutes`: 31분 후 → 무효.
  - `mismatchedConfirmIsFormError`: 확인 불일치 → 폼 오류, 비밀번호 그대로.
  - `existingSessionsAreEnded`: 로그인 세션 만든 뒤 재설정 → 그 세션으로 `/reservations` 요청 시 `/login`으로 이동.
  - `unverifiedEmailGetsNothing`: `pendingEmail`만 있는 주소 요청 → 0행.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=PasswordResetTests` FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로.
- [ ] **Step 4: 통과 확인** — `./mvnw -o test -Dtest='PasswordResetTests,ReservationWebTests'` PASS.
- [ ] **Step 5: 커밋** — `feat: 이메일 링크로 비밀번호 재설정`

### Task 7: 기존 사용자 이메일 등록 팝업

**Files:** Modify `CurrentUserAdvice`, `fragments/layout.html`, `EmailController`(`POST /my/email/prompt/dismiss`), messages. Test `account/EmailPromptTests.java`.

**Interfaces:**
- Produces: 모델 속성 `emailPrompt`(boolean): 로그인 && 인증 이메일 없음 && 세션 속성 `emailPromptDismissed` 없음 && 요청 경로가 `/my/email`, `/verify-email`, `/password/` 로 시작하지 않음. 팝업 마크업 `id="email-prompt"`, "지금 등록"(`/my/email`), "나중에"(POST dismiss, CSRF 포함, 이전 페이지 또는 `/`로 이동).

- [ ] **Step 1: 실패하는 테스트** — `EmailPromptTests`:
  - `shownForUserWithoutVerifiedEmail`: 미인증 사용자 `GET /` → `id="email-prompt"` 포함.
  - `hiddenForVerifiedUser`, `hiddenForAnonymous`.
  - `dismissHidesForSession`: 같은 `MockHttpSession`으로 dismiss 후 `GET /` → 미포함; 새 세션 → 다시 포함.
  - `hiddenOnEmailPage`: `GET /my/email` → 미포함.
- [ ] **Step 2: 실패 확인** — `./mvnw -o test -Dtest=EmailPromptTests` FAIL.
- [ ] **Step 3: 구현** — 기존 VetCore 스타일(`vc-card`, `vc-btn-primary`)로 고정 위치 팝업. 새 Tailwind 클래스를 쓰면 `src/main/tailwind/README.md` 절차로 CSS 재생성.
- [ ] **Step 4: 통과 확인** — `./mvnw -o test -Dtest='EmailPromptTests,I18nPropertiesSyncTest'` PASS.
- [ ] **Step 5: 커밋** — `feat: 이메일 미등록 사용자 안내 팝업`

### Task 8: 전체 검증과 문서

**Files:** Modify `docs/feature-spec.md`(URL 표·업무 규칙·검증 결과), `docs/db-design.md`(새 테이블 3개와 이유), `README` 또는 `docs/`의 실행 안내(메일 환경 변수 `MAIL_USERNAME`, `MAIL_PASSWORD`, `VETCORE_MAIL_ENABLED`, `VETCORE_MAIL_BASE_URL`).

- [ ] **Step 1:** `./mvnw -o test` → 실패 0. 건수 기록.
- [ ] **Step 2:** `JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9 ./gradlew clean check -Porg.gradle.java.installations.paths=$JAVA_HOME` → BUILD SUCCESSFUL. 건수 기록.
- [ ] **Step 3:** 문서 갱신. 미검증으로 남길 것: 실제 Gmail 발송, AWS·GCP 587 아웃바운드, 스팸 분류, MySQL 8.4에서 새 테이블 생성.
- [ ] **Step 4:** `git status`로 비밀 정보·빌드 산출물이 없는지 확인 후 커밋 `docs: 메일 기능 문서와 실행 설정 안내`. push·PR은 사용자 확인 후.
