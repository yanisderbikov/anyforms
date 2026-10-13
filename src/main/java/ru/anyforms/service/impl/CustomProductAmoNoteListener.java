package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.anyforms.event.CustomProductAmoNoteEvent;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.util.retry.Retrier;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Дублирует изменения кастомных позиций примечанием в сделку amoCRM: после коммита, асинхронно,
 * с повторами. Не получилось — только пишем в лог, у нас всё уже сохранено.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class CustomProductAmoNoteListener {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm").withZone(MSK);

    private final AmoCrmGateway amoCrmGateway;
    private final Retrier retrier;

    @Async
    @TransactionalEventListener
    public void onNote(CustomProductAmoNoteEvent event) {
        String note = noteText(event);
        boolean sent = retrier.attempt("Примечание в сделку " + event.leadId(),
                () -> amoCrmGateway.addNoteToLead(event.leadId(), note));
        if (!sent) {
            log.error("Примечание не попало в сделку {}: {}", event.leadId(), event.text());
        }
    }

    static String noteText(CustomProductAmoNoteEvent event) {
        return "Время МСК " + TIME.format(event.at()) + " — " + event.text();
    }
}
