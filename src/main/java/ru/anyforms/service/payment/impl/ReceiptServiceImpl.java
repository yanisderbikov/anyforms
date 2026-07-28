package ru.anyforms.service.payment.impl;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.email.ReceiptEmailTaskPayload;
import ru.anyforms.dto.payment.ReceiptSendRequest;
import ru.anyforms.dto.payment.ReceiptTaskDTO;
import ru.anyforms.dto.payment.ReceiptTransactionDTO;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTask;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.ReceiptService;
import ru.anyforms.service.task.TaskAdder;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class ReceiptServiceImpl implements ReceiptService {

    private static final List<String> TRAINING_PRODUCT_CODES = List.of(
            PaymentProduct.CODE_GUIDE,
            PaymentProduct.CODE_COURSE,
            PaymentProduct.CODE_COURSE_PERSONAL);

    private final GetterTransaction getterTransaction;
    private final GetterTask getterTask;
    private final TaskAdder taskAdder;
    private final Gson gson = new Gson();

    @Override
    public void sendReceipt(ReceiptSendRequest request) {
        String link = request.getLink().trim();
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ссылка на чек должна начинаться с http(s)://");
        }
        taskAdder.addTask(ReceiptEmailTaskPayload.builder()
                .to(request.getEmail().trim())
                .link(link)
                .build());
        log.info("Чек поставлен в очередь на отправку: {}", request.getEmail());
    }

    @Override
    public List<ReceiptTaskDTO> recentTasks(int limit) {
        return getterTask.getRecentByType(TaskType.RECEIPT_EMAIL, limit).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<ReceiptTransactionDTO> paidTransactions(int limit) {
        return getterTransaction.getRecentByProviderStatusAndProductCodes(
                        PaymentProvider.YOOKASSA, PaymentTransactionStatus.SUCCEEDED, TRAINING_PRODUCT_CODES, limit)
                .stream()
                .map(ReceiptTransactionDTO::from)
                .toList();
    }

    private ReceiptTaskDTO toDto(Task task) {
        ReceiptEmailTaskPayload payload = parsePayload(task.getPayload());
        return new ReceiptTaskDTO(
                payload != null ? payload.getTo() : null,
                payload != null ? payload.getLink() : null,
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getComment(),
                task.getCreatedAt());
    }

    private ReceiptEmailTaskPayload parsePayload(String json) {
        try {
            return gson.fromJson(json, ReceiptEmailTaskPayload.class);
        } catch (Exception e) {
            log.error("Не удалось распарсить payload таски чека", e);
            return null;
        }
    }
}
