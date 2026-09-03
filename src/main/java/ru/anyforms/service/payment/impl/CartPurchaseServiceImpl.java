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
import ru.anyforms.dto.payment.PromoCheckResponse;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceipt;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceiptItem;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;
import ru.anyforms.dto.payment.yookassa.PaymentConfirmation;
import ru.anyforms.dto.payment.yookassa.PaymentCustomer;
import ru.anyforms.dto.payment.yookassa.PaymentItem;
import ru.anyforms.dto.payment.yookassa.PaymentReceipt;
import ru.anyforms.model.DeliveryMethod;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.marketplace.ProductVariant;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.CartPurchaseService;
import ru.anyforms.service.payment.InvalidPromoCodeException;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.TinkoffService;
import ru.anyforms.service.payment.YooKassaService;
import ru.anyforms.service.product.ShopService;
import ru.anyforms.util.MoneyUtil;
import ru.anyforms.util.PhoneUtil;
import ru.anyforms.util.PickupAddressDetector;
import ru.anyforms.util.PublicIdGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.anyforms.service.payment.impl.TinkoffPaymentSupport.appendParam;

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
    private final TinkoffService tinkoffService;
    private final TinkoffPaymentSupport tinkoffSupport;
    private final SaverTransaction saverTransaction;
    private final GetterProduct getterProduct;
    private final GetterPromoCode getterPromoCode;
    private final GetterTransaction getterTransaction;
    private final OrderRepository orderRepository;
    private final PaymentStatusConverter paymentStatusConverter;
    private final HttpServletRequest httpRequest;
    private final ShopService shopService;

    @Value("${payment.default-domain}")
    private String defaultDomain;

    @Value("${payment.allowed-return-hosts}")
    private String allowedReturnHosts;

    @Value("${payment.yookassa.vat-code}")
    private Integer yookassaVatCode;

    @Value("${payment.marketplace.provider}")
    private String marketplaceProvider;

    @Value("${amocrm.products.catalog.id}")
    private Long productsCatalogId;

    /** Позиция корзины после серверной валидации и прайсинга; variant задан, если товар с вариантами. */
    private record PricedItem(Product product, ProductVariant variant, int quantity, long unitKopecks) {
    }

    @Override
    @Transactional
    public PaymentUrlResponse purchase(CartPurchaseRequest request) {
        List<PricedItem> priced = priceItems(request.getItems());
        long subtotalKopecks = priced.stream().mapToLong(i -> i.unitKopecks() * i.quantity()).sum();
        PromoCode promo = resolvePromo(request.getPromoCode(), request.getEmail(), request.getPhone(), subtotalKopecks);
        if (promo != null) {
            priced = applyPromoToItems(priced, promo);
        }
        long totalKopecks = priced.stream().mapToLong(i -> i.unitKopecks() * i.quantity()).sum();
        long totalQty = priced.stream().mapToLong(PricedItem::quantity).sum();

        Amount amount = Amount.builder()
                .value(MoneyUtil.kopecksToString(totalKopecks))
                .currency(Currency.RUB.getCode())
                .build();

        String fullName = (request.getFullName() == null || request.getFullName().isBlank())
                ? DEFAULT_FULL_NAME
                : request.getFullName().trim();

        Order order = createAwaitingOrder(request, fullName, priced);
        String description = "Заказ anyforms: " + totalQty + " " + pluralItems(totalQty);
        String returnUrl = buildReturnUrl(request.getReturnUrl(), order.getPublicId());

        if (TinkoffPaymentSupport.PROVIDER_NAME.equalsIgnoreCase(marketplaceProvider)) {
            return purchaseViaTinkoff(request, order, priced, totalKopecks, description, returnUrl, amount, promo);
        }
        return purchaseViaYooKassa(request, order, priced, fullName, description, returnUrl, amount, promo);
    }

    @Override
    public PromoCheckResponse checkPromo(String code, String email, String phone, Long totalKopecks) {
        Optional<PromoCode> found = getterPromoCode.getByCode(code);
        if (found.isEmpty()) {
            return PromoCheckResponse.builder().message("Такого промокода нет.").build();
        }
        PromoCode promo = found.get();
        if (!promo.isCurrentlyValid()) {
            return PromoCheckResponse.builder()
                    .code(promo.getCode()).message("Срок действия промокода истёк.").build();
        }
        if (getterTransaction.promoUsedByCustomer(promo.getCode(), email, PhoneUtil.last10(phone))) {
            return PromoCheckResponse.builder()
                    .code(promo.getCode()).message("Этот промокод уже был использован.").build();
        }
        // totalKopecks приходит с клиента и только для ранней подсказки:
        // при оформлении порог проверяется заново по серверным ценам.
        if (totalKopecks != null && !promo.meetsMinOrder(totalKopecks)) {
            return PromoCheckResponse.builder()
                    .code(promo.getCode())
                    .minOrderKopecks(promo.getMinOrderKopecks())
                    .message(minOrderMessage(promo))
                    .build();
        }
        return PromoCheckResponse.builder()
                .valid(true)
                .code(promo.getCode())
                .discountPercent(promo.getDiscountPercent())
                .discountAmountKopecks(promo.getDiscountAmountKopecks())
                .minOrderKopecks(promo.getMinOrderKopecks())
                .validUntil(promo.getValidUntil() != null ? promo.getValidUntil().toString() : null)
                .build();
    }

    private PromoCode resolvePromo(String rawCode, String email, String phone, long subtotalKopecks) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        PromoCode promo = getterPromoCode.getByCode(rawCode)
                .orElseThrow(() -> new InvalidPromoCodeException("Промокод не найден: " + PromoCode.normalize(rawCode)));
        if (!promo.isCurrentlyValid()) {
            throw new InvalidPromoCodeException("Промокод недействителен или его срок истёк: " + promo.getCode());
        }
        if (getterTransaction.promoUsedByCustomer(promo.getCode(), email, PhoneUtil.last10(phone))) {
            throw new InvalidPromoCodeException("Промокод " + promo.getCode() + " уже был использован.");
        }
        if (!promo.meetsMinOrder(subtotalKopecks)) {
            throw new InvalidPromoCodeException(minOrderMessage(promo));
        }
        return promo;
    }

    private String minOrderMessage(PromoCode promo) {
        return "Промокод " + promo.getCode() + " действует для заказов от "
                + MoneyUtil.formatRubles(promo.getMinOrderKopecks()) + ".";
    }

    /**
     * Применяет промокод к позициям: процент — с каждой единицы (HALF_UP до копейки),
     * затем фиксированная сумма распределяется по позициям пропорционально их стоимости.
     * Позиция с «неделимым» остатком расщепляется на две строки с ценами, отличающимися
     * на копейку, — чек у провайдера (цена × количество) обязан сходиться с платежом.
     * Итог не опускается ниже {@link MoneyUtil#MIN_PAYABLE_KOPECKS}.
     */
    private List<PricedItem> applyPromoToItems(List<PricedItem> priced, PromoCode promo) {
        List<PricedItem> discounted = priced.stream()
                .map(i -> new PricedItem(i.product(), i.variant(), i.quantity(),
                        MoneyUtil.applyDiscountPercent(i.unitKopecks(), promo.getDiscountPercent())))
                .toList();
        if (!promo.hasAmountDiscount()) {
            return discounted;
        }
        long afterPercent = discounted.stream().mapToLong(i -> i.unitKopecks() * i.quantity()).sum();
        long reduction = Math.min(promo.getDiscountAmountKopecks(),
                Math.max(0, afterPercent - MoneyUtil.MIN_PAYABLE_KOPECKS));
        if (reduction == 0) {
            return discounted;
        }
        long[] lineTotals = discounted.stream().mapToLong(i -> i.unitKopecks() * i.quantity()).toArray();
        long[] shares = MoneyUtil.distributeReduction(lineTotals, reduction);
        List<PricedItem> result = new ArrayList<>();
        for (int i = 0; i < discounted.size(); i++) {
            PricedItem item = discounted.get(i);
            long perUnit = shares[i] / item.quantity();
            int extraKopeckUnits = (int) (shares[i] % item.quantity());
            result.add(new PricedItem(item.product(), item.variant(),
                    item.quantity() - extraKopeckUnits, item.unitKopecks() - perUnit));
            if (extraKopeckUnits > 0) {
                result.add(new PricedItem(item.product(), item.variant(),
                        extraKopeckUnits, item.unitKopecks() - perUnit - 1));
            }
        }
        return result;
    }

    private PaymentUrlResponse purchaseViaYooKassa(CartPurchaseRequest request, Order order,
                                                   List<PricedItem> priced, String fullName,
                                                   String description, String returnUrl, Amount amount,
                                                   PromoCode promo) {
        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(amount)
                .description(description)
                .capture(true)
                .confirmation(new PaymentConfirmation(CONFIRMATION_REDIRECT, returnUrl))
                .receipt(buildReceipt(fullName, request.getEmail(), request.getPhone(), priced))
                .paymentMode(PAYMENT_MODE)
                .paymentSubject(PAYMENT_SUBJECT)
                .build();

        YooKassaPaymentResponse response = yooKassaService.createPayment(paymentRequest);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .provider(PaymentProvider.YOOKASSA)
                .externalPaymentId(response.getId().toString())
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .amount(MoneyUtil.stringToKopecks(response.getAmount().getValue()))
                .currency(Currency.fromCode(response.getAmount().getCurrency()))
                .description(response.getDescription())
                .email(request.getEmail())
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .status(resolveStatus(response.getStatus()))
                .orderId(order.getId())
                .promoCode(promo != null ? promo.getCode() : null)
                .discountPercent(promo != null ? promo.getDiscountPercent() : null)
                .discountAmountKopecks(promo != null ? promo.getDiscountAmountKopecks() : null)
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(
                response.getId().toString(),
                response.getConfirmation().getConfirmationUrl(),
                response.getAmount());
    }

    private PaymentUrlResponse purchaseViaTinkoff(CartPurchaseRequest request, Order order,
                                                  List<PricedItem> priced, long totalKopecks,
                                                  String description, String returnUrl, Amount amount,
                                                  PromoCode promo) {
        TinkoffInitRequest initRequest = tinkoffSupport.initRequest(totalKopecks, order.getPublicId(), description)
                .successURL(appendParam(returnUrl, "status", "success"))
                .failURL(appendParam(returnUrl, "status", "fail"))
                .redirectDueDate(TinkoffPaymentSupport.redirectDueDate(
                        Instant.now().plus(TinkoffPaymentSupport.CART_LINK_TTL)))
                .receipt(buildTinkoffReceipt(request, priced))
                .build();

        TinkoffInitResponse response = tinkoffService.init(initRequest);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .provider(PaymentProvider.TINKOFF)
                .externalPaymentId(response.getPaymentId())
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .amount(totalKopecks)
                .currency(Currency.RUB)
                .description(description)
                .email(request.getEmail())
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .status(tinkoffSupport.resolveStatus(response.getStatus()))
                .orderId(order.getId())
                .promoCode(promo != null ? promo.getCode() : null)
                .discountPercent(promo != null ? promo.getDiscountPercent() : null)
                .discountAmountKopecks(promo != null ? promo.getDiscountAmountKopecks() : null)
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(response.getPaymentId(), response.getPaymentURL(), amount);
    }

    private TinkoffReceipt buildTinkoffReceipt(CartPurchaseRequest request, List<PricedItem> priced) {
        List<TinkoffReceiptItem> items = priced.stream()
                .map(i -> tinkoffSupport.receiptItem(
                        displayName(i.product(), i.variant()), i.unitKopecks(), i.quantity(), PAYMENT_SUBJECT))
                .collect(Collectors.toList());
        return tinkoffSupport.receipt(request.getEmail(), request.getPhone(), items);
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
            if (Boolean.FALSE.equals(product.getActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Товар недоступен для покупки: " + product.getName());
            }
            int quantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
            if (quantity < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Некорректное количество для товара " + product.getName());
            }
            ProductVariant variant = resolveVariant(product, cartItem.getVariantId());
            String price = variant != null ? variant.getPrice() : product.getPrice();
            priced.add(new PricedItem(product, variant, quantity, parsePriceToKopecks(price, displayName(product, variant))));
        }
        return priced;
    }

    private ProductVariant resolveVariant(Product product, UUID variantId) {
        boolean hasVariants = product.getVariants() != null && !product.getVariants().isEmpty();
        if (variantId == null) {
            if (hasVariants) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Для товара нужно выбрать вариант: " + product.getName());
            }
            return null;
        }
        if (!hasVariants) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Вариант товара не найден: " + product.getName());
        }
        return product.getVariants().stream()
                .filter(v -> variantId.equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Вариант товара не найден: " + product.getName()));
    }

    private String displayName(Product product, ProductVariant variant) {
        return variant == null ? product.getName() : product.getName() + " " + variant.getLabel();
    }

    private PaymentReceipt buildReceipt(String fullName, String email, String phone, List<PricedItem> priced) {
        List<PaymentItem> receiptItems = priced.stream()
                .map(i -> new PaymentItem(
                        displayName(i.product(), i.variant()),
                        Amount.builder()
                                .value(MoneyUtil.kopecksToString(i.unitKopecks()))
                                .currency(Currency.RUB.getCode())
                                .build(),
                        yookassaVatCode,
                        i.quantity()))
                .collect(Collectors.toList());
        return new PaymentReceipt(new PaymentCustomer(fullName, email, PhoneUtil.toE164(phone)), receiptItems);
    }

    private Order createAwaitingOrder(CartPurchaseRequest request, String fullName, List<PricedItem> priced) {
        Shop shop = resolveShop(request.getShopSlug(), priced);
        Order order = new Order();
        order.setShop(shop);
        order.setSource(OrderSource.MARKETPLACE);
        order.setRetail(true);
        order.setPaymentStatus(OrderPaymentStatus.AWAITING_PAYMENT);
        order.setPublicId(PublicIdGenerator.generateUnique(orderRepository::existsByPublicId));
        order.setContactName(fullName);
        order.setContactPhone(request.getPhone());
        order.setPvzSdekCity(request.getPvzCity());
        order.setPvzSdekStreet(request.getPvzStreet());
        order.setDeliveryMethod(PickupAddressDetector.isPickup(request.getPvzCity(), request.getPvzStreet())
                ? DeliveryMethod.PICKUP
                : DeliveryMethod.CDEK);

        for (PricedItem item : priced) {
            Product product = item.product();
            String baseName = product.getAmoProductName() != null && !product.getAmoProductName().isBlank()
                    ? product.getAmoProductName()
                    : product.getName();
            String itemName = item.variant() == null ? baseName : baseName + " " + item.variant().getLabel();
            OrderItem orderItem = new OrderItem();
            orderItem.setProductName(itemName);
            orderItem.setQuantity(item.quantity());
            orderItem.setProductId(product.getAmoProductId());
            orderItem.setCatalogId(productsCatalogId != null && productsCatalogId > 0 ? productsCatalogId : null);
            orderItem.setPriceKopecks(item.unitKopecks());
            order.addItem(orderItem);
        }
        return orderRepository.save(order);
    }

    private Shop resolveShop(String shopSlug, List<PricedItem> priced) {
        Shop shop = shopService.resolveBySlug(shopSlug);
        for (PricedItem item : priced) {
            boolean soldInShop = item.product().getShops() != null && item.product().getShops().stream()
                    .anyMatch(s -> s.getSlug().equals(shop.getSlug()));
            if (!soldInShop) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Товар не продаётся в магазине " + shop.getSlug() + ": " + item.product().getName());
            }
        }
        return shop;
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

    private String buildReturnUrl(String requestedReturnUrl, String orderPublicId) {
        String origin = extractAllowedOrigin(requestedReturnUrl);
        if (origin == null) {
            origin = extractAllowedOrigin(httpRequest.getHeader("Origin"));
        }

        String base;
        if (origin == null) {
            base = joinUrl(defaultDomain, DEFAULT_SUCCESS_PATH);
        } else if (requestedReturnUrl != null && !requestedReturnUrl.isBlank()) {
            // Клиент прислал полный URL с разрешённого хоста — используем как есть.
            base = requestedReturnUrl.trim();
        } else {
            base = joinUrl(origin, DEFAULT_SUCCESS_PATH);
        }
        return appendOrderParam(base, orderPublicId);
    }

    private String appendOrderParam(String url, String orderPublicId) {
        if (orderPublicId == null || orderPublicId.isBlank()) {
            return url;
        }
        return appendParam(url, "order", orderPublicId);
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
