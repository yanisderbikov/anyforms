package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;

@Component
@RequiredArgsConstructor
class AmoContactFinder {

    private final AmoCrmGateway amoCrmGateway;

    Long findByEmailOrPhone(String email, String phone) {
        Long byEmail = search(email);
        if (byEmail != null) {
            return byEmail;
        }
        return search(phone == null ? null : phone.replaceAll("\\D", ""));
    }

    private Long search(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return amoCrmGateway.findContactIdByQuery(query);
    }
}
