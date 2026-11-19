package org.springframework.samples.petclinic.reservation;

import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.samples.petclinic.account.*;
import org.springframework.samples.petclinic.mail.MailOutboxRepository;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "vetcore.scheduling.enabled=false")
@Import(ReminderJobTests.Config.class)
class ReminderJobTests {

	static class MutableClock extends Clock {

		Instant instant = Instant.parse("2026-01-01T09:00:00Z");

		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		public Clock withZone(ZoneId zone) {
			return this;
		}

		public Instant instant() {
			return instant;
		}

		void set(LocalDateTime time) {
			instant = time.atZone(getZone()).toInstant();
		}

	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		MutableClock fixedClock() {
			return new MutableClock();
		}

	}

	@Autowired
	ReminderJob job;

	@Autowired
	MutableClock clock;

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

	Reservation reservation;

	TreatmentSchedule schedule;

	AppUser user;

	@BeforeEach
	void setup() {
		outbox.deleteAll();
		reservations.deleteAll();
		clock.set(LocalDateTime.of(2026, 1, 1, 18, 0));
		user = new AppUser();
		user.setUsername("rem" + System.nanoTime());
		user.setPassword("encoded");
		user.setDisplayName("Test");
		user.setCreatedAt(LocalDateTime.now(clock));
		user = users.saveAndFlush(user);
		UserEmail email = new UserEmail();
		email.setUserId(user.getId());
		email.setVerifiedEmail(user.getUsername() + "@example.com");
		emails.saveAndFlush(email);
		schedule = new TreatmentSchedule();
		schedule.setHospital(hospitals.findById(1).orElseThrow());
		schedule.setTreatmentName("Dental");
		schedule.setTreatmentDate(LocalDate.of(2026, 1, 2));
		schedule.setStartTime(LocalTime.of(10, 0));
		schedule.setCapacity(5);
		schedule.setBookingOpensAt(LocalDateTime.now(clock).minusDays(1));
		schedule = schedules.saveAndFlush(schedule);
		reservation = new Reservation();
		reservation.setSchedule(schedule);
		reservation.setUser(user);
		reservation.setPetName("pet");
		reservation.setCreatedAt(LocalDateTime.now(clock));
		reservation.setUpdatedAt(LocalDateTime.now(clock));
		reservation = reservations.saveAndFlush(reservation);
	}

	@Test
	void notBefore1800DayBefore() {
		clock.set(LocalDateTime.of(2026, 1, 1, 17, 59));
		assertThat(job.enqueueDueReminders()).isZero();
	}

	@Test
	void onceAfter1800() {
		assertThat(job.enqueueDueReminders()).isEqualTo(1);
		assertThat(job.enqueueDueReminders()).isZero();
		assertThat(outbox.count()).isEqualTo(1);
	}

	@Test
	void lateRunStillSends() {
		clock.set(LocalDateTime.of(2026, 1, 2, 9, 0));
		assertThat(job.enqueueDueReminders()).isEqualTo(1);
	}

	@Test
	void noneAfterStart() {
		clock.set(LocalDateTime.of(2026, 1, 2, 10, 0));
		assertThat(job.enqueueDueReminders()).isZero();
	}

	@Test
	void skipsCancelledAndUnverified() {
		reservation.setStatus(ReservationStatus.CANCELLED);
		reservations.saveAndFlush(reservation);
		assertThat(job.enqueueDueReminders()).isZero();
		reservation.setStatus(ReservationStatus.BOOKED);
		reservations.saveAndFlush(reservation);
		emails.deleteById(user.getId());
		assertThat(job.enqueueDueReminders()).isZero();
	}

	@Test
	void movedDateGetsNewReminder() {
		assertThat(job.enqueueDueReminders()).isEqualTo(1);
		schedule.setTreatmentDate(LocalDate.of(2026, 1, 3));
		schedules.saveAndFlush(schedule);
		clock.set(LocalDateTime.of(2026, 1, 2, 18, 0));
		assertThat(job.enqueueDueReminders()).isEqualTo(1);
	}

}
