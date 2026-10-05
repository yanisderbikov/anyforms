package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.amo.PromoPopupAmoLeadTaskPayload;
import ru.anyforms.dto.email.PromoPopupCodeEmailPayload;
import ru.anyforms.dto.promo.PromoPopupActiveRequest;
import ru.anyforms.dto.promo.PromoPopupClaimRequest;
import ru.anyforms.dto.promo.PromoPopupClaimResponse;
import ru.anyforms.dto.promo.PromoPopupIssueRequest;
import ru.anyforms.dto.promo.PublicPromoPopupDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.promo.PromoPopupView;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterPromoPopupView;
import ru.anyforms.repository.SaverPromoCode;
import ru.anyforms.repository.SaverPromoPopupLead;
import ru.anyforms.repository.SaverPromoPopupView;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;
import ru.anyforms.service.promo.PromoPopupPublicService;
import ru.anyforms.service.task.TaskAdder;
import ru.anyforms.util.PhoneUtil;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class PromoPopupPublicServiceImpl implements PromoPopupPublicService {

    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_RANDOM_LENGTH = 5;
    static final int SINGLE_USE = 1;
    static final Duration VIEW_DEDUP_WINDOW = Duration.ofMinutes(10);
    private static final int CODE_ATTEMPTS = 20;
    private static final int MAX_STORED_LENGTH = 255;
    private static final int MAX_PAGE_URL_LENGTH = 1024;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final GetterPromoPopup getterPromoPopup;
    private final GetterPromoPopupLead getterPromoPopupLead;
    private final SaverPromoPopupLead saverPromoPopupLead;
    private final GetterPromoPopupView getterPromoPopupView;
    private final SaverPromoPopupView saverPromoPopupView;
    private final GetterPromoCode getterPromoCode;
    private final SaverPromoCode saverPromoCode;
    private final PromoClientChecker promoClientChecker;
    private final TaskAdder taskAdder;
    private final ClaimRateLimiter claimRateLimiter;
    private final SecureRandom random = new SecureRandom();

    @Value("${promo.popup.consent-version}")
    private String consentVersion;

    private record LeadSource(String ip, String userAgent, String pageUrl, String utmSource, String utmMedium,
                              String utmCampaign, String utmContent, String utmTerm) {
    }

    @Override
    public Optional<PublicPromoPopupDTO> getActive(PromoPopupActiveRequest request) {
        String slug = request.getShop() == null || request.getShop().isBlank()
                ? Shop.DEFAULT_SLUG
                : request.getShop().trim();
        PromoClient client = PromoClient.of(request.getEmail(), request.getPhone(), request.getDeviceId());
        Instant now = Instant.now();
        for (PromoPopup popup : getterPromoPopup.getLive(slug, now)) {
            Optional<PublicPromoPopupDTO> shown = showFor(popup, client, slug, now);
            if (shown.isPresent()) {
                return shown;
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void recordView(UUID popupId, String rawDeviceId) {
        String deviceId = PromoClient.normalizeDeviceId(rawDeviceId);
        if (deviceId.isEmpty() || getterPromoPopup.getById(popupId).isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        boolean recent = getterPromoPopupView.lastViewAt(popupId, deviceId)
                .map(last -> last.isAfter(now.minus(VIEW_DEDUP_WINDOW)))
                .orElse(false);
        if (!recent) {
            saverPromoPopupView.save(PromoPopupView.builder().popupId(popupId).deviceId(deviceId).build());
        }
    }

    @Override
    @Transactional
    public PromoPopupClaimResponse claim(UUID popupId, PromoPopupClaimRequest request, String ip, String userAgent) {
        rejectBots(popupId, request.getWebsite(), "claim", ClaimRateLimiter.CLAIM_ATTEMPTS, ip);
        PromoPopup popup = livePopup(popupId);
        if (!popup.isContact()) {
            throw badRequest("Этот попап не выдаёт коды за контакт.");
        }
        if (!consentVersion.equals(request.getConsentVersion())) {
            throw conflict("Условия акции обновились. Обновите страницу и попробуйте ещё раз.");
        }
        String phoneDigits = PhoneUtil.toE164(request.getPhone());
        PromoClient client = PromoClient.of(request.getEmail(), phoneDigits, request.getDeviceId());
        if (phoneDigits == null || client.phoneLast10().isEmpty()) {
            throw badRequest("Проверьте номер телефона.");
        }

        Optional<PromoPopupLead> previous = getterPromoPopupLead.getLatestForClient(
                popup.getId(), client.email(), client.phoneLast10(), client.deviceId());
        if (previous.isPresent()) {
            PromoPopupLead lead = previous.get();
            boolean sameContact = client.email().equalsIgnoreCase(lead.getEmail() == null ? "" : lead.getEmail())
                    || client.phoneLast10().equals(lead.getPhoneLast10());
            if (!sameContact) {
                throw conflict("С этого устройства уже получали код по этой акции.");
            }
            return reissue(lead, client);
        }
        requireEligible(popup, client);

        PromoCode promo = issueCode(popup, client, true);
        PromoPopupLead lead = saverPromoPopupLead.save(leadBuilder(popup, promo, client, new LeadSource(ip, userAgent,
                request.getPageUrl(), request.getUtmSource(), request.getUtmMedium(), request.getUtmCampaign(),
                request.getUtmContent(), request.getUtmTerm()))
                .email(client.email())
                .phone("+" + phoneDigits)
                .phoneLast10(client.phoneLast10())
                .consentPersonalData(true)
                .consentAdvertising(true)
                .consentVersion(request.getConsentVersion())
                .build());

        taskAdder.addTask(new PromoPopupAmoLeadTaskPayload(lead.getId()));
        taskAdder.addTask(new PromoPopupCodeEmailPayload(lead.getId()));
        log.info("Попап {}: за контакт выдан код {} (заявка {})", popup.getId(), promo.getCode(), lead.getId());
        return response(promo, false);
    }

    @Override
    @Transactional
    public PromoPopupClaimResponse issue(UUID popupId, PromoPopupIssueRequest request, String ip, String userAgent) {
        rejectBots(popupId, request.getWebsite(), "issue", ClaimRateLimiter.ISSUE_ATTEMPTS, ip);
        PromoPopup popup = livePopup(popupId);
        if (!popup.isUniqueCode()) {
            throw badRequest("Этот попап не выдаёт одноразовые коды.");
        }
        PromoClient client = PromoClient.of(request.getEmail(), request.getPhone(), request.getDeviceId());
        if (!client.hasDevice()) {
            throw badRequest("Не удалось определить устройство. Обновите страницу и попробуйте ещё раз.");
        }

        Optional<PromoPopupLead> previous = getterPromoPopupLead.getLatestForClient(
                popup.getId(), "", "", client.deviceId());
        if (previous.isPresent()) {
            return reissue(previous.get(), client);
        }
        requireEligible(popup, client);

        PromoCode promo = issueCode(popup, client, false);
        PromoPopupLead lead = saverPromoPopupLead.save(leadBuilder(popup, promo, client, new LeadSource(ip, userAgent,
                request.getPageUrl(), request.getUtmSource(), request.getUtmMedium(), request.getUtmCampaign(),
                request.getUtmContent(), request.getUtmTerm())).build());
        log.info("Попап {}: устройству выдан одноразовый код {} (заявка {})", popup.getId(), promo.getCode(), lead.getId());
        return response(promo, false);
    }

    private Optional<PublicPromoPopupDTO> showFor(PromoPopup popup, PromoClient client, String shopSlug, Instant now) {
        if (popup.hidesKnownContacts() && client.hasContact()) {
            return Optional.empty();
        }
        if (!dueForDevice(popup, client, now)) {
            return Optional.empty();
        }
        if (popup.isPublicCode()) {
            return showablePromo(popup, shopSlug)
                    .filter(promo -> !(promo.isFirstOrderOnly() && promoClientChecker.hasOrders(client)))
                    .filter(promo -> !promoClientChecker.usedCode(promo.getCode(), client))
                    .filter(promo -> !promoClientChecker.exhausted(promo, client))
                    .map(promo -> PublicPromoPopupDTO.forPublicCode(popup, promo));
        }
        if (Boolean.TRUE.equals(popup.getFirstOrderOnly()) && promoClientChecker.hasOrders(client)) {
            return Optional.empty();
        }
        if (promoClientChecker.usedPopup(popup.getId(), client)) {
            return Optional.empty();
        }
        Optional<PromoPopupLead> issued = client.isAnonymous()
                ? Optional.empty()
                : getterPromoPopupLead.getLatestForClient(popup.getId(), client.email(), client.phoneLast10(),
                client.deviceId());
        if (popup.isUniqueCode()) {
            return issued.isEmpty() || stillUsable(issued.get(), client)
                    ? Optional.of(PublicPromoPopupDTO.forUniqueCode(popup))
                    : Optional.empty();
        }
        return issued.isPresent() ? Optional.empty() : Optional.of(PublicPromoPopupDTO.forContact(popup, consentVersion));
    }

    private boolean stillUsable(PromoPopupLead lead, PromoClient client) {
        return lead.getPromoCodeId() != null && getterPromoCode.getById(lead.getPromoCodeId())
                .filter(PromoCode::isCurrentlyValid)
                .filter(promo -> !promoClientChecker.usedCode(promo.getCode(), client))
                .filter(promo -> !promoClientChecker.exhausted(promo, client))
                .isPresent();
    }

    private boolean dueForDevice(PromoPopup popup, PromoClient client, Instant now) {
        if (!client.hasDevice()) {
            return true;
        }
        if (popup.getMaxShows() != null
                && getterPromoPopupView.countForDevice(popup.getId(), client.deviceId()) >= popup.getMaxShows()) {
            return false;
        }
        int hours = popup.getRepeatAfterHours() == null ? 0 : popup.getRepeatAfterHours();
        if (hours <= 0) {
            return true;
        }
        return getterPromoPopupView.lastViewAt(popup.getId(), client.deviceId())
                .map(last -> !now.isBefore(last.plus(Duration.ofHours(hours))))
                .orElse(true);
    }

    private Optional<PromoCode> showablePromo(PromoPopup popup, String shopSlug) {
        if (popup.getPromoCodeId() == null) {
            return Optional.empty();
        }
        return getterPromoCode.getById(popup.getPromoCodeId())
                .filter(PromoCode::isCurrentlyValid)
                .filter(promo -> !promo.isPersonal() && promo.getPopupId() == null)
                .filter(promo -> promo.allowedInShop(shopSlug));
    }

    private void rejectBots(UUID popupId, String honeypot, String action, int maxAttempts, String ip) {
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("Попап {}: заполнено поле-ловушка, запрос с IP {} отклонён", popupId, ip);
            throw badRequest("Не удалось выдать промокод.");
        }
        if (ip != null && !claimRateLimiter.tryAcquire(action + ":" + ip, maxAttempts)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много попыток. Попробуйте через несколько минут.");
        }
    }

    private PromoPopup livePopup(UUID popupId) {
        Instant now = Instant.now();
        return getterPromoPopup.getById(popupId)
                .filter(p -> p.isLiveAt(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Акция уже закончилась."));
    }

    private void requireEligible(PromoPopup popup, PromoClient client) {
        if (Boolean.TRUE.equals(popup.getFirstOrderOnly()) && promoClientChecker.hasOrders(client)) {
            throw conflict("Скидка действует только на первый заказ, а у вас уже есть заказы в нашем магазине.");
        }
        if (promoClientChecker.usedPopup(popup.getId(), client)) {
            throw conflict("Скидка по этой акции уже использована.");
        }
    }

    private PromoCode issueCode(PromoPopup popup, PromoClient client, boolean bindToContact) {
        Instant now = Instant.now();
        return saverPromoCode.save(PromoCode.builder()
                .code(generateUniqueCode(popup.getCodePrefix()))
                .discountPercent(popup.getDiscountPercent())
                .discountAmountKopecks(popup.getDiscountAmountKopecks())
                .minOrderKopecks(popup.getMinOrderKopecks())
                .active(true)
                .validFrom(now)
                .validUntil(now.plus(Duration.ofDays(popup.getCodeTtlDays())))
                .popupId(popup.getId())
                .shopSlug(popup.getShopSlug())
                .ownerEmail(bindToContact ? client.email() : null)
                .ownerPhoneLast10(bindToContact ? client.phoneLast10() : null)
                .ownerDeviceId(client.deviceIdOrNull())
                .maxUses(SINGLE_USE)
                .firstOrderOnly(Boolean.TRUE.equals(popup.getFirstOrderOnly()))
                .build());
    }

    private PromoPopupLead.PromoPopupLeadBuilder leadBuilder(PromoPopup popup, PromoCode promo, PromoClient client,
                                                            LeadSource source) {
        return PromoPopupLead.builder()
                .popupId(popup.getId())
                .promoCodeId(promo.getId())
                .code(promo.getCode())
                .shopSlug(popup.getShopSlug())
                .deviceId(client.deviceIdOrNull())
                .ip(crop(source.ip(), 64))
                .userAgent(crop(source.userAgent(), MAX_USER_AGENT_LENGTH))
                .pageUrl(crop(source.pageUrl(), MAX_PAGE_URL_LENGTH))
                .utmSource(crop(source.utmSource(), MAX_STORED_LENGTH))
                .utmMedium(crop(source.utmMedium(), MAX_STORED_LENGTH))
                .utmCampaign(crop(source.utmCampaign(), MAX_STORED_LENGTH))
                .utmContent(crop(source.utmContent(), MAX_STORED_LENGTH))
                .utmTerm(crop(source.utmTerm(), MAX_STORED_LENGTH));
    }

    private PromoPopupClaimResponse reissue(PromoPopupLead lead, PromoClient client) {
        PromoCode promo = lead.getPromoCodeId() == null
                ? null
                : getterPromoCode.getById(lead.getPromoCodeId()).orElse(null);
        if (promo == null || !promo.isCurrentlyValid()
                || promoClientChecker.usedCode(promo.getCode(), client)
                || promoClientChecker.exhausted(promo, client)) {
            throw conflict("Вы уже получали скидку по этой акции.");
        }
        return response(promo, true);
    }

    private PromoPopupClaimResponse response(PromoCode promo, boolean repeated) {
        return new PromoPopupClaimResponse(
                promo.getCode(),
                promo.getValidUntil() != null ? promo.getValidUntil().toString() : null,
                promo.getDiscountPercent(),
                promo.getDiscountAmountKopecks(),
                promo.getMinOrderKopecks(),
                repeated);
    }

    private String generateUniqueCode(String prefix) {
        String normalizedPrefix = PromoCode.normalize(prefix);
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(normalizedPrefix).append('-');
            for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (getterPromoCode.getByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный промокод с префиксом " + normalizedPrefix);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static String crop(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
