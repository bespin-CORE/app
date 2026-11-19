package org.springframework.samples.petclinic.account;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmailController {

	private final AppUserRepository users;

	private final UserEmailService emails;

	public EmailController(AppUserRepository users, UserEmailService emails) {
		this.users = users;
		this.emails = emails;
	}

	@GetMapping("/my/email")
	String page(Principal principal, @RequestParam(required = false) String after, Model model) {
		model.addAttribute("emailState", this.emails.state(user(principal).getId()));
		model.addAttribute("after", after);
		return "account/email";
	}

	@PostMapping("/my/email")
	String update(Principal principal, @RequestParam String email, @RequestParam(required = false) String after,
			RedirectAttributes redirect) {
		try {
			this.emails.requestVerification(user(principal), email);
			redirect.addFlashAttribute("messageKey", "email.verificationSent");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("messageKey", "email.invalid");
		}
		catch (UserEmailService.VerificationRateLimitedException ex) {
			redirect.addFlashAttribute("messageKey", "email.rateLimited");
		}
		return "redirect:/my/email" + ("booking".equals(after) ? "?after=booking" : "");
	}

	@PostMapping("/my/email/resend")
	String resend(Principal principal, RedirectAttributes redirect) {
		String pending = this.emails.state(user(principal).getId()).pending();
		return update(principal, pending == null ? "" : pending, null, redirect);
	}

	@GetMapping("/verify-email")
	String verify(@RequestParam(required = false) String token, Model model) {
		model.addAttribute("result", this.emails.verify(token));
		return "account/verifyResult";
	}

	@PostMapping("/my/email/prompt/dismiss")
	String dismiss(jakarta.servlet.http.HttpSession session) {
		session.setAttribute("emailPromptDismissed", true);
		return "redirect:/";
	}

	@GetMapping("/verify-email/resend")
	String resendPage() {
		return "account/resend";
	}

	@PostMapping("/verify-email/resend")
	String resendPublic(@RequestParam(defaultValue = "") String email) {
		if (!EmailAddresses.hasControlCharacters(email)) {
			String username = EmailAddresses.normalize(email);
			if (EmailAddresses.isValid(username)) {
				try {
					this.emails.resendForUsername(username);
				}
				catch (UserEmailService.VerificationRateLimitedException ex) {
				}
			}
		}
		return "redirect:/verify-email/resend?sent";
	}

	private AppUser user(Principal principal) {
		return this.users.findByUsername(principal.getName()).orElseThrow();
	}

}
