package org.springframework.samples.petclinic.account;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads sign-in data from the users table.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	private final UserEmailRepository emails;

	public AppUserDetailsService(AppUserRepository users, UserEmailRepository emails) {
		this.users = users;
		this.emails = emails;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		AppUser user = this.users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
		boolean enabled = !AppUser.ROLE_USER.equals(user.getRole())
				|| this.emails.findById(user.getId()).map(e -> e.getVerifiedEmail() != null).orElse(true);
		return User.withUsername(user.getUsername())
			.password(user.getPassword())
			.roles(user.getRole())
			.disabled(!enabled)
			.build();
	}

}
