package org.springframework.samples.petclinic.account;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "account_tokens")
public class AccountToken extends BaseEntity {

	private Integer userId;

	@Enumerated(EnumType.STRING)
	private TokenPurpose purpose;

	@Column(unique = true)
	private String tokenHash;

	private String email;

	private LocalDateTime expiresAt;

	private LocalDateTime usedAt;

	private LocalDateTime createdAt;

	public Integer getUserId() {
		return this.userId;
	}

	public void setUserId(Integer value) {
		this.userId = value;
	}

	public TokenPurpose getPurpose() {
		return this.purpose;
	}

	public void setPurpose(TokenPurpose value) {
		this.purpose = value;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

	public void setTokenHash(String value) {
		this.tokenHash = value;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String value) {
		this.email = value;
	}

	public LocalDateTime getExpiresAt() {
		return this.expiresAt;
	}

	public void setExpiresAt(LocalDateTime value) {
		this.expiresAt = value;
	}

	public LocalDateTime getUsedAt() {
		return this.usedAt;
	}

	public void setUsedAt(LocalDateTime value) {
		this.usedAt = value;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(LocalDateTime value) {
		this.createdAt = value;
	}

}
