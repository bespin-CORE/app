package org.springframework.samples.petclinic.account;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_emails")
public class UserEmail {

	@Id
	private Integer userId;

	private String pendingEmail;

	@Column(unique = true)
	private String verifiedEmail;

	private LocalDateTime verifiedAt;

	private LocalDateTime updatedAt;

	public Integer getUserId() {
		return this.userId;
	}

	public void setUserId(Integer value) {
		this.userId = value;
	}

	public String getPendingEmail() {
		return this.pendingEmail;
	}

	public void setPendingEmail(String value) {
		this.pendingEmail = value;
	}

	public String getVerifiedEmail() {
		return this.verifiedEmail;
	}

	public void setVerifiedEmail(String value) {
		this.verifiedEmail = value;
	}

	public LocalDateTime getVerifiedAt() {
		return this.verifiedAt;
	}

	public void setVerifiedAt(LocalDateTime value) {
		this.verifiedAt = value;
	}

	public LocalDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(LocalDateTime value) {
		this.updatedAt = value;
	}

}
