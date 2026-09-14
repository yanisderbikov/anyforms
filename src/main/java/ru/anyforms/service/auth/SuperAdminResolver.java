package ru.anyforms.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SuperAdminResolver {

    private final String superAdminEmail;

    public SuperAdminResolver(@Value("${admin.super.email}") String superAdminEmail) {
        if (superAdminEmail == null || superAdminEmail.isBlank()) {
            throw new IllegalStateException("ADMIN_SUPER_EMAIL не задан: без супер-админа некому выдавать доступы");
        }
        this.superAdminEmail = normalize(superAdminEmail);
    }

    public String email() {
        return superAdminEmail;
    }

    public boolean isSuperAdmin(String email) {
        return email != null && superAdminEmail.equals(normalize(email));
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
