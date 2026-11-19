package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

	private final AppUserRepository users;

	private final PasswordEncoder encoder;

	private final Clock clock;

	private final UserEmailService emails;

	public AccountService(AppUserRepository users, PasswordEncoder encoder, Clock clock, UserEmailService emails) {
		this.users = users;
		this.encoder = encoder;
		this.clock = clock;
		this.emails = emails;
	}

	@Transactional
	public AppUser register(SignupForm form) {
		AppUser user = new AppUser();
		user.setUsername(form.getUsername());
		user.setPassword(this.encoder.encode(form.getPassword()));
		user.setDisplayName(form.getDisplayName());
		user.setCreatedAt(LocalDateTime.now(this.clock));
		try {
			this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateUsernameException(ex);
		}
		this.emails.requestVerification(user, user.getUsername());
		return user;
	}

	public static class DuplicateUsernameException extends RuntimeException {

		public DuplicateUsernameException(Throwable cause) {
			super(cause);
		}

	}

}
