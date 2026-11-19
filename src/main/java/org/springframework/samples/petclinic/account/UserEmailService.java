package org.springframework.samples.petclinic.account;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.mail.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserEmailService {

	public enum VerifyResult {

		VERIFIED, INVALID, TAKEN

	}

	public static class VerificationRateLimitedException extends RuntimeException {

	}

	private final UserEmailRepository emails;

	private final AccountTokenService tokens;

	private final MailComposer composer;

	private final MailOutboxService outbox;

	private final MailProperties properties;

	private final Clock clock;

	private final AppUserRepository users;

	private final TransactionTemplate transactions;

	public UserEmailService(UserEmailRepository emails, AccountTokenService tokens, MailComposer composer,
			MailOutboxService outbox, MailProperties properties, Clock clock, AppUserRepository users,
			PlatformTransactionManager manager) {
		this.emails = emails;
		this.tokens = tokens;
		this.composer = composer;
		this.outbox = outbox;
		this.properties = properties;
		this.clock = clock;
		this.users = users;
		this.transactions = new TransactionTemplate(manager);
	}

	@Transactional(readOnly = true)
	public Optional<String> verifiedEmail(Integer id) {
		return this.emails.findById(id).map(UserEmail::getVerifiedEmail);
	}

	@Transactional
	public void requestVerification(AppUser user, String rawEmail) {
		String email = EmailAddresses.normalize(rawEmail);
		if (EmailAddresses.hasControlCharacters(rawEmail) || !EmailAddresses.isValid(email)
				|| !email.equals(user.getUsername()))
			throw new IllegalArgumentException("Invalid email");
		this.users.findByIdForUpdate(user.getId()).orElseThrow();
		LocalDateTime now = LocalDateTime.now(this.clock);
		if (this.tokens.issuedSince(user.getId(), TokenPurpose.VERIFY_EMAIL, now.minusHours(1)) >= 3)
			throw new VerificationRateLimitedException();
		UserEmail row = this.emails.findById(user.getId()).orElseGet(UserEmail::new);
		row.setUserId(user.getId());
		row.setPendingEmail(email);
		row.setUpdatedAt(now);
		this.emails.save(row);
		String raw = this.tokens.issue(user, TokenPurpose.VERIFY_EMAIL, email, Duration.ofHours(24));
		String link = this.properties.baseUrl() + "/verify-email?token=" + raw;
		this.outbox.enqueue(MailKind.VERIFY_EMAIL, email,
				this.composer.compose(MailKind.VERIFY_EMAIL, Map.of("link", link)));
	}

	@Transactional
	public void resendForUsername(String username) {
		this.users.findByUsername(username).ifPresent(user -> {
			this.users.findByIdForUpdate(user.getId()).orElseThrow();
			this.emails.findById(user.getId())
				.filter(e -> e.getVerifiedEmail() == null && user.getUsername().equals(e.getPendingEmail())
						&& AppUser.ROLE_USER.equals(user.getRole()))
				.ifPresent(e -> requestVerification(user, e.getPendingEmail()));
		});
	}

	public VerifyResult verify(String raw) {
		try {
			return this.transactions.execute(status -> verifyInTransaction(raw));
		}
		catch (DataIntegrityViolationException ex) {
			return VerifyResult.TAKEN;
		}
	}

	private VerifyResult verifyInTransaction(String raw) {
		Optional<AccountToken> candidate = this.tokens.findValid(raw, TokenPurpose.VERIFY_EMAIL);
		if (candidate.isEmpty())
			return VerifyResult.INVALID;
		AccountToken token = candidate.get();
		AppUser owner = this.users.findByIdForUpdate(token.getUserId()).orElseThrow();
		if (!owner.getUsername().equals(token.getEmail()))
			return VerifyResult.INVALID;
		Optional<UserEmail> found = this.emails.findById(token.getUserId());
		if (found.isEmpty() || !token.getEmail().equals(found.get().getPendingEmail()))
			return VerifyResult.INVALID;
		if (this.emails.findByVerifiedEmail(token.getEmail())
			.filter(e -> !e.getUserId().equals(token.getUserId()))
			.isPresent())
			return VerifyResult.TAKEN;
		if (this.tokens.consume(raw, TokenPurpose.VERIFY_EMAIL).isEmpty())
			return VerifyResult.INVALID;
		UserEmail email = found.get();
		email.setVerifiedEmail(token.getEmail());
		email.setVerifiedAt(LocalDateTime.now(this.clock));
		email.setPendingEmail(null);
		email.setUpdatedAt(LocalDateTime.now(this.clock));
		this.emails.saveAndFlush(email);
		return VerifyResult.VERIFIED;
	}

}
