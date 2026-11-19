package org.springframework.samples.petclinic.mail;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"vetcore.mail.base-url=https://vetcore.example", "spring.mail.username=test@example.com",
		"spring.mail.password=x" })
@Import(MailTestConfig.class)
class MailTemplateTests {

	@Autowired
	MailComposer composer;

	private Map<String, Object> reservationVars(String petName, String status) {
		return Map.of("hospital", "서울중앙동물병원", "treatment", "종합 예방접종", "date", LocalDate.of(2026, 5, 20), "time",
				LocalTime.of(14, 30), "petName", petName, "status", status);
	}

	@Test
	void verifyEmailHasButtonFallbackSafetyAndNoExternalAssets() {
		String link = "https://vetcore.example/verify-email?token=verify-token-123";
		ComposedMail mail = composer.compose(MailKind.VERIFY_EMAIL, Map.of("link", link));

		assertThat(mail.html()).contains("VetCore")
			.contains("#00685f")
			.contains("href=\"" + link + "\"")
			.contains(">" + link + "<")
			.contains("버튼이 동작하지 않으면")
			.contains("24시간")
			.contains("가입하지 않았다면")
			.contains("회신하지 마세요")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains(link).contains("24시간").contains("가입하지 않았다면").contains("회신하지 마세요");
	}

	@Test
	void resetPasswordHasButtonFallbackSafetyAndNoExternalAssets() {
		String link = "https://vetcore.example/password/reset?token=reset-token-456";
		ComposedMail mail = composer.compose(MailKind.RESET_PASSWORD, Map.of("link", link));

		assertThat(mail.html()).contains("VetCore")
			.contains("#00685f")
			.contains("href=\"" + link + "\"")
			.contains(">" + link + "<")
			.contains("버튼이 동작하지 않으면")
			.contains("30분")
			.contains("직접 요청하지 않았다면")
			.contains("비밀번호는 바뀌지 않습니다")
			.contains("회신하지 마세요")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains(link)
			.contains("30분")
			.contains("직접 요청하지 않았다면")
			.contains("비밀번호는 바뀌지 않습니다")
			.contains("회신하지 마세요");
	}

	@Test
	void reservationBookedIncludesDetailsEscapesPetNameAndLinksToReservations() {
		ComposedMail mail = composer.compose(MailKind.RESERVATION_BOOKED, reservationVars("<b>콩</b>", "BOOKED"));

		assertThat(mail.html()).contains("VetCore")
			.contains("서울중앙동물병원")
			.contains("종합 예방접종")
			.contains("2026-05-20")
			.contains("14:30")
			.contains("&lt;b&gt;콩&lt;/b&gt;")
			.doesNotContain("<b>콩</b>")
			.contains("예약 완료")
			.contains("예약 확인하기")
			.contains("href=\"https://vetcore.example/reservations\"")
			.contains("회신하지 마세요")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains("서울중앙동물병원")
			.contains("종합 예방접종")
			.contains("2026-05-20")
			.contains("14:30")
			.contains("<b>콩</b>")
			.contains("예약 완료")
			.contains("https://vetcore.example/reservations")
			.contains("회신하지 마세요");
	}

	@Test
	void reservationChangedIncludesDetailsAndLinksToReservations() {
		ComposedMail mail = composer.compose(MailKind.RESERVATION_CHANGED, reservationVars("보리", "BOOKED"));

		assertThat(mail.html()).contains("변경")
			.contains("서울중앙동물병원")
			.contains("종합 예방접종")
			.contains("2026-05-20")
			.contains("14:30")
			.contains("보리")
			.contains("예약 확인하기")
			.contains("href=\"https://vetcore.example/reservations\"")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains("변경")
			.contains("서울중앙동물병원")
			.contains("https://vetcore.example/reservations")
			.contains("회신하지 마세요");
	}

	@Test
	void reservationCancelledEmphasizesStatusAndLinksToReservations() {
		ComposedMail mail = composer.compose(MailKind.RESERVATION_CANCELLED, reservationVars("보리", "CANCELLED"));

		assertThat(mail.html()).contains("취소")
			.contains("취소됨")
			.contains("서울중앙동물병원")
			.contains("종합 예방접종")
			.contains("2026-05-20")
			.contains("14:30")
			.contains("보리")
			.contains("예약 확인하기")
			.contains("href=\"https://vetcore.example/reservations\"")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains("취소됨")
			.contains("서울중앙동물병원")
			.contains("https://vetcore.example/reservations")
			.contains("회신하지 마세요");
	}

	@Test
	void reservationReminderHighlightsDateAndTimeInSubjectAndBody() {
		ComposedMail mail = composer.compose(MailKind.RESERVATION_REMINDER, reservationVars("보리", "BOOKED"));

		assertThat(mail.subject()).contains("2026-05-20").contains("14:30");

		assertThat(mail.html()).contains("2026-05-20")
			.contains("14:30")
			.contains("서울중앙동물병원")
			.contains("종합 예방접종")
			.contains("보리")
			.contains("예약 확인하기")
			.contains("href=\"https://vetcore.example/reservations\"")
			.doesNotContain("<img src=\"http")
			.doesNotContain("<style");

		assertThat(mail.text()).contains("2026-05-20")
			.contains("14:30")
			.contains("서울중앙동물병원")
			.contains("https://vetcore.example/reservations")
			.contains("회신하지 마세요");
	}

}
