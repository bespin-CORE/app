package org.springframework.samples.petclinic.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.account.*;
import org.springframework.samples.petclinic.mail.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"spring.mail.username=test@example.com", "spring.mail.password=x" })
@Import(MailTestConfig.class)
class ReservationMailTests {

	@Autowired
	ReservationService service;

	@Autowired
	ReservationRepository reservations;

	@Autowired
	TreatmentScheduleRepository schedules;

	@Autowired
	PartnerHospitalRepository hospitals;

	@Autowired
	AppUserRepository users;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	MailOutboxRepository outbox;

	@Autowired
	Clock clock;

	@Autowired
	RecordingMailSender sender;

	@Autowired
	MailDispatcher dispatcher;

	AppUser user;

	TreatmentSchedule schedule;

	@BeforeEach
	void setup() {
		outbox.deleteAll();
		sender.reset();
		user = new AppUser();
		user.setUsername("mail" + System.nanoTime());
		user.setDisplayName("Test");
		user.setPassword("encoded");
		user.setCreatedAt(LocalDateTime.now(clock));
		user = users.saveAndFlush(user);
		schedule = new TreatmentSchedule();
		schedule.setHospital(hospitals.findById(1).orElseThrow());
		schedule.setTreatmentName("Dental");
		schedule.setTreatmentDate(LocalDateTime.now(clock).toLocalDate().plusDays(5));
		schedule.setStartTime(LocalTime.NOON);
		schedule.setCapacity(2);
		schedule.setBookingOpensAt(LocalDateTime.now(clock).minusDays(1));
		schedule = schedules.saveAndFlush(schedule);
	}

	private void verify() {
		UserEmail email = new UserEmail();
		email.setUserId(user.getId());
		email.setVerifiedEmail(user.getUsername() + "@example.com");
		emails.saveAndFlush(email);
	}

	@Test
	void bookingQueuesMailForVerifiedUser() {
		verify();
		service.book(user.getUsername(), schedule.getId(), "<b>콩</b>", null);
		MailOutbox row = outbox.findAll().get(0);
		assertThat(row.getKind()).isEqualTo(MailKind.RESERVATION_BOOKED);
		assertThat(row.getBodyText()).contains("Dental", schedule.getHospital().getName());
		assertThat(row.getBodyHtml()).contains("&lt;b&gt;").doesNotContain("<b>콩</b>");
	}

	@Test
	void noMailWithoutVerifiedEmail() {
		service.book(user.getUsername(), schedule.getId(), "pet", null);
		assertThat(outbox.count()).isZero();
	}

	@Test
	void changeAndCancelQueueMail() {
		verify();
		int id = service.book(user.getUsername(), schedule.getId(), "pet", null);
		service.change(user.getUsername(), id, schedule.getId(), "changed", null);
		service.cancel(user.getUsername(), id);
		assertThat(outbox.findAll()).extracting(MailOutbox::getKind)
			.containsExactly(MailKind.RESERVATION_BOOKED, MailKind.RESERVATION_CHANGED, MailKind.RESERVATION_CANCELLED);
		assertThat(outbox.findAll().get(1).getBodyText()).contains("changed");
	}

	@Test
	void failedBookingQueuesNothing() {
		verify();
		schedule.setCapacity(0);
		schedules.saveAndFlush(schedule);
		assertThatThrownBy(() -> service.book(user.getUsername(), schedule.getId(), "pet", null))
			.isInstanceOf(ReservationException.class);
		assertThat(outbox.count()).isZero();
	}

	@Test
	void mailFailureDoesNotFailBooking() {
		verify();
		sender.failNext(10);
		Integer id = service.book(user.getUsername(), schedule.getId(), "pet", null);
		dispatcher.dispatchDue();
		assertThat(reservations.findById(id).orElseThrow().getStatus()).isEqualTo(ReservationStatus.BOOKED);
		assertThat(outbox.findAll().get(0).getStatus()).isEqualTo(MailStatus.PENDING);
	}

}
