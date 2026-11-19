package org.springframework.samples.petclinic.mail;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
public class MailComposer {

	private final TemplateEngine html;

	private final TemplateEngine text;

	private final MessageSource messages;

	private final MailProperties properties;

	public MailComposer(TemplateEngine html, MessageSource messages, MailProperties properties) {
		this.html = html;
		this.messages = messages;
		this.properties = properties;
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/mail-text/");
		resolver.setSuffix(".txt");
		resolver.setTemplateMode(TemplateMode.TEXT);
		resolver.setCharacterEncoding("UTF-8");
		SpringTemplateEngine textEngine = new SpringTemplateEngine();
		textEngine.setTemplateResolver(resolver);
		textEngine.setTemplateEngineMessageSource(messages);
		this.text = textEngine;
	}

	public ComposedMail compose(MailKind kind, Map<String, Object> vars) {
		Context context = new Context(Locale.KOREAN);
		context.setVariables(vars);
		context.setVariable("baseUrl", this.properties.baseUrl());
		String name = kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
		Object[] subjectArgs = (kind == MailKind.RESERVATION_REMINDER && vars.containsKey("date")
				&& vars.containsKey("time")) ? new Object[] { vars.get("date"), vars.get("time") } : null;
		return new ComposedMail(this.messages.getMessage("mail.subject." + kind.name(), subjectArgs, Locale.KOREAN),
				this.text.process(name, context), this.html.process("mail/" + name, context));
	}

}
