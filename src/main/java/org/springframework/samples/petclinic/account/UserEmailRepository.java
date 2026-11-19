package org.springframework.samples.petclinic.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEmailRepository extends JpaRepository<UserEmail, Integer> {

	Optional<UserEmail> findByVerifiedEmail(String email);

}
