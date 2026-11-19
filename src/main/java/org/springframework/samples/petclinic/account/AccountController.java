package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class AccountController {

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final UserEmailService emails;

	AccountController(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock, UserEmailService emails) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.emails = emails;
	}

	@GetMapping("/login")
	String login() {
		return "account/login";
	}

	@GetMapping("/signup")
	String signupForm(Model model) {
		model.addAttribute("signupForm", new SignupForm());
		return "account/signup";
	}

	@org.springframework.transaction.annotation.Transactional
	@PostMapping("/signup")
	String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult result,
			RedirectAttributes redirectAttributes) {
		if (!result.hasFieldErrors("username") && this.users.existsByUsername(form.getUsername())) {
			result.rejectValue("username", "duplicate");
		}
		if (EmailAddresses.hasControlCharacters(form.getEmail())
				|| !EmailAddresses.isValid(EmailAddresses.normalize(form.getEmail()))) {
			result.rejectValue("email", "email.invalid");
		}
		if (result.hasErrors()) {
			return "account/signup";
		}
		AppUser user = new AppUser();
		user.setUsername(form.getUsername());
		user.setPassword(this.passwordEncoder.encode(form.getPassword()));
		user.setDisplayName(form.getDisplayName());
		user.setCreatedAt(LocalDateTime.now(this.clock));
		try {
			this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Another request took the same username between the check and the insert
			result.rejectValue("username", "duplicate");
			return "account/signup";
		}
		this.emails.requestVerification(user, form.getEmail());
		redirectAttributes.addFlashAttribute("messageKey", "signup.doneVerify");
		return "redirect:/login";
	}

}
