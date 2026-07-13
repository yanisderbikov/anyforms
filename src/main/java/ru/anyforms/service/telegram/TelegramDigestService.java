package ru.anyforms.service.telegram;

import ru.anyforms.dto.telegram.TelegramDigestDTO;

import java.util.List;

public interface TelegramDigestService {

    TelegramDigestDTO buildPendingDigest();

    int confirmSent(List<Long> orderIds);
}
