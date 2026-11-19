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

	public static boolean isValid(String email) {
		if (email == null || email.length() > 254 || email.isEmpty())
			return false;
		for (int i = 0; i < email.length(); i++)
			if (Character.isWhitespace(email.charAt(i)) || Character.isISOControl(email.charAt(i)))
				return false;
		int at = email.indexOf('@');
		return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
				&& email.substring(at + 1).contains(".") && !email.endsWith(".");
	}

	public static String mask(String email) {
		int at = email.indexOf('@');
		if (at < 1)
			return "***";
		return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
	}

}
