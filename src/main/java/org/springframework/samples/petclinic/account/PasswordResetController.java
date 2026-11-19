package org.springframework.samples.petclinic.account;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
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
		PasswordResetForm form = new PasswordResetForm();
		form.setToken(token);
		model.addAttribute("resetForm", form);
		return "account/reset";
	}

	@PostMapping("/password/reset")
	String reset(@Valid @ModelAttribute("resetForm") PasswordResetForm form, BindingResult errors, Model model,
			RedirectAttributes redirect) {
		if (!this.service.valid(form.getToken()))
			return invalid(model);
		if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword()))
			errors.rejectValue("confirmPassword", "password.invalid");
		if (errors.hasErrors())
			return "account/reset";
		if (!this.service.reset(form.getToken(), form.getPassword()))
			return invalid(model);
		redirect.addFlashAttribute("messageKey", "password.resetDone");
		return "redirect:/login";
	}

	private String invalid(Model model) {
		model.addAttribute("result", UserEmailService.VerifyResult.INVALID);
		return "account/verifyResult";
	}

}
