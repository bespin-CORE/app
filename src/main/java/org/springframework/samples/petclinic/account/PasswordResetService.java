package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.samples.petclinic.mail.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	private final UserEmailRepository emails;

	private final AppUserRepository users;

	private final AccountTokenService tokens;

	private final AccountTokenRepository tokenRows;

	private final MailComposer composer;

	private final MailOutboxService outbox;

	private final MailProperties properties;

	private final PasswordEncoder encoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final Clock clock;

	public PasswordResetService(UserEmailRepository emails, AppUserRepository users, AccountTokenService tokens,
			AccountTokenRepository tokenRows, MailComposer composer, MailOutboxService outbox,
			MailProperties properties, PasswordEncoder encoder,
			FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
		this.emails = emails;
		this.users = users;
		this.tokens = tokens;
		this.tokenRows = tokenRows;
		this.composer = composer;
		this.outbox = outbox;
		this.properties = properties;
		this.encoder = encoder;
		this.sessions = sessions;
		this.clock = clock;
	}

	@Transactional
	public void requestReset(String rawEmail) {
		if (EmailAddresses.hasControlCharacters(rawEmail))
			return;
		String email = EmailAddresses.normalize(rawEmail);
		if (!EmailAddresses.isValid(email))
			return;
		this.emails.findByVerifiedEmail(email).ifPresent(row -> {
			if (this.tokens.issuedSince(row.getUserId(), TokenPurpose.RESET_PASSWORD,
					LocalDateTime.now(this.clock).minusHours(1)) >= 3)
				return;
			AppUser user = this.users.findById(row.getUserId()).orElseThrow();
			String raw = this.tokens.issue(user, TokenPurpose.RESET_PASSWORD, email, Duration.ofMinutes(30));
			String link = this.properties.baseUrl() + "/password/reset?token=" + raw;
			this.outbox.enqueue(MailKind.RESET_PASSWORD, email,
					this.composer.compose(MailKind.RESET_PASSWORD, Map.of("link", link)));
		});
	}

	@Transactional
	public boolean reset(String raw, String password) {
		if (password == null || password.length() < 8 || password.length() > 64)
			return false;
		var token = this.tokens.consume(raw, TokenPurpose.RESET_PASSWORD);
		if (token.isEmpty())
			return false;
		AppUser user = this.users.findById(token.get().getUserId()).orElseThrow();
		user.setPassword(this.encoder.encode(password));
		this.users.save(user);
		this.tokenRows.invalidateUnused(user.getId(), TokenPurpose.RESET_PASSWORD, LocalDateTime.now(this.clock));
		this.sessions.findByPrincipalName(user.getUsername()).keySet().forEach(this.sessions::deleteById);
		return true;
	}

	public boolean valid(String raw) {
		return this.tokens.findValid(raw, TokenPurpose.RESET_PASSWORD).isPresent();
	}

}
