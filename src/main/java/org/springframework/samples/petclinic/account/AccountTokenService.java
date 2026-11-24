package org.springframework.samples.petclinic.account;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTokenService {

	private final AccountTokenRepository repository;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public AccountTokenService(AccountTokenRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public String issue(AppUser user, TokenPurpose purpose, String email, Duration ttl) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		this.repository.invalidateUnused(user.getId(), purpose, now);
		byte[] bytes = new byte[32];
		this.random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		AccountToken token = new AccountToken();
		token.setUserId(user.getId());
		token.setPurpose(purpose);
		token.setEmail(email);
		token.setTokenHash(hash(raw));
		token.setCreatedAt(now);
		token.setExpiresAt(now.plus(ttl));
		this.repository.save(token);
		return raw;
	}

	@Transactional(readOnly = true)
	public Optional<AccountToken> findValid(String raw, TokenPurpose purpose) {
		if (raw == null || raw.isBlank())
			return Optional.empty();
		LocalDateTime now = LocalDateTime.now(this.clock);
		return this.repository.findByTokenHashAndPurpose(hash(raw), purpose)
			.filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
	}

	@Transactional
	public Optional<AccountToken> consume(String raw, TokenPurpose purpose) {
		return findValid(raw, purpose)
			.filter(t -> this.repository.markUsed(t.getId(), LocalDateTime.now(this.clock)) == 1);
	}

	public long issuedSince(Integer userId, TokenPurpose purpose, LocalDateTime since) {
		return this.repository.countByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, since);
	}

	private static String hash(String raw) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
