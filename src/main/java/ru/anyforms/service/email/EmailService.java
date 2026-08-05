package ru.anyforms.service.email;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
    void sendEmail(String to, String subject, String body, String fromName);
    void sendEmailWithReplyTo(String to, String subject, String body, String replyTo);
}
