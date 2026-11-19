package org.springframework.samples.petclinic.account;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AccountController {

	private final AppUserRepository users;

	private final AccountService accounts;

	AccountController(AppUserRepository users, AccountService accounts) {
		this.users = users;
		this.accounts = accounts;
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

	@PostMapping("/signup")
	String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult result,
			RedirectAttributes redirect) {
		String raw = form.getUsername();
		String username = EmailAddresses.normalize(raw);
		form.setUsername(username);
		if (EmailAddresses.hasControlCharacters(raw) || !EmailAddresses.isValid(username))
			result.rejectValue("username", "signup.username.email");
		else if (this.users.existsByUsername(username))
			result.rejectValue("username", "duplicate");
		if (result.hasErrors())
			return "account/signup";
		try {
			this.accounts.register(form);
		}
		catch (AccountService.DuplicateUsernameException ex) {
			result.rejectValue("username", "duplicate");
			return "account/signup";
		}
		redirect.addFlashAttribute("messageKey", "signup.doneVerify");
		return "redirect:/login";
	}

}
