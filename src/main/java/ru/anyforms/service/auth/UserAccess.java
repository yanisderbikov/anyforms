package ru.anyforms.service.auth;

import ru.anyforms.model.Role;

public record UserAccess(String email, String name, Role role, boolean superAdmin, String shopSlug, String shopName) {
}
