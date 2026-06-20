package ru.anyforms.service.email;

/** Загрузка HTML-шаблонов писем из {@code resources/templates} и подстановка плейсхолдеров. */
public final class EmailTemplate {

    private EmailTemplate() {
    }

    /** Письмо с доступом к гайду. {@code link} — ссылка на скачивание/просмотр. */
    public static String getGuideEmail(String link) {
        return load("templates/email-guide.html").replace("%LINK%", link);
    }

    /** Письмо с доступом к курсу. {@code link} — ссылка на курс. */
    public static String getCourseEmail(String link) {
        return load("templates/email-course.html").replace("%LINK%", link);
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
