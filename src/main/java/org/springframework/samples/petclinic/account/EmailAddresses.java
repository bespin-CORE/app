package org.springframework.samples.petclinic.account;

import java.util.Locale;

public final class EmailAddresses {

	private EmailAddresses() {
	}

	public static boolean hasControlCharacters(String email) {
		return email != null && email.chars().anyMatch(Character::isISOControl);
	}

	public static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	public static final int MAX_LENGTH = 25;

	public static boolean isValid(String email) {
		return email != null && email.length() <= MAX_LENGTH && email.matches(
				"[a-z0-9_+\\-]+(?:\\.[a-z0-9_+\\-]+)*@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}");
	}

	public static String mask(String email) {
		int at = email.indexOf('@');
		if (at < 1)
			return "***";
		return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
	}

}
