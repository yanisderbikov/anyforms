package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.ReceiptSendRequest;
import ru.anyforms.dto.payment.ReceiptTaskDTO;
import ru.anyforms.dto.payment.ReceiptTransactionDTO;

import java.util.List;

public interface ReceiptService {

    void sendReceipt(ReceiptSendRequest request);

    List<ReceiptTaskDTO> recentTasks(int limit);

    List<ReceiptTransactionDTO> paidTransactions(int limit);
}
