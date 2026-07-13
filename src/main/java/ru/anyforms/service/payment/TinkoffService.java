package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;

public interface TinkoffService {
    TinkoffInitResponse init(TinkoffInitRequest request);
}
