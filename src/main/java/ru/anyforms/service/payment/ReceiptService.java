package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.ReceiptSendRequest;
import ru.anyforms.dto.payment.ReceiptTaskDTO;
import ru.anyforms.dto.payment.ReceiptTransactionDTO;

import java.time.LocalDate;
import java.util.List;

public interface ReceiptService {

    void sendReceipt(ReceiptSendRequest request);

    List<ReceiptTaskDTO> recentTasks(int limit);

    /**
     * Оплаченные покупки обучения.
     *
     * @param receiptSent null — все, true — только те, кому чек уже отправляли, false — только те, кому ещё нет
     * @param from        нижняя граница по дате оплаты (включительно, МСК), может быть null
     * @param to          верхняя граница по дате оплаты (включительно, МСК), может быть null
     */
    List<ReceiptTransactionDTO> paidTransactions(int limit, Boolean receiptSent, LocalDate from, LocalDate to);
}
