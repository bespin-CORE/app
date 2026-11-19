package org.springframework.samples.petclinic.account;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Integer> {

	Optional<AccountToken> findByTokenHashAndPurpose(String hash, TokenPurpose purpose);

	long countByUserIdAndPurposeAndCreatedAtAfter(Integer userId, TokenPurpose purpose, LocalDateTime since);

	@Modifying
	@Transactional
	@Query("update AccountToken t set t.usedAt=:now where t.id=:id and t.usedAt is null and t.expiresAt > :now")
	int markUsed(@Param("id") Integer id, @Param("now") LocalDateTime now);

	@Modifying
	@Transactional
	@Query("update AccountToken t set t.usedAt=:now where t.userId=:userId and t.purpose=:purpose and t.usedAt is null")
	int invalidateUnused(@Param("userId") Integer userId, @Param("purpose") TokenPurpose purpose,
			@Param("now") LocalDateTime now);

}
