package ru.anyforms.service.payment.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.anyforms.dto.payment.Amount;
import ru.anyforms.dto.payment.CartItemDTO;
import ru.anyforms.dto.payment.CartPurchaseRequest;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;
import ru.anyforms.dto.payment.yookassa.PaymentConfirmation;
import ru.anyforms.dto.payment.yookassa.PaymentCustomer;
import ru.anyforms.dto.payment.yookassa.PaymentItem;
import ru.anyforms.dto.payment.yookassa.PaymentReceipt;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionItem;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.CartPurchaseService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.YooKassaService;
import ru.anyforms.util.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class CartPurchaseServiceImpl implements CartPurchaseService {

    private static final String PAYMENT_MODE = "full_payment";
    private static final String PAYMENT_SUBJECT = "commodity"; // товар (в отличие от service у курса/гайда)
    private static final String CONFIRMATION_REDIRECT = "redirect";
    private static final String DEFAULT_FULL_NAME = "Клиент не представился";
    private static final String DEFAULT_SUCCESS_PATH = "/shop/success";

    private final YooKassaService yooKassaService;
    private final SaverTransaction saverTransaction;
    private final GetterProduct getterProduct;
    private final PaymentStatusConverter paymentStatusConverter;
    private final HttpServletRequest httpRequest;

    @Value("${payment.default-domain}")
    private String defaultDomain;

    @Value("${payment.allowed-return-hosts}")
    private String allowedReturnHosts;

    @Value("${payment.marketplace.vat-code:1}")
    private Integer marketplaceVatCode;

    @Override
    public PaymentUrlResponse purchase(CartPurchaseRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Корзина пуста");
        }

        long totalKopecks = 0;
        long totalQty = 0;
        List<PaymentItem> receiptItems = new ArrayList<>();
        List<PaymentTransactionItem> snapshotItems = new ArrayList<>();

        for (CartItemDTO cartItem : request.getItems()) {
            Product product = getterProduct.getById(cartItem.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Товар не найден: " + cartItem.getProductId()));
            int quantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
            if (quantity < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Некорректное количество для товара " + product.getName());
            }

            long unitKopecks = parsePriceToKopecks(product.getPrice(), product.getName());
            totalKopecks += unitKopecks * quantity;
            totalQty += quantity;

            String unitValue = MoneyUtil.kopecksToString(unitKopecks);
            receiptItems.add(new PaymentItem(
                    product.getName(),
                    Amount.builder().value(unitValue).currency(Currency.RUB.getCode()).build(),
                    marketplaceVatCode,
                    quantity));

            snapshotItems.add(PaymentTransactionItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .priceKopecks(unitKopecks)
                    .quantity(quantity)
                    .build());
        }

        Amount amount = Amount.builder()
                .value(MoneyUtil.kopecksToString(totalKopecks))
                .currency(Currency.RUB.getCode())
                .build();

        String fullName = (request.getFullName() == null || request.getFullName().isBlank())
                ? DEFAULT_FULL_NAME
                : request.getFullName().trim();

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(amount)
                .description("Заказ anyforms: " + totalQty + " " + pluralItems(totalQty))
                .capture(true)
                .confirmation(new PaymentConfirmation(CONFIRMATION_REDIRECT, buildReturnUrl(request.getReturnUrl())))
                .receipt(new PaymentReceipt(new PaymentCustomer(fullName, request.getEmail()), receiptItems))
                .paymentMode(PAYMENT_MODE)
                .paymentSubject(PAYMENT_SUBJECT)
                .build();

        YooKassaPaymentResponse response = yooKassaService.createPayment(paymentRequest);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .externalPaymentId(response.getId())
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .amount(MoneyUtil.stringToKopecks(response.getAmount().getValue()))
                .currency(Currency.fromCode(response.getAmount().getCurrency()))
                .description(response.getDescription())
                .email(request.getEmail())
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .status(resolveStatus(response.getStatus()))
                .customerName(fullName)
                .customerPhone(request.getPhone())
                .pvzCity(request.getPvzCity())
                .pvzStreet(request.getPvzStreet())
                .pvzCode(request.getPvzCode())
                .build();
        snapshotItems.forEach(transaction::addItem);
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(
                response.getId(),
                response.getConfirmation().getConfirmationUrl(),
                response.getAmount());
    }

    /** Цена товара — строка рублей ("890", "1 190", "1190,50"). Приводим к копейкам. */
    private long parsePriceToKopecks(String price, String productName) {
        if (price == null || price.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У товара не указана цена: " + productName);
        }
        String normalized = price.replaceAll("[\\s\\u00A0]", "").replace(',', '.');
        try {
            return new BigDecimal(normalized)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некорректная цена товара " + productName + ": " + price);
        }
    }

    private PaymentTransactionStatus resolveStatus(String yooKassaStatus) {
        PaymentTransactionStatus status = paymentStatusConverter.fromYooKassa(yooKassaStatus);
        return status != null ? status : PaymentTransactionStatus.PENDING;
    }

    private String buildReturnUrl(String requestedReturnUrl) {
        String origin = extractAllowedOrigin(requestedReturnUrl);
        if (origin == null) {
            origin = extractAllowedOrigin(httpRequest.getHeader("Origin"));
        }
        if (origin == null) {
            return joinUrl(defaultDomain, DEFAULT_SUCCESS_PATH);
        }
        // Если клиент прислал полный URL с разрешённого хоста — используем его как есть.
        if (requestedReturnUrl != null && !requestedReturnUrl.isBlank()) {
            return requestedReturnUrl.trim();
        }
        return joinUrl(origin, DEFAULT_SUCCESS_PATH);
    }

    private String joinUrl(String domain, String path) {
        if (domain.endsWith("/") && path.startsWith("/")) {
            return domain + path.substring(1);
        }
        if (!domain.endsWith("/") && !path.startsWith("/")) {
            return domain + "/" + path;
        }
        return domain + path;
    }

    private String extractAllowedOrigin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            for (String allowed : allowedReturnHosts.split(",")) {
                if (host.equalsIgnoreCase(allowed.trim())) {
                    String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
                    String port = uri.getPort() != -1 ? ":" + uri.getPort() : "";
                    return scheme + "://" + host + port;
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("Некорректный URL для домена '{}'", url);
        }
        return null;
    }

    private String pluralItems(long n) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "товар";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "товара";
        }
        return "товаров";
    }
}
