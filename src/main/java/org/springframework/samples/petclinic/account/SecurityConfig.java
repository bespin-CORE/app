package org.springframework.samples.petclinic.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Browsing hospitals and schedules is public. Reservations need a signed-in user. Owner
 * records and the admin pages are for staff only.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth.requestMatchers("/owners/**", "/admin/**")
			.hasRole(AppUser.ROLE_STAFF)
			.requestMatchers("/reservations/**", "/my/**")
			.authenticated()
			.requestMatchers("/", "/resources/**", "/webjars/**", "/error", "/oups", "/login", "/signup",
					"/verify-email", "/verify-email/resend", "/password/**", "/hospitals/**", "/schedules/**",
					"/api/schedules/**", "/vets", "/vets.html", "/actuator/health/**", "/actuator/info",
					"/actuator/prometheus")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginPage("/login")
				.defaultSuccessUrl("/reservations")
				.failureHandler((request, response,
						ex) -> response.sendRedirect(request.getContextPath()
								+ (ex instanceof DisabledException ? "/login?unverified" : "/login?error")))
				.permitAll())
			.logout(logout -> logout.logoutSuccessUrl("/"));
		return http.build();
	}

	@Bean
	DaoAuthenticationProvider authenticationProvider(AppUserDetailsService users, PasswordEncoder encoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(encoder);
		provider.setPreAuthenticationChecks(user -> {
			if (!user.isAccountNonLocked())
				throw new LockedException("Account locked");
			if (!user.isAccountNonExpired())
				throw new AccountExpiredException("Account expired");
		});
		provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
		return provider;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
