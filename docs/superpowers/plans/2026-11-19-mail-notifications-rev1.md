# 메일 기능 개정 1 Implementation Plan

> **For agentic workers:** 기존 계획(`2026-11-19-mail-notifications.md`)의 Global Constraints를 그대로 따른다. 설계는 설계 문서의 "개정 1" 절이 기준이다.

**Goal:** 아이디를 이메일로 바꾸고 인증 전 로그인을 막으며, 불필요해진 기능을 걷어내고 점검 결과 4건을 고친다.

## Review Focus
1. 대문자·공백 섞인 이메일로 가입(` Hong@Naver.com `) 후 소문자로 로그인 → 같은 계정.
2. 26자 이메일, `@` 없는 값, `a..b@x.com`, `.a@x.com`, `a@x.c`, `x<y@z.com` → 가입 폼 오류.
3. 미인증 상태 로그인 → 로그인 실패 + 인증 안내(비밀번호가 틀린 경우와 구분되는 안내는 비밀번호가 맞을 때만).
4. 기존 계정(`user_emails` 행 없음)과 직원 계정 → 지금처럼 로그인.
5. 존재하지 않는 이메일로 인증 메일 재요청 → 존재하는 경우와 같은 응답.

### Task R1: 아이디 = 이메일 가입과 형식 검사
- `EmailAddresses.isValid`를 설계 개정 1의 규칙으로 교체하고 `MAX_LENGTH = 25` 상수를 둔다. `SignupForm`의 `email` 필드를 없애고 `username`에 이메일 규칙을 적용(정규화 후 검증, 메시지 키 `signup.username.email`).
- 가입 성공 시 `UserEmailService.requestVerification(user, username)` 그대로 호출(대기 주소 = 아이디).
- 가입 화면: 아이디 라벨을 이메일로, `type="email"`, `maxlength="25"`, 별도 이메일 칸 삭제.
- 가입 동시성: 컨트롤러의 `@Transactional`을 없애고 사용자 저장과 인증 요청을 서비스 메서드(`AccountService.register(SignupForm) -> AppUser`, 중복 시 `DuplicateUsernameException`) 하나의 트랜잭션으로 옮긴다. 컨트롤러는 예외를 잡아 `username` 필드 오류.
- 테스트: Review Focus 1·2, `duplicateUsernameIsFormError`(같은 아이디 두 번 가입 → 두 번째는 200 + 필드 오류), 기존 가입 테스트의 아이디를 이메일 형식으로 수정.
- 커밋: `feat: 이메일을 아이디로 쓰는 회원가입`

### Task R2: 인증 전 로그인 차단과 재요청
- `AppUserDetailsService`: `USER` 역할이고 `user_emails` 행이 있으며 `verifiedEmail`이 없으면 `enabled=false`. `STAFF`와 행이 없는 기존 계정은 영향 없음.
- 로그인 실패 처리: 원인이 `DisabledException`이면 `/login?unverified`로 보내고 인증 안내와 재요청 링크 표시. 그 외는 지금처럼 `/login?error`.
- `GET/POST /verify-email/resend`(공개): 이메일 입력, 정규화 후 미인증 계정이면 `requestVerification`(1시간 3회), 항상 `redirect:/verify-email/resend?sent`.
- 테스트: Review Focus 3·4·5, `verifiedUserCanLogin`, `resendLimitedToThreePerHour`.
- 커밋: `feat: 이메일 인증 전 로그인 차단과 인증 메일 재요청`

### Task R3: 불필요한 기능 제거
- 삭제: 팝업(`CurrentUserAdvice`의 `emailPrompt`, 레이아웃 마크업, dismiss 경로, `EmailPromptTests`), 예약 직후 단계(`ReservationController`는 항상 `redirect:/reservations`), `RESERVATION_SUMMARY`(종류·템플릿·발송 코드·테스트), `/my/email` 입력·변경(`EmailController`의 해당 경로·화면·테스트). `UserEmailService.verify`의 `TAKEN` 분기는 유지해도 되나 화면 문구는 일반 실패로 통일.
- 사용하지 않게 된 메시지 키는 11개 파일에서 함께 삭제.
- 테스트: 남은 테스트 전체 통과, 삭제한 경로는 404 또는 존재하지 않음.
- 커밋: `refactor: 아이디-이메일 통합으로 불필요해진 이메일 등록 기능 제거`

### Task R4: 점검 결과 정리
- `ReminderJob`: `existsByDedupeKey` 확인 후 새 행일 때만 `compose` 호출. 테스트 `doesNotComposeWhenAlreadyQueued`(두 번째 실행에서 composer 호출 0회 — 스파이 사용).
- 컨트롤러의 클래스 전체 이름을 import로 정리.
- 커밋: `refactor: 사전 알림 중복 확인 순서와 import 정리`

### Task R5: 검증과 문서
- `./mvnw -o test` 전체, JDK 17 `./gradlew clean check` 통과.
- `docs/feature-spec.md`, `docs/db-design.md`, README의 메일 절을 개정 1 기준으로 수정. rulings 파일에 개정 작업의 판단 추가.
- 커밋: `docs: 아이디-이메일 통합 반영`
