package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.promo.CoursePromoDTO;
import ru.anyforms.dto.promo.CoursePromoOutcome;
import ru.anyforms.dto.promo.PromoPopupAfterPurchaseRequest;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverPromoCode;
import ru.anyforms.repository.SaverPromoPopupLead;
import ru.anyforms.repository.TransactionLock;
import ru.anyforms.service.payment.PromoReservationService;
import ru.anyforms.service.promo.CoursePromoService;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;
import ru.anyforms.util.MoneyUtil;
import ru.anyforms.util.PhoneUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
class CoursePromoServiceImpl implements CoursePromoService {

    static final int SINGLE_USE = 1;
    static final List<String> COURSE_PRODUCT_CODES = List.of(
            PaymentProduct.CODE_COURSE,
            PaymentProduct.CODE_COURSE_PERSONAL,
            PaymentProduct.CODE_COURSE_STREAM_2);
    private static final Pattern ORDER_NUMBER = Pattern.compile("[A-Z0-9]{6}");
    private static final int MAX_PHONE_LENGTH = 32;
    private static final int MAX_IP_LENGTH = 64;
    private static final long KOPECKS_PER_RUBLE = 100;

    private final GetterPromoPopup getterPromoPopup;
    private final GetterPromoPopupLead getterPromoPopupLead;
    private final SaverPromoPopupLead saverPromoPopupLead;
    private final GetterPromoCode getterPromoCode;
    private final SaverPromoCode saverPromoCode;
    private final GetterPaymentProduct getterPaymentProduct;
    private final GetterTransaction getterTransaction;
    private final OrderRepository orderRepository;
    private final PromoClientChecker promoClientChecker;
    private final PromoReservationService promoReservationService;
    private final TransactionLock transactionLock;
    private final ClaimRateLimiter claimRateLimiter;
    private final PromoCodeGenerator promoCodeGenerator;

