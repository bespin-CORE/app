package org.springframework.samples.petclinic.account;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EmailController {

	private final UserEmailService emails;

	public EmailController(UserEmailService emails) {
		this.emails = emails;
	}

	@GetMapping("/verify-email")
	String verify(@RequestParam(required = false) String token, Model model) {
		model.addAttribute("result", this.emails.verify(token));
		return "account/verifyResult";
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
					return "redirect:/verify-email/resend?sent";
				}
			}
		}
		return "redirect:/verify-email/resend?sent";
	}

}
