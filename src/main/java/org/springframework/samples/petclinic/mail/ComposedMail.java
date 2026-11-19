package org.springframework.samples.petclinic.mail;

public record ComposedMail(String subject, String text, String html) {
}
