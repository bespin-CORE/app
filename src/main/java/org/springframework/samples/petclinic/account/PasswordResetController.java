package org.springframework.samples.petclinic.account;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

	private final PasswordResetService service;

	public PasswordResetController(PasswordResetService service) {
		this.service = service;
	}

	@GetMapping("/password/forgot")
	String forgot() {
		return "account/forgot";
	}

	@PostMapping("/password/forgot")
	String request(@RequestParam(defaultValue = "") String email) {
		this.service.requestReset(email);
		return "redirect:/password/forgot?sent";
	}

	@GetMapping("/password/reset")
	String form(@RequestParam(required = false) String token, Model model) {
		if (!this.service.valid(token))
			return invalid(model);
		model.addAttribute("token", token);
		return "account/reset";
	}

	@PostMapping("/password/reset")
	String reset(@RequestParam String token, @RequestParam String password, @RequestParam String confirmPassword,
			Model model, RedirectAttributes redirect) {
		if (!this.service.valid(token))
			return invalid(model);
		if (password.length() < 8 || password.length() > 64 || !password.equals(confirmPassword)) {
			model.addAttribute("token", token);
			model.addAttribute("errorKey", "password.invalid");
			return "account/reset";
		}
		if (!this.service.reset(token, password))
			return invalid(model);
		redirect.addFlashAttribute("messageKey", "password.resetDone");
		return "redirect:/login";
	}

	private String invalid(Model model) {
		model.addAttribute("result", UserEmailService.VerifyResult.INVALID);
		return "account/verifyResult";
	}

}
