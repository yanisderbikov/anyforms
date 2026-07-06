package ru.anyforms.service.payment.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.OrderRepository;
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
import java.util.stream.Collectors;

/**
 * Оформление заказа маркетплейса (order-first): заказ создаётся сразу со статусом
 * AWAITING_PAYMENT вместе с позициями, затем создаётся платёж в Юкассе. Вебхук
 * переводит заказ в PAID. Брошенные корзины остаются заказами AWAITING_PAYMENT
 * (в рабочие списки цеха не попадают) — их можно дожимать как лидов.
 */
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
    private final OrderRepository orderRepository;
    private final CustomProductItemRepository customProductItemRepository;
    private final PaymentStatusConverter paymentStatusConverter;
    private final HttpServletRequest httpRequest;

    @Value("${payment.default-domain}")
    private String defaultDomain;

    @Value("${payment.allowed-return-hosts}")
    private String allowedReturnHosts;

    @Value("${payment.marketplace.vat-code:1}")
    private Integer marketplaceVatCode;

    /** Позиция корзины после серверной валидации и прайсинга. */
    private record PricedItem(Product product, int quantity, long unitKopecks) {
    }

    @Override
    @Transactional
    public PaymentUrlResponse purchase(CartPurchaseRequest request) {
        List<PricedItem> priced = priceItems(request.getItems());
        long totalKopecks = priced.stream().mapToLong(i -> i.unitKopecks() * i.quantity()).sum();
        long totalQty = priced.stream().mapToLong(PricedItem::quantity).sum();

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
                .receipt(buildReceipt(fullName, request.getEmail(), priced))
                .paymentMode(PAYMENT_MODE)
                .paymentSubject(PAYMENT_SUBJECT)
                .build();

        YooKassaPaymentResponse response = yooKassaService.createPayment(paymentRequest);

        Order order = createAwaitingOrder(request, fullName, priced);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .externalPaymentId(response.getId())
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .amount(MoneyUtil.stringToKopecks(response.getAmount().getValue()))
                .currency(Currency.fromCode(response.getAmount().getCurrency()))
                .description(response.getDescription())
                .email(request.getEmail())
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .status(resolveStatus(response.getStatus()))
                .orderId(order.getId())
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(
                response.getId(),
                response.getConfirmation().getConfirmationUrl(),
                response.getAmount());
    }

    private List<PricedItem> priceItems(List<CartItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Корзина пуста");
        }
        List<PricedItem> priced = new ArrayList<>();
        for (CartItemDTO cartItem : items) {
            Product product = getterProduct.getById(cartItem.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Товар не найден: " + cartItem.getProductId()));
            int quantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
            if (quantity < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Некорректное количество для товара " + product.getName());
            }
            priced.add(new PricedItem(product, quantity, parsePriceToKopecks(product.getPrice(), product.getName())));
        }
        return priced;
    }

    private PaymentReceipt buildReceipt(String fullName, String email, List<PricedItem> priced) {
        List<PaymentItem> receiptItems = priced.stream()
                .map(i -> new PaymentItem(
                        i.product().getName(),
                        Amount.builder()
                                .value(MoneyUtil.kopecksToString(i.unitKopecks()))
                                .currency(Currency.RUB.getCode())
                                .build(),
                        marketplaceVatCode,
                        i.quantity()))
                .collect(Collectors.toList());
        return new PaymentReceipt(new PaymentCustomer(fullName, email), receiptItems);
    }

    private Order createAwaitingOrder(CartPurchaseRequest request, String fullName, List<PricedItem> priced) {
        Order order = new Order();
        order.setRetail(false);
        order.setPaymentStatus(OrderPaymentStatus.AWAITING_PAYMENT);
        order.setContactName(fullName);
        order.setContactPhone(request.getPhone());
        order.setPvzSdekCity(request.getPvzCity());
        order.setPvzSdekStreet(request.getPvzStreet());
        order.setComment("Заказ с сайта. ПВЗ СДЭК: " + pvzSummary(request) + ". Состав: " + itemsSummary(priced));
        Order saved = orderRepository.save(order);

        for (PricedItem item : priced) {
            CustomProductItem custom = new CustomProductItem();
            custom.setOrder(saved);
            custom.setProductName(item.product().getName());
            custom.setQuantity(item.quantity());
            custom.setPriceKopecks(item.unitKopecks());
            customProductItemRepository.save(custom);
        }
        return saved;
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

    private String itemsSummary(List<PricedItem> priced) {
        return priced.stream()
                .map(i -> i.product().getName() + " ×" + i.quantity())
                .collect(Collectors.joining(", "));
    }

    private String pvzSummary(CartPurchaseRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getPvzCity() != null && !request.getPvzCity().isBlank()) {
            sb.append(request.getPvzCity());
        }
        if (request.getPvzStreet() != null && !request.getPvzStreet().isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(request.getPvzStreet());
        }
        if (request.getPvzCode() != null && !request.getPvzCode().isBlank()) {
            sb.append(" [").append(request.getPvzCode()).append("]");
        }
        return sb.toString();
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
