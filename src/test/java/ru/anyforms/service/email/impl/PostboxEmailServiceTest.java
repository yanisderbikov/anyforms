package ru.anyforms.service.email.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostboxEmailServiceTest {

    private static final String FROM = "noreply@anyforms.ru";
    private static final String TO = "client@example.com";

    private final SesV2Client client = mock(SesV2Client.class);
    private PostboxEmailService service;

    @BeforeEach
    void setUp() {
        service = new PostboxEmailService(client, FROM, "Эниформс");
        when(client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());
    }

    private SendEmailRequest sentRequest() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        return captor.getValue();
    }

    private MimeMessage sentMessage() throws Exception {
        byte[] raw = sentRequest().content().raw().data().asByteArray();
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    @Test
    void sendsHtmlLetterWithCyrillicSubjectAndSender() throws Exception {
        String html = "<p>Здравствуйте! Заказ <b>#AB12</b> отправлен</p>";

        service.sendEmail(TO, "Заказ #AB12 передан в СДЭК", html);

        SendEmailRequest request = sentRequest();
        assertEquals(FROM, request.fromEmailAddress());
        assertEquals(List.of(TO), request.destination().toAddresses());

        MimeMessage message = sentMessage();
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals(FROM, from.getAddress());
        assertEquals("Эниформс", from.getPersonal());
        assertEquals(TO, ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertEquals("Заказ #AB12 передан в СДЭК", message.getSubject());
        assertTrue(message.getContentType().toLowerCase().startsWith("text/html"));
        assertTrue(message.getContentType().toLowerCase().contains("charset=utf-8"));
        assertEquals(html, message.getContent());
        assertNull(message.getHeader("Reply-To"));
        assertNotNull(message.getSentDate());
    }

    @Test
    void overridesSenderNameForShopLetters() throws Exception {
        service.sendEmail(TO, "Ваш заказ оформлен", "<p>ok</p>", "Луна Свеча");

        assertEquals("Луна Свеча", ((InternetAddress) sentMessage().getFrom()[0]).getPersonal());
    }

    @Test
    void blankSenderNameFallsBackToDefault() throws Exception {
        service.sendEmail(TO, "Ваш заказ оформлен", "<p>ok</p>", "  ");

        assertEquals("Эниформс", ((InternetAddress) sentMessage().getFrom()[0]).getPersonal());
    }

    @Test
    void setsReplyToHeader() throws Exception {
        service.sendEmailWithReplyTo(TO, "Вопрос", "<p>ok</p>", "support@anyforms.ru");

        MimeMessage message = sentMessage();
        assertEquals("support@anyforms.ru", ((InternetAddress) message.getReplyTo()[0]).getAddress());
    }

    @Test
    void keepsRawLinesShortForLongHtml() {
        String html = "<p>" + "Длинная строка письма без переносов. ".repeat(200) + "</p>";

        service.sendEmail(TO, "Тема", html);

        String raw = new String(sentRequest().content().raw().data().asByteArray(), StandardCharsets.US_ASCII);
        assertTrue(raw.lines().allMatch(line -> line.length() <= 998));
    }

    @Test
    void wrapsPostboxFailure() {
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.create("connection refused"));

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.sendEmail(TO, "Тема", "<p>ok</p>"));
        assertTrue(e.getMessage().contains("connection refused"));
    }

    @Test
    void rejectsInvalidRecipientBeforeCallingPostbox() {
        assertThrows(RuntimeException.class, () -> service.sendEmail("not an email", "Тема", "<p>ok</p>"));
        verify(client, never()).sendEmail(any(SendEmailRequest.class));
    }
}
