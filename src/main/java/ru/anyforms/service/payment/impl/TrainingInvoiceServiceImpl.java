package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PurchaseRequest;
import ru.anyforms.dto.payment.TrainingInvoiceCreateRequest;
import ru.anyforms.dto.payment.TrainingInvoiceDTO;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PurchaseService;
import ru.anyforms.service.payment.TrainingInvoiceService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class TrainingInvoiceServiceImpl implements TrainingInvoiceService {

    private static final List<String> TRAINING_PRODUCT_CODES = List.of(
            PaymentProduct.CODE_GUIDE,
            PaymentProduct.CODE_COURSE,
            PaymentProduct.CODE_COURSE_PERSONAL);

    private final PurchaseService purchaseService;
    private final GetterTransaction getterTransaction;

    @Override
    public TrainingInvoiceDTO create(TrainingInvoiceCreateRequest request) {
        if (!TRAINING_PRODUCT_CODES.contains(request.getProductCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Недопустимый код продукта: " + request.getProductCode());
        }

        PurchaseRequest purchaseRequest = new PurchaseRequest(
                request.getProductCode(),
                request.getEmail().trim(),
                request.getFullName().trim(),
                request.getPhone().trim(),
                null,
                null,
                request.getPromoCode());
        PaymentUrlResponse response = purchaseService.purchase(purchaseRequest);

        return getterTransaction.getByExternalPaymentId(response.getExternalPaymentId())
                .map(transaction -> {
                    log.info("Выставлен счёт на обучение {} ({}) для {}",
                            transaction.getExternalPaymentId(), transaction.getProductCode(),
                            transaction.getContactName());
                    return TrainingInvoiceDTO.from(transaction);
                })
                .orElseThrow(() -> new IllegalStateException(
                        "Транзакция не найдена: " + response.getExternalPaymentId()));
    }

    @Override
    public List<TrainingInvoiceDTO> recent(int limit) {
        return getterTransaction.getRecentByProductCodes(TRAINING_PRODUCT_CODES, limit).stream()
                .map(TrainingInvoiceDTO::from)
                .toList();
    }
}
