package org.springframework.samples.petclinic.reservation;

import java.util.Map;
import jakarta.persistence.EntityManager;
import org.springframework.samples.petclinic.account.UserEmailService;
import org.springframework.samples.petclinic.mail.*;
import org.springframework.stereotype.Component;

@Component
public class ReservationMailer {

	private final ReservationRepository reservations;

	private final UserEmailService emails;

	private final MailComposer composer;

	private final MailOutboxService outbox;

	private final EntityManager entityManager;

	public ReservationMailer(ReservationRepository reservations, UserEmailService emails, MailComposer composer,
			MailOutboxService outbox, EntityManager entityManager) {
		this.reservations = reservations;
		this.emails = emails;
		this.composer = composer;
		this.outbox = outbox;
		this.entityManager = entityManager;
	}

	public void reservationBooked(Integer id) {
		enqueue(id, MailKind.RESERVATION_BOOKED);
	}

	public void reservationChanged(Integer id) {
		enqueue(id, MailKind.RESERVATION_CHANGED);
	}

	public void reservationCancelled(Integer id) {
		enqueue(id, MailKind.RESERVATION_CANCELLED);
	}

	private void enqueue(Integer id, MailKind kind) {
		Reservation row = this.reservations.findById(id).orElseThrow();
		this.entityManager.refresh(row);
		this.emails.verifiedEmail(row.getUser().getId())
			.ifPresent(email -> this.outbox.enqueue(kind, email, this.composer.compose(kind, variables(row))));
	}

	public static Map<String, Object> variables(Reservation row) {
		return Map.of("hospital", row.getSchedule().getHospital().getName(), "treatment",
				row.getSchedule().getTreatmentName(), "date", row.getSchedule().getTreatmentDate(), "time",
				row.getSchedule().getStartTime(), "petName", row.getPetName(), "status", row.getStatus().name());
	}

}
