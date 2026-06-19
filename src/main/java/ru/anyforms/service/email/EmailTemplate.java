package ru.anyforms.service.email;

/** Загрузка HTML-шаблонов писем из {@code resources/templates} и подстановка плейсхолдеров. */
public final class EmailTemplate {

    private EmailTemplate() {
    }

    /** Письмо с доступом к гайду. {@code guideLink} — ссылка на скачивание/просмотр. */
    public static String getGuideEmail(String guideLink) {
        return load("templates/email-guide.html")
                .replace("%LINK%", guideLink);
    }

    private static String load(String templatePath) {
        try (var stream = EmailTemplate.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (stream == null) {
                throw new IllegalStateException("Шаблон письма не найден: " + templatePath);
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Не получилось использовать шаблон письма: " + templatePath, e);
        }
    }
}
