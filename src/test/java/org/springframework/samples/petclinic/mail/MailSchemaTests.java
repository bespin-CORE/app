package org.springframework.samples.petclinic.mail;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.account.AccountTokenRepository;
import org.springframework.samples.petclinic.account.AccountToken;
import org.springframework.samples.petclinic.account.TokenPurpose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.samples.petclinic.account.UserEmailRepository;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "vetcore.scheduling.enabled=false", "vetcore.mail.enabled=true",
		"spring.mail.username=test@example.com", "spring.mail.password=x" })
@Import(MailTestConfig.class)
class MailSchemaTests {

	@Autowired
	MailOutboxRepository outbox;

	@Autowired
	UserEmailRepository emails;

	@Autowired
	AccountTokenRepository tokens;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void claimIsExclusive() {
		MailOutbox row = new MailOutbox();
		row.setKind(MailKind.VERIFY_EMAIL);
		row.setRecipient("test@example.com");
		row.setSubject("test");
		row.setStatus(MailStatus.PENDING);
		LocalDateTime now = LocalDateTime.now().withNano(0);
		row.setCreatedAt(now);
		row.setNextAttemptAt(now);
		row = outbox.saveAndFlush(row);
		assertThat(outbox.claim(row.getId(), now, now.plusMinutes(2))).isEqualTo(1);
		assertThat(outbox.claim(row.getId(), now, now.plusMinutes(2))).isZero();
	}

	private int user(String name) {
		jdbc.update(
				"insert into users(username,password,display_name,role,created_at) values (?,?,?, ?,CURRENT_TIMESTAMP)",
				name, "encoded", name, "USER");
		return jdbc.queryForObject("select id from users where username=?", Integer.class, name);
	}

	@Test
	void verifiedEmailIsUnique() {
		int a = user("schema_user_a"), b = user("schema_user_b"), c = user("schema_user_c"), d = user("schema_user_d");
		jdbc.update("insert into user_emails(user_id,verified_email) values (?,?)", a, "unique@example.com");
		assertThatThrownBy(() -> jdbc.update("insert into user_emails(user_id,verified_email) values (?,?)", b,
				"unique@example.com"))
			.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("insert into user_emails(user_id) values (?)", c);
		jdbc.update("insert into user_emails(user_id) values (?)", d);
	}

	@Test
	void dedupeKeyIsUnique() {
		String sql = "insert into mail_outbox(kind,recipient,subject,status,attempts,created_at,dedupe_key) values ('VERIFY_EMAIL','a@example.com','test','PENDING',0,CURRENT_TIMESTAMP,?)";
		jdbc.update(sql, "schema-key");
		assertThatThrownBy(() -> jdbc.update(sql, "schema-key")).isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update(sql, (Object) null);
		jdbc.update(sql, (Object) null);
	}

	@Test
	void tokenHashIsUnique() {
		int id = user("schema_token_user");
		String sql = "insert into account_tokens(user_id,purpose,token_hash,expires_at,created_at) values (?, 'VERIFY_EMAIL', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
		jdbc.update(sql, id, "hash");
		assertThatThrownBy(() -> jdbc.update(sql, id, "hash")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void markUsedOnlyOnce() {
		int id = user("schema_mark_user");
		LocalDateTime now = LocalDateTime.now().withNano(0);
		AccountToken token = new AccountToken();
		token.setUserId(id);
		token.setPurpose(TokenPurpose.VERIFY_EMAIL);
		token.setTokenHash("mark-hash");
		token.setCreatedAt(now);
		token.setExpiresAt(now.plusMinutes(1));
		token = tokens.saveAndFlush(token);
		assertThat(tokens.markUsed(token.getId(), now)).isEqualTo(1);
		assertThat(tokens.markUsed(token.getId(), now)).isZero();
		AccountToken expired = new AccountToken();
		expired.setUserId(id);
		expired.setPurpose(TokenPurpose.VERIFY_EMAIL);
		expired.setTokenHash("expired-hash");
		expired.setCreatedAt(now);
		expired.setExpiresAt(now.minusSeconds(1));
		expired = tokens.saveAndFlush(expired);
		assertThat(tokens.markUsed(expired.getId(), now)).isZero();
	}

}
