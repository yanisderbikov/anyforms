package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.anyforms.event.ProductionLogAddedEvent;
import ru.anyforms.integration.AmoCrmGateway;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Дублирует запись лога производства примечанием в сделку amoCRM: после коммита, асинхронно,
 * до {@link #MAX_ATTEMPTS} попыток. Не получилось — только пишем в лог, запись у нас уже сохранена.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class ProductionLogAmoNoteListener {

    static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_PAUSE_MS = 2_000;
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm").withZone(MSK);

    private final AmoCrmGateway amoCrmGateway;

    @Async
    @TransactionalEventListener
    public void onLogAdded(ProductionLogAddedEvent event) {
        String note = noteText(event);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (amoCrmGateway.addNoteToLead(event.leadId(), note)) {
                    return;
                }
            } catch (Exception e) {
                log.warn("Попытка {} добавить лог производства в сделку {}: {}", attempt, event.leadId(), e.getMessage());
            }
            if (attempt < MAX_ATTEMPTS && !pause()) {
                break;
            }
        }
        log.error("Лог производства не попал в сделку {} после {} попыток", event.leadId(), MAX_ATTEMPTS);
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static String noteText(ProductionLogAddedEvent event) {
        return "Время МСК " + TIME.format(event.createdAt()) + " — комментарий производства:\n\n" + event.text();
    }
}
