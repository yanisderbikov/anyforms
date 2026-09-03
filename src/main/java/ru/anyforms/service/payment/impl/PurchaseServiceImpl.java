package ru.anyforms.service.payment.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.Amount;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PurchaseRequest;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;
import ru.anyforms.dto.payment.yookassa.PaymentConfirmation;
import ru.anyforms.dto.payment.yookassa.PaymentCustomer;
import ru.anyforms.dto.payment.yookassa.PaymentItem;
import ru.anyforms.dto.payment.yookassa.PaymentReceipt;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.InvalidPromoCodeException;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.PurchaseService;
import ru.anyforms.service.payment.TinkoffService;
import ru.anyforms.service.payment.YooKassaService;
import ru.anyforms.util.MoneyUtil;
import ru.anyforms.util.PhoneUtil;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static ru.anyforms.service.payment.impl.TinkoffPaymentSupport.appendParam;
import static ru.anyforms.service.payment.impl.TinkoffPaymentSupport.blankToNull;

/**
 * Покупка обучения (гайд, курс, личное сопровождение). Провайдер выбирается настройкой
 * {@code payment.training.provider}: {@code tinkoff} — Т-Касса, иначе ЮKassa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class PurchaseServiceImpl implements PurchaseService {

    private static final String PAYMENT_MODE = "full_payment";
    private static final String PAYMENT_SUBJECT = "service";
    private static final String CONFIRMATION_REDIRECT = "redirect";
    private static final String DEFAULT_FULL_NAME = "Клиент не представился";

    private final YooKassaService yooKassaService;
    private final TinkoffService tinkoffService;
    private final TinkoffPaymentSupport tinkoffSupport;
    private final SaverTransaction saverTransaction;
    private final GetterPaymentProduct getterPaymentProduct;
    private final GetterPromoCode getterPromoCode;
    private final PaymentStatusConverter paymentStatusConverter;
    private final HttpServletRequest httpRequest;

    @Value("${payment.default-domain}")
    private String defaultDomain;

    @Value("${payment.allowed-return-hosts}")
    private String allowedReturnHosts;

    @Value("${payment.yookassa.vat-code}")
    private Integer yookassaVatCode;

    @Value("${payment.training.provider}")
    private String trainingProvider;

    @Override
    public PaymentUrlResponse purchase(PurchaseRequest request) {
        PaymentProduct product = getterPaymentProduct.getByCode(request.getProductCode())
                .orElseThrow(() -> new RuntimeException("Продукт не найден: " + request.getProductCode()));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new RuntimeException("Продукт неактивен: " + product.getCode());
        }

        PromoCode promo = resolvePromo(request.getPromoCode(), product.getPriceKopecks());
        long priceKopecks = promo != null
                ? MoneyUtil.applyPromoDiscount(product.getPriceKopecks(), promo.getDiscountPercent(),
                        promo.getDiscountAmountKopecks())
                : product.getPriceKopecks();

        Amount amount = Amount.builder()
                .value(MoneyUtil.kopecksToString(priceKopecks))
                .currency(Currency.RUB.getCode())
                .build();
        String description = "Покупка: " + product.getTitle();
        String returnUrl = buildReturnUrl(product, request);

        if (TinkoffPaymentSupport.PROVIDER_NAME.equalsIgnoreCase(trainingProvider)) {
            return purchaseViaTinkoff(request, product, promo, priceKopecks, amount, description, returnUrl);
        }
        return purchaseViaYooKassa(request, product, promo, amount, description, returnUrl);
    }

    private PaymentUrlResponse purchaseViaYooKassa(PurchaseRequest request, PaymentProduct product, PromoCode promo,
                                                   Amount amount, String description, String returnUrl) {
        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(amount)
                .description(description)
                .capture(true)
                .confirmation(new PaymentConfirmation(CONFIRMATION_REDIRECT, returnUrl))
                .receipt(buildYooKassaReceipt(product, amount, request))
                .paymentMode(PAYMENT_MODE)
                .paymentSubject(PAYMENT_SUBJECT)
                .build();

        YooKassaPaymentResponse response = yooKassaService.createPayment(paymentRequest);

        if (Boolean.TRUE.equals(response.getPaid())) {
            throw new RuntimeException("Продукт уже оплачен");
        }

        PaymentTransaction transaction = baseTransaction(request, product, promo)
                .provider(PaymentProvider.YOOKASSA)
                .externalPaymentId(response.getId().toString())
                .amount(MoneyUtil.stringToKopecks(response.getAmount().getValue()))
                .currency(Currency.fromCode(response.getAmount().getCurrency()))
                .description(response.getDescription())
                .paymentUrl(response.getConfirmation() != null ? response.getConfirmation().getConfirmationUrl() : null)
                .status(resolveYooKassaStatus(response.getStatus()))
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(
                response.getId().toString(),
                response.getConfirmation().getConfirmationUrl(),
                response.getAmount()
        );
    }

    private PaymentUrlResponse purchaseViaTinkoff(PurchaseRequest request, PaymentProduct product, PromoCode promo,
                                                  long priceKopecks, Amount amount, String description,
                                                  String returnUrl) {
        String phone = PhoneUtil.toE164(request.getPhone());
        TinkoffInitRequest initRequest = tinkoffSupport.initRequest(priceKopecks, UUID.randomUUID().toString(), description)
                .successURL(appendParam(returnUrl, "status", "success"))
                .failURL(appendParam(returnUrl, "status", "fail"))
                .receipt(tinkoffSupport.receipt(request.getEmail(), phone != null ? "+" + phone : null,
                        List.of(tinkoffSupport.receiptItem(product.getDescription(), priceKopecks, 1, PAYMENT_SUBJECT))))
                .build();

        TinkoffInitResponse response = tinkoffService.init(initRequest);

        PaymentTransaction transaction = baseTransaction(request, product, promo)
                .provider(PaymentProvider.TINKOFF)
                .externalPaymentId(response.getPaymentId())
                .amount(priceKopecks)
                .currency(Currency.RUB)
                .description(description)
                .paymentUrl(response.getPaymentURL())
                .status(tinkoffSupport.resolveStatus(response.getStatus()))
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(response.getPaymentId(), response.getPaymentURL(), amount);
    }

    /** Общие для обоих провайдеров поля транзакции: продукт, контакты покупателя, промокод. */
    private PaymentTransaction.PaymentTransactionBuilder baseTransaction(PurchaseRequest request,
                                                                         PaymentProduct product,
                                                                         PromoCode promo) {
        return PaymentTransaction.builder()
                .productCode(product.getCode())
                .email(request.getEmail())
                .contactName(blankToNull(request.getFullName()))
                .contactPhone(blankToNull(request.getPhone()))
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .promoCode(promo != null ? promo.getCode() : null)
                .discountPercent(promo != null ? promo.getDiscountPercent() : null)
                .discountAmountKopecks(promo != null ? promo.getDiscountAmountKopecks() : null);
    }

    /** Null, если код не передан; исключение, если передан, но невалиден — молча игнорировать нельзя. */
    private PromoCode resolvePromo(String rawCode, long priceKopecks) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        PromoCode promo = getterPromoCode.getByCode(rawCode)
                .orElseThrow(() -> new InvalidPromoCodeException("Промокод не найден: " + PromoCode.normalize(rawCode)));
        if (!promo.isCurrentlyValid()) {
            throw new InvalidPromoCodeException("Промокод недействителен или его срок истёк: " + promo.getCode());
        }
        if (!promo.meetsMinOrder(priceKopecks)) {
            throw new InvalidPromoCodeException("Промокод " + promo.getCode() + " действует для заказов от "
                    + MoneyUtil.formatRubles(promo.getMinOrderKopecks()) + ".");
        }
        return promo;
    }

    private PaymentTransactionStatus resolveYooKassaStatus(String yooKassaStatus) {
        PaymentTransactionStatus status = paymentStatusConverter.fromYooKassa(yooKassaStatus);
        return status != null ? status : PaymentTransactionStatus.PENDING;
    }

    private String buildReturnUrl(PaymentProduct product, PurchaseRequest request) {
        String domain = resolveDomain(request.getReturnUrl());
        String path = product.getSuccessUrlPath();
        if (domain.endsWith("/") && path.startsWith("/")) {
            return domain + path.substring(1);
        }
        if (!domain.endsWith("/") && !path.startsWith("/")) {
            return domain + "/" + path;
        }
        return domain + path;
    }

    private String resolveDomain(String requestedReturnUrl) {
        String fromRequest = extractAllowedOrigin(requestedReturnUrl);
        if (fromRequest != null) {
            return fromRequest;
        }
        String fromHeader = extractAllowedOrigin(httpRequest.getHeader("Origin"));
        if (fromHeader != null) {
            return fromHeader;
        }
        return defaultDomain;
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

    private PaymentReceipt buildYooKassaReceipt(PaymentProduct product, Amount amount, PurchaseRequest request) {
        String fullName = (request.getFullName() == null || request.getFullName().isBlank())
                ? DEFAULT_FULL_NAME
                : request.getFullName();
        PaymentCustomer customer = new PaymentCustomer(fullName, request.getEmail(), PhoneUtil.toE164(request.getPhone()));
        PaymentItem item = new PaymentItem(product.getDescription(), amount, yookassaVatCode, 1);
        return new PaymentReceipt(customer, List.of(item));
    }
}
