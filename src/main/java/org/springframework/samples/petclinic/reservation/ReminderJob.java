package org.springframework.samples.petclinic.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.samples.petclinic.account.UserEmailService;
import org.springframework.samples.petclinic.mail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReminderJob {

	private final ReservationRepository reservations;

	private final UserEmailService emails;

	private final MailComposer composer;

	private final MailOutboxService outbox;

	private final Clock clock;

	private final MailOutboxRepository mailRows;

	public ReminderJob(ReservationRepository reservations, UserEmailService emails, MailComposer composer,
			MailOutboxService outbox, Clock clock, MailOutboxRepository mailRows) {
		this.reservations = reservations;
		this.emails = emails;
		this.composer = composer;
		this.outbox = outbox;
		this.clock = clock;
		this.mailRows = mailRows;
	}

	@Scheduled(fixedDelayString = "PT15M")
	@Transactional(readOnly = true)
	public int enqueueDueReminders() {
		LocalDateTime now = LocalDateTime.now(this.clock);
		int count = 0;
		for (Reservation reservation : this.reservations
			.findBookedOnDates(List.of(now.toLocalDate(), now.toLocalDate().plusDays(1)))) {
			TreatmentSchedule schedule = reservation.getSchedule();
			LocalDateTime start = schedule.getTreatmentDate().atTime(schedule.getStartTime());
			LocalDateTime threshold = schedule.getTreatmentDate().minusDays(1).atTime(LocalTime.of(18, 0));
			if (now.isBefore(threshold) || !now.isBefore(start))
				continue;
			String key = "REMINDER:" + reservation.getId() + ":" + schedule.getTreatmentDate();
			if (this.mailRows.existsByDedupeKey(key))
				continue;
			var email = this.emails.verifiedEmail(reservation.getUser().getId());
			if (email.isPresent() && this.outbox.enqueueOnce(MailKind.RESERVATION_REMINDER, email.get(),
					this.composer.compose(MailKind.RESERVATION_REMINDER, ReservationMailer.variables(reservation)),
					key))
				count++;
		}
		return count;
	}

}
