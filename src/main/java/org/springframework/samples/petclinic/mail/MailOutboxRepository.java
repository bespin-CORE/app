package org.springframework.samples.petclinic.mail;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Integer> {

	@Query("select m from MailOutbox m where (m.status = org.springframework.samples.petclinic.mail.MailStatus.PENDING and m.nextAttemptAt <= :now) or (m.status = org.springframework.samples.petclinic.mail.MailStatus.SENDING and m.lockedUntil <= :now) order by m.id")
	List<MailOutbox> findDue(@Param("now") LocalDateTime now, Pageable pageable);

	@Modifying
	@Transactional
	@Query("update MailOutbox m set m.status = org.springframework.samples.petclinic.mail.MailStatus.SENDING, m.lockedUntil=:lockedUntil where m.id=:id and ((m.status = org.springframework.samples.petclinic.mail.MailStatus.PENDING and m.nextAttemptAt <= :now) or (m.status = org.springframework.samples.petclinic.mail.MailStatus.SENDING and m.lockedUntil <= :now))")
	int claim(@Param("id") Integer id, @Param("now") LocalDateTime now,
			@Param("lockedUntil") LocalDateTime lockedUntil);

	boolean existsByDedupeKey(String key);

	@Modifying
	@Transactional
	@Query("update MailOutbox m set m.bodyText=null, m.bodyHtml=null where m.status=org.springframework.samples.petclinic.mail.MailStatus.SENT and m.kind in (org.springframework.samples.petclinic.mail.MailKind.VERIFY_EMAIL,org.springframework.samples.petclinic.mail.MailKind.RESET_PASSWORD) and m.sentAt < :cutoff and (m.bodyText is not null or m.bodyHtml is not null)")
	int purgeLinkBodies(@Param("cutoff") LocalDateTime cutoff);

}
