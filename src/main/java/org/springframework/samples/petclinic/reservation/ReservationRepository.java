package org.springframework.samples.petclinic.reservation;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

	List<Reservation> findByUserUsernameOrderByCreatedAtDesc(String username);

	Optional<Reservation> findByIdAndUserUsername(Integer id, String username);

	Optional<Reservation> findByScheduleIdAndUserId(Integer scheduleId, Integer userId);

	long countByScheduleIdAndStatus(Integer scheduleId, ReservationStatus status);

	/**
	 * Each state change below names the state it expects. If another request changed the
	 * row first, 0 rows match and the caller must not touch the seat count.
	 */
	@Modifying
	@Query("update Reservation r set r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.CANCELLED, "
			+ "r.updatedAt = :now where r.id = :id and r.schedule.id = :scheduleId "
			+ "and r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.BOOKED")
	int markCancelled(@Param("id") Integer id, @Param("scheduleId") Integer scheduleId,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from Reservation r where r.id = :id "
			+ "and r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.CANCELLED")
	int deleteIfCancelled(@Param("id") Integer id);

	@Modifying
	@Query("update Reservation r set r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.BOOKED, "
			+ "r.petName = :petName, r.memo = :memo, r.updatedAt = :now where r.id = :id "
			+ "and r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.CANCELLED")
	int markRebooked(@Param("id") Integer id, @Param("petName") String petName, @Param("memo") String memo,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("update Reservation r set r.petName = :petName, r.memo = :memo, r.updatedAt = :now where r.id = :id "
			+ "and r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.BOOKED")
	int updateDetails(@Param("id") Integer id, @Param("petName") String petName, @Param("memo") String memo,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("update Reservation r set r.schedule = :newSchedule, r.petName = :petName, r.memo = :memo, "
			+ "r.updatedAt = :now where r.id = :id and r.schedule.id = :oldScheduleId "
			+ "and r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.BOOKED")
	int moveToSchedule(@Param("id") Integer id, @Param("oldScheduleId") Integer oldScheduleId,
			@Param("newSchedule") TreatmentSchedule newSchedule, @Param("petName") String petName,
			@Param("memo") String memo, @Param("now") LocalDateTime now);

	@Query("select r from Reservation r join fetch r.schedule s join fetch s.hospital join fetch r.user where r.status = org.springframework.samples.petclinic.reservation.ReservationStatus.BOOKED and s.treatmentDate in :dates")
	List<Reservation> findBookedOnDates(@Param("dates") Collection<LocalDate> dates);

}
