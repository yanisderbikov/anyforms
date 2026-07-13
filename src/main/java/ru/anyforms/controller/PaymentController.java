package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.CartPurchaseRequest;
import ru.anyforms.dto.payment.PaymentProductDTO;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PromoCheckResponse;
import ru.anyforms.dto.payment.PurchaseRequest;
import ru.anyforms.dto.payment.YooKassaWebhookBody;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.service.payment.CartPurchaseService;
import ru.anyforms.service.payment.InvalidPromoCodeException;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PurchaseService;
import ru.anyforms.util.MoneyUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Оплата продуктов через Юкассу")
public class PaymentController {

    private final PurchaseService purchaseService;
    private final CartPurchaseService cartPurchaseService;
    private final PaymentConfirmService paymentConfirmService;
    private final GetterPaymentProduct getterPaymentProduct;
    private final GetterPromoCode getterPromoCode;

    @Operation(summary = "Доступные продукты для покупки")
    @GetMapping("/products")
    public ResponseEntity<List<PaymentProductDTO>> products() {
        List<PaymentProductDTO> products = getterPaymentProduct.getAllActive().stream()
                .map(PaymentProductDTO::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Проверить промокод",
            description = "Возвращает скидку и цену продукта с учётом промокода; для невалидного кода valid=false с причиной")
    @GetMapping("/promo-check")
    public ResponseEntity<PromoCheckResponse> promoCheck(
            @RequestParam("code") String code,
            @RequestParam("productCode") String productCode) {
        PaymentProduct product = getterPaymentProduct.getByCode(productCode)
                .orElseThrow(() -> new RuntimeException("Продукт не найден: " + productCode));
        Long price = product.getPriceKopecks();

        Optional<PromoCode> promo = getterPromoCode.getByCode(code);
        if (promo.isEmpty()) {
            return ResponseEntity.ok(new PromoCheckResponse(false, null, null, price, null, "Такого промокода нет.", null));
        }
        if (!promo.get().isCurrentlyValid()) {
            return ResponseEntity.ok(new PromoCheckResponse(
                    false, promo.get().getCode(), null, price, null, "Срок действия промокода истёк.", null));
        }
        int percent = promo.get().getDiscountPercent();
        String validUntil = promo.get().getValidUntil() != null ? promo.get().getValidUntil().toString() : null;
        return ResponseEntity.ok(new PromoCheckResponse(
                true, promo.get().getCode(), percent, price,
                MoneyUtil.applyDiscountPercent(price, percent), null, validUntil));
    }

    @ExceptionHandler(InvalidPromoCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPromo(InvalidPromoCodeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @Operation(summary = "Купить продукт", description = "Создаёт платёж в Юкассе и возвращает ссылку на оплату")
    @PostMapping("/purchase")
    public ResponseEntity<PaymentUrlResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(purchaseService.purchase(request));
    }

    @Operation(summary = "Оформить заказ маркетплейса",
            description = "Считает сумму корзины по серверным ценам, создаёт платёж в Юкассе и возвращает ссылку на оплату")
    @PostMapping("/cart-purchase")
    public ResponseEntity<PaymentUrlResponse> cartPurchase(@Valid @RequestBody CartPurchaseRequest request) {
        return ResponseEntity.ok(cartPurchaseService.purchase(request));
    }

    @Operation(summary = "Вебхук Юкассы", description = "Принимает уведомления об изменении статуса платежа")
    @PostMapping("/yookassa-webhook")
    public ResponseEntity<String> yooKassaWebhook(@RequestBody YooKassaWebhookBody request) {
        boolean success = paymentConfirmService.confirm(request);
        return success
                ? new ResponseEntity<>("success", HttpStatus.OK)
                : new ResponseEntity<>("fail", HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Нотификации Т-Кассы",
            description = "Принимает уведомления Тинькофф об изменении статуса платежа; при успехе отвечает OK")
    @PostMapping("/tinkoff-webhook")
    public ResponseEntity<String> tinkoffWebhook(@RequestBody String rawBody) {
        boolean success = paymentConfirmService.confirmTinkoff(rawBody);
        return success
                ? new ResponseEntity<>("OK", HttpStatus.OK)
                : new ResponseEntity<>("fail", HttpStatus.BAD_REQUEST);
    }
}