    @Override
    @Transactional
    public CoursePromoOutcome afterPurchase(PromoPopupAfterPurchaseRequest request, String ip) {
        if (ip != null && !claimRateLimiter.tryAcquire("course-after-purchase:" + ip, ClaimRateLimiter.ISSUE_ATTEMPTS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много попыток. Попробуйте через несколько минут.");
        }
        String orderNumber = request.getOrderNumber() == null
                ? ""
                : request.getOrderNumber().trim().toUpperCase(Locale.ROOT);
        if (!ORDER_NUMBER.matcher(orderNumber).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден.");
        }
        return issue(orderNumber, ip);
    }

    @Override
    @Transactional
    public CoursePromoOutcome issueForOrder(String orderPublicId) {
        return issue(orderPublicId, null);
    }

    private CoursePromoOutcome issue(String orderNumber, String ip) {
        Order order = orderRepository.findByPublicId(orderNumber)
                .filter(o -> o.getSource() == OrderSource.MARKETPLACE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден."));
        if (order.getPaymentStatus() == OrderPaymentStatus.AWAITING_PAYMENT) {
            return CoursePromoOutcome.awaitingPayment();
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            return CoursePromoOutcome.none();
        }
        String shopSlug = order.getShop() == null || order.getShop().getSlug() == null
                ? Shop.DEFAULT_SLUG
                : order.getShop().getSlug();
        long paid = paidKopecks(order.getId());
        Optional<PromoPopup> live = getterPromoPopup.getLive(shopSlug, Instant.now()).stream()
                .filter(PromoPopup::isCourseAfterPurchase)
                .filter(p -> p.matchesOrderSum(paid))
                .findFirst();
        if (live.isEmpty()) {
            return CoursePromoOutcome.none();
        }
        PromoPopup popup = live.get();
        PromoClient client = PromoClient.of(order.getEmail(), order.getContactPhone(), order.getDeviceId());
        List<String> lockKeys = new ArrayList<>(List.of("promo-popup:" + popup.getId() + ":order:" + order.getId()));
        if (popup.getMaxShows() != null) {
            lockKeys.addAll(clientLockKeys(popup, client));
        }
        transactionLock.lockAll(lockKeys);

        Optional<PromoPopupLead> previous = getterPromoPopupLead.getLatestForOrder(popup.getId(), order.getId());
        if (previous.isPresent()) {
            return reissue(popup, previous.get());
        }
        if (!client.hasContact()) {
            return CoursePromoOutcome.none();
        }
        Optional<PaymentProduct> productFound = getterPaymentProduct.getByCode(popup.getProductCode())
                .filter(p -> Boolean.TRUE.equals(p.getActive()));
        if (productFound.isEmpty()) {
            log.warn("Попап {}: продукт {} не найден или выключен, код на курс не выдан",
                    popup.getId(), popup.getProductCode());
            return CoursePromoOutcome.none();
        }
        PaymentProduct product = productFound.get();
        if (alreadyBought(product.getCode(), client)) {
            log.info("Попап {}: по заказу {} код на курс не выдан — курс уже куплен", popup.getId(), order.getPublicId());
            return CoursePromoOutcome.none();
        }
        if (reachedClientLimit(popup, client)) {
            log.info("Попап {}: по заказу {} код на курс не выдан — клиент уже получал код {} раз",
                    popup.getId(), order.getPublicId(), popup.getMaxShows());
            return CoursePromoOutcome.none();
        }
        long discount = discountFor(paid, popup.getDiscountPercent(), popup.getDiscountAmountKopecks(),
                product.currentPriceKopecks());
        if (discount <= 0) {
            return CoursePromoOutcome.none();
        }

        Instant now = Instant.now();
        PromoCode promo = saverPromoCode.save(PromoCode.builder()
                .code(promoCodeGenerator.generate(popup.getCodePrefix()))
                .discountPercent(0)
                .discountAmountKopecks(discount)
                .active(true)
                .validFrom(now)
                .validUntil(now.plus(Duration.ofDays(popup.getCodeTtlDays())))
                .popupId(popup.getId())
                .ownerEmail(client.email().isEmpty() ? null : client.email())
                .ownerPhoneLast10(client.phoneLast10().isEmpty() ? null : client.phoneLast10())
                .maxUses(SINGLE_USE)
                .firstOrderOnly(false)
                .productCode(product.getCode())
                .build());
        PromoPopupLead lead = saverPromoPopupLead.save(PromoPopupLead.builder()
                .popupId(popup.getId())
                .promoCodeId(promo.getId())
                .code(promo.getCode())
                .shopSlug(popup.getShopSlug())
                .orderId(order.getId())
                .email(promo.getOwnerEmail())
                .phone(storedPhone(order.getContactPhone()))
                .phoneLast10(promo.getOwnerPhoneLast10())
                .deviceId(client.deviceIdOrNull())
                .ip(crop(ip, MAX_IP_LENGTH))
                .build());
        log.info("Попап {}: по оплаченному заказу {} ({} коп.) выдан код на курс {} со скидкой {} коп. (заявка {})",
                popup.getId(), order.getPublicId(), paid, promo.getCode(), discount, lead.getId());
        return CoursePromoOutcome.ready(CoursePromoDTO.of(popup, promo, product, false));
    }

    @Override
    public Optional<String> previewRejection(PromoCode promo, PaymentProduct product, PromoClient client) {
        return rejection(promo, product, client, false);
    }

    @Override
    public Optional<String> checkoutRejection(PromoCode promo, PaymentProduct product, PromoClient client) {
        if (promoClientChecker.exhausted(promo)) {
            promoReservationService.releaseOwnReservations(promo, client);
        }
        return rejection(promo, product, client, true);
    }

    private Optional<String> rejection(PromoCode promo, PaymentProduct product, PromoClient client, boolean checkout) {
        String code = promo.getCode();
        if (!promo.isForProduct(product.getCode())) {
            return Optional.of(getterPaymentProduct.getByCode(promo.getProductCode())
                    .map(p -> "Промокод " + code + " действует только на «" + p.getTitle() + "».")
                    .orElse("Промокод " + code + " действует только на другой продукт."));
        }
        if (!client.hasContact()) {
            return checkout
                    ? Optional.of("Чтобы применить промокод " + code + ", укажите телефон, на который он выдан.")
                    : Optional.empty();
        }
        String ownerPhone = promo.getOwnerPhoneLast10();
        if (ownerPhone != null && !ownerPhone.isBlank()) {
            if (client.phoneLast10().isEmpty()) {
                return Optional.of("Укажите телефон: промокод " + code
                        + " выдан на номер, который вы указывали при заказе в магазине.");
            }
            if (!ownerPhone.equals(client.phoneLast10())) {
                return Optional.of("Промокод " + code
                        + " выдан на другой номер телефона. Укажите номер, который вы указывали при заказе в магазине.");
            }
        } else if (!promo.belongsTo(client.email(), client.phoneLast10())) {
            return Optional.of("Промокод " + code + " персональный: укажите почту, на которую он выдан.");
        }
        if (alreadyBought(promo.getProductCode(), client)) {
            return Optional.of("По этому телефону или почте курс уже куплен — промокод " + code + " применить нельзя.");
        }
        boolean exhausted = checkout ? promoClientChecker.exhausted(promo) : previewExhausted(promo, client);
        if (exhausted) {
            return Optional.of("Промокод " + code + " уже использован.");
        }
        return Optional.empty();
    }

    private boolean previewExhausted(PromoCode promo, PromoClient client) {
        return promo.getMaxUses() != null
                && getterTransaction.countPromoUsesExceptOwnPending(promo.getCode(),
                Instant.now().minus(PromoClientChecker.PENDING_RESERVATION), client.email(), client.phoneLast10(),
                "") >= promo.getMaxUses();
    }

    private boolean alreadyBought(String productCode, PromoClient client) {
        return client.hasContact()
                && getterTransaction.customerPaidAnyProduct(productFamily(productCode), client.email(),
                client.phoneLast10());
    }

    static List<String> productFamily(String productCode) {
        return COURSE_PRODUCT_CODES.contains(productCode) ? COURSE_PRODUCT_CODES : List.of(productCode);
    }

    private boolean reachedClientLimit(PromoPopup popup, PromoClient client) {
        if (popup.getMaxShows() == null || client.isAnonymous()) {
            return false;
        }
        return getterPromoPopupLead.countForClient(popup.getId(), client.email(), client.phoneLast10(),
                client.deviceId()) >= popup.getMaxShows();
    }

    private CoursePromoOutcome reissue(PromoPopup popup, PromoPopupLead lead) {
        if (lead.getPromoCodeId() == null) {
            return CoursePromoOutcome.none();
        }
        return getterPromoCode.getById(lead.getPromoCodeId())
                .filter(PromoCode::isCurrentlyValid)
                .filter(promo -> !promoClientChecker.exhausted(promo))
                .flatMap(promo -> getterPaymentProduct.getByCode(promo.getProductCode())
                        .map(product -> CoursePromoOutcome.ready(CoursePromoDTO.of(popup, promo, product, true))))
                .orElseGet(CoursePromoOutcome::none);
    }

    private long paidKopecks(Long orderId) {
        return getterTransaction.getByOrderId(orderId).stream()
                .filter(t -> t.getStatus() == PaymentTransactionStatus.SUCCEEDED)
                .mapToLong(PaymentTransaction::getAmount)
                .sum();
    }

    static long discountFor(long paidKopecks, Integer percent, Long fixedKopecks, long coursePriceKopecks) {
        long discount = fixedKopecks != null
                ? fixedKopecks
                : paidKopecks * (percent == null ? 0 : percent) / 100 / KOPECKS_PER_RUBLE * KOPECKS_PER_RUBLE;
        return Math.min(discount, coursePriceKopecks - MoneyUtil.MIN_PAYABLE_KOPECKS);
    }

    private static List<String> clientLockKeys(PromoPopup popup, PromoClient client) {
        List<String> keys = new ArrayList<>();
        if (!client.email().isEmpty()) {
            keys.add("promo-popup:" + popup.getId() + ":email:" + client.email());
        }
        if (!client.phoneLast10().isEmpty()) {
            keys.add("promo-popup:" + popup.getId() + ":phone:" + client.phoneLast10());
        }
        if (client.hasDevice()) {
            keys.add("promo-popup:" + popup.getId() + ":device:" + client.deviceId());
        }
        return keys;
    }

    private static String storedPhone(String raw) {
        String e164 = PhoneUtil.toE164(raw);
        return e164 == null ? null : crop("+" + e164, MAX_PHONE_LENGTH);
    }

    private static String crop(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
