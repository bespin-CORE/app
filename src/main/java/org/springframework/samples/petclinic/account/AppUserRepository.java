package org.springframework.samples.petclinic.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

	Optional<AppUser> findByUsername(String username);

	boolean existsByUsername(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AppUser u where u.id=:id")
	Optional<AppUser> findByIdForUpdate(@Param("id") Integer id);

}
