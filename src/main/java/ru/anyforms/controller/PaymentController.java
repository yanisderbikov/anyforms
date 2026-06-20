package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.PaymentProductDTO;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PurchaseRequest;
import ru.anyforms.dto.payment.YooKassaWebhookBody;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PurchaseService;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Оплата продуктов через Юкассу")
public class PaymentController {

    private final PurchaseService purchaseService;
    private final PaymentConfirmService paymentConfirmService;
    private final GetterPaymentProduct getterPaymentProduct;

    @Operation(summary = "Доступные продукты для покупки")
    @GetMapping("/products")
    public ResponseEntity<List<PaymentProductDTO>> products() {
        List<PaymentProductDTO> products = getterPaymentProduct.getAllActive().stream()
                .map(PaymentProductDTO::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Купить продукт", description = "Создаёт платёж в Юкассе и возвращает ссылку на оплату")
    @PostMapping("/purchase")
    public ResponseEntity<PaymentUrlResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        // TODO: запрос расширен контактами покупателя (fullName, phone, email, marketingConsent).
        //  Прокинуть phone/fullName в сохранение транзакции и/или в CRM (amoCRM),
        //  завести рассылочный контакт при marketingConsent == true.
        return ResponseEntity.ok(purchaseService.purchase(request));
    }

    @Operation(summary = "Вебхук Юкассы", description = "Принимает уведомления об изменении статуса платежа")
    @PostMapping("/yookassa-webhook")
    public ResponseEntity<String> yooKassaWebhook(@RequestBody YooKassaWebhookBody request) {
        boolean success = paymentConfirmService.confirm(request);
        return success
                ? new ResponseEntity<>("success", HttpStatus.OK)
                : new ResponseEntity<>("fail", HttpStatus.BAD_REQUEST);
    }
}
