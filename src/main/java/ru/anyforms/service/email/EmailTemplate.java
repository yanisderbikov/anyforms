package ru.anyforms.service.email;

public final class EmailTemplate {

    private EmailTemplate() {
    }

    public static String getGuideEmail(String link) {
        return load("templates/email-guide.html").replace("%LINK%", link);
    }

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
