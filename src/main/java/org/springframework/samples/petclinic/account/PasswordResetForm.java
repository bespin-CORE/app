package org.springframework.samples.petclinic.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetForm {

	private String token;

	@NotBlank
	@Size(min = 8, max = 64, message = "{password.invalid}")
	private String password;

	@NotBlank
	private String confirmPassword;

	public String getToken() {
		return this.token;
	}

	public void setToken(String value) {
		this.token = value;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String value) {
		this.password = value;
	}

	public String getConfirmPassword() {
		return this.confirmPassword;
	}

	public void setConfirmPassword(String value) {
		this.confirmPassword = value;
	}

}
