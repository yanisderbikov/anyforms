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
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.repository.GetterTask;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.ReceiptService;
import ru.anyforms.service.task.TaskAdder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final GetterPaymentProduct getterPaymentProduct;
    private final TaskAdder taskAdder;
    private final Gson gson = new Gson();

    @Override
    public void sendReceipt(ReceiptSendRequest request) {
        String link = request.getLink().trim();
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ссылка на чек должна начинаться с http(s)://");
        }
        String productCode = request.getProductCode() == null || request.getProductCode().isBlank()
                ? null
                : request.getProductCode().trim();
        taskAdder.addTask(ReceiptEmailTaskPayload.builder()
                .to(request.getEmail().trim())
                .link(link)
                .productCode(productCode)
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
        Map<String, String> titlesByCode = productTitlesByCode();
        Set<String> sentEmailProductPairs = new HashSet<>();
        Set<String> sentLegacyEmails = new HashSet<>();
        collectSentReceipts(sentEmailProductPairs, sentLegacyEmails);
        return getterTransaction.getRecentByProviderStatusAndProductCodes(
                        PaymentProvider.YOOKASSA, PaymentTransactionStatus.SUCCEEDED, TRAINING_PRODUCT_CODES, limit)
                .stream()
                .map(t -> ReceiptTransactionDTO.from(
                        t,
                        titlesByCode.get(t.getProductCode()),
                        receiptSent(sentEmailProductPairs, sentLegacyEmails, t.getEmail(), t.getProductCode())))
                .toList();
    }

    private void collectSentReceipts(Set<String> emailProductPairs, Set<String> legacyEmails) {
        for (Task task : getterTask.getAllByType(TaskType.RECEIPT_EMAIL)) {
            if (task.getStatus() == TaskStatus.FAILED) {
                continue;
            }
            ReceiptEmailTaskPayload payload = parsePayload(task.getPayload());
            if (payload == null || payload.getTo() == null || payload.getTo().isBlank()) {
                continue;
            }
            String email = payload.getTo().trim().toLowerCase();
            if (payload.getProductCode() == null || payload.getProductCode().isBlank()) {
                legacyEmails.add(email);
            } else {
                emailProductPairs.add(email + "|" + payload.getProductCode().trim());
            }
        }
    }

    private boolean receiptSent(Set<String> emailProductPairs, Set<String> legacyEmails, String email, String productCode) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        return emailProductPairs.contains(normalized + "|" + productCode) || legacyEmails.contains(normalized);
    }

    private Map<String, String> productTitlesByCode() {
        return TRAINING_PRODUCT_CODES.stream()
                .map(getterPaymentProduct::getByCode)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(PaymentProduct::getCode, PaymentProduct::getTitle));
    }

    private ReceiptTaskDTO toDto(Task task) {
        ReceiptEmailTaskPayload payload = parsePayload(task.getPayload());
        return new ReceiptTaskDTO(
                payload != null ? payload.getTo() : null,
                payload != null ? payload.getLink() : null,
                payload != null ? payload.getProductCode() : null,
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
