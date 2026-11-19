package org.springframework.samples.petclinic.mail;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "mail_outbox")
public class MailOutbox extends BaseEntity {

	@Enumerated(EnumType.STRING)
	private MailKind kind;

	private String recipient;

	private String subject;

	@Column(columnDefinition = "TEXT")
	private String bodyText;

	@Column(columnDefinition = "TEXT")
	private String bodyHtml;

	@Column(unique = true)
	private String dedupeKey;

	@Enumerated(EnumType.STRING)
	private MailStatus status;

	private int attempts;

	private LocalDateTime nextAttemptAt;

	private LocalDateTime lockedUntil;

	private String lastError;

	private LocalDateTime createdAt;

	private LocalDateTime sentAt;

	public MailKind getKind() {
		return this.kind;
	}

	public void setKind(MailKind value) {
		this.kind = value;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public void setRecipient(String value) {
		this.recipient = value;
	}

	public String getSubject() {
		return this.subject;
	}

	public void setSubject(String value) {
		this.subject = value;
	}

	public String getBodyText() {
		return this.bodyText;
	}

	public void setBodyText(String value) {
		this.bodyText = value;
	}

	public String getBodyHtml() {
		return this.bodyHtml;
	}

	public void setBodyHtml(String value) {
		this.bodyHtml = value;
	}

	public String getDedupeKey() {
		return this.dedupeKey;
	}

	public void setDedupeKey(String value) {
		this.dedupeKey = value;
	}

	public MailStatus getStatus() {
		return this.status;
	}

	public void setStatus(MailStatus value) {
		this.status = value;
	}

	public int getAttempts() {
		return this.attempts;
	}

	public void setAttempts(int value) {
		this.attempts = value;
	}

	public LocalDateTime getNextAttemptAt() {
		return this.nextAttemptAt;
	}

	public void setNextAttemptAt(LocalDateTime value) {
		this.nextAttemptAt = value;
	}

	public LocalDateTime getLockedUntil() {
		return this.lockedUntil;
	}

	public void setLockedUntil(LocalDateTime value) {
		this.lockedUntil = value;
	}

	public String getLastError() {
		return this.lastError;
	}

	public void setLastError(String value) {
		this.lastError = value;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(LocalDateTime value) {
		this.createdAt = value;
	}

	public LocalDateTime getSentAt() {
		return this.sentAt;
	}

	public void setSentAt(LocalDateTime value) {
		this.sentAt = value;
	}

}
