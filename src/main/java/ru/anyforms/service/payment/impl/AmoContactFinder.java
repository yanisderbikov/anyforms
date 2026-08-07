package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class AmoContactFinder {

    private final AmoCrmGateway amoCrmGateway;

    Long findByEmailOrPhone(String email, String phone) {
        Long byEmail = search(email);
        if (byEmail != null) {
            return byEmail;
        }
        return search(phone == null ? null : phone.replaceAll("\\D", ""));
    }

    void fillContactFio(Long leadId, String fio) {
        if (fio == null || fio.isBlank()) {
            return;
        }
        Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
        if (contactId == null) {
            log.warn("У сделки {} нет контакта — ФИО не заполнено", leadId);
            return;
        }
        amoCrmGateway.updateContactCustomField(contactId,
                Map.of(AmoCrmFieldId.FIO_CONTACT.getId(), fio));
    }

    private Long search(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return amoCrmGateway.findContactIdByQuery(query);
    }
}
