package org.springframework.samples.petclinic.account;

import java.security.Principal;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the signed-in user available to every page so the menu can show the right items.
 */
@ControllerAdvice
class CurrentUserAdvice {

	private final AppUserRepository users;

	private final UserEmailService emails;

	CurrentUserAdvice(AppUserRepository users, UserEmailService emails) {
		this.users = users;
		this.emails = emails;
	}

	@ModelAttribute("emailPrompt")
	boolean emailPrompt(Principal principal, jakarta.servlet.http.HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (principal == null || path.startsWith("/my/email") || path.startsWith("/verify-email")
				|| path.startsWith("/password/") || (request.getSession(false) != null
						&& Boolean.TRUE.equals(request.getSession(false).getAttribute("emailPromptDismissed"))))
			return false;
		return this.users.findByUsername(principal.getName())
			.map(user -> this.emails.verifiedEmail(user.getId()).isEmpty())
			.orElse(false);
	}

	@ModelAttribute("currentUsername")
	String currentUsername(Principal principal) {
		return (principal != null) ? principal.getName() : null;
	}

	@ModelAttribute("currentUserIsStaff")
	boolean currentUserIsStaff(Principal principal) {
		return principal instanceof Authentication authentication && authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> ("ROLE_" + AppUser.ROLE_STAFF).equals(authority.getAuthority()));
	}

}
