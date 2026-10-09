package ru.anyforms.service.email.impl;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.anyforms.service.email.EmailService;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;

@Slf4j
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "postbox")
class PostboxEmailService implements EmailService {

    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final SesV2Client client;
    private final String emailAddress;
    private final String fromName;
    private final Session session;

    PostboxEmailService(SesV2Client client,
                        @Value("${email.postbox.email.address}") String emailAddress,
                        @Value("${email.postbox.from.name}") String fromName) {
        this.client = client;
        this.emailAddress = emailAddress;
        this.fromName = fromName;
        Properties properties = new Properties();
        properties.setProperty("mail.from", emailAddress);
        this.session = Session.getInstance(properties);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        send(to, subject, body, fromName, null);
    }

    @Override
    public void sendEmail(String to, String subject, String body, String overrideFromName) {
        String from = overrideFromName == null || overrideFromName.isBlank() ? fromName : overrideFromName;
        send(to, subject, body, from, null);
    }

    @Override
    public void sendEmailWithReplyTo(String to, String subject, String body, @NonNull String replyTo) {
        send(to, subject, body, fromName, replyTo);
    }

    private void send(String to, String subject, String html, String senderName, String replyTo) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(emailAddress)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .raw(RawMessage.builder()
                                .data(SdkBytes.fromByteArray(buildMime(to, subject, html, senderName, replyTo)))
                                .build())
                        .build())
                .build();

        SendEmailResponse response;
        try {
            response = client.sendEmail(request);
        } catch (SdkException e) {
            log.error("Postbox отклонил письмо: {}", e.getMessage());
            throw new RuntimeException("Postbox не принял письмо: " + e.getMessage(), e);
        }
        log.info("Postbox принял письмо: messageId {}", response.messageId());
    }

    private byte[] buildMime(String to, String subject, String html, String senderName, String replyTo) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(emailAddress, senderName, CHARSET));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to, true));
            if (replyTo != null) {
                message.setReplyTo(new InternetAddress[]{new InternetAddress(replyTo, true)});
            }
            message.setSubject(subject, CHARSET);
            message.setSentDate(new Date());
            message.setText(html, CHARSET, "html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toByteArray();
        } catch (MessagingException | IOException e) {
            throw new RuntimeException("Не получилось собрать письмо: " + e.getMessage(), e);
        }
    }
}
