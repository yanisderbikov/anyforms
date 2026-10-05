package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.promo.PromoPopupCreateUpdateRequest;
import ru.anyforms.dto.promo.PromoPopupDTO;
import ru.anyforms.dto.promo.PromoPopupLeadDTO;
import ru.anyforms.dto.promo.PromoPopupOptionsDTO;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterPromoPopupView;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.PopupViewStats;
import ru.anyforms.repository.PromoPopupDeleter;
import ru.anyforms.repository.SaverPromoPopup;
import ru.anyforms.service.promo.PromoPopupAdminService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PromoPopupAdminServiceImpl implements PromoPopupAdminService {

    static final int MAX_LEADS_LIMIT = 500;
    static final int DEFAULT_TASK_DEADLINE_MINUTES = 60;

    private final GetterPromoPopup getterPromoPopup;
    private final SaverPromoPopup saverPromoPopup;
    private final PromoPopupDeleter promoPopupDeleter;
    private final GetterPromoPopupLead getterPromoPopupLead;
    private final GetterPromoCode getterPromoCode;
    private final GetterPromoPopupView getterPromoPopupView;
    private final GetterTransaction getterTransaction;

    @Value("${promo.popup.consent-version}")
    private String consentVersion;

    @Override
    public List<PromoPopupDTO> list() {
        Map<UUID, Long> leads = getterPromoPopupLead.countByPopup();
        Map<UUID, PopupViewStats> views = getterPromoPopupView.statsByPopup();
        Map<UUID, Long> used = getterTransaction.countSucceededByPopup();
        return getterPromoPopup.getAll().stream()
                .map(p -> {
                    PromoCode promo = promoOf(p);
                    PopupViewStats view = views.getOrDefault(p.getId(), new PopupViewStats(0, 0));
                    long usedCount = p.isPublicCode()
                            ? (promo == null ? 0 : getterTransaction.countSucceededByPromoCode(promo.getCode()))
                            : used.getOrDefault(p.getId(), 0L);
                    return PromoPopupDTO.from(p, promo, new PromoPopupDTO.Stats(
                            leads.getOrDefault(p.getId(), 0L), view.views(), view.devices(), usedCount));
                })
                .toList();
    }

    @Override
    @Transactional
    public PromoPopupDTO create(PromoPopupCreateUpdateRequest request) {
        PromoPopup popup = new PromoPopup();
        apply(popup, request);
        PromoPopup saved = saverPromoPopup.save(popup);
        return PromoPopupDTO.from(saved, promoOf(saved), PromoPopupDTO.Stats.EMPTY);
    }

    @Override
    @Transactional
    public PromoPopupDTO update(UUID id, PromoPopupCreateUpdateRequest request) {
        PromoPopup popup = getterPromoPopup.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Попап не найден"));
        long leadsCount = getterPromoPopupLead.countByPopup().getOrDefault(id, 0L);
        if (leadsCount > 0 && request.getPopupType() != popup.getPopupType()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "По попапу уже выданы персональные коды — тип менять нельзя. Создайте новый попап.");
        }
        apply(popup, request);
        PromoPopup saved = saverPromoPopup.save(popup);
        return PromoPopupDTO.from(saved, promoOf(saved), new PromoPopupDTO.Stats(leadsCount, 0, 0, 0));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (getterPromoPopup.getById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Попап не найден");
        }
        if (getterPromoPopupLead.countByPopup().getOrDefault(id, 0L) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "По попапу уже выданы коды — удалить нельзя, выключите его.");
        }
        promoPopupDeleter.deleteById(id);
    }

    @Override
    public List<PromoPopupLeadDTO> leads(UUID popupId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LEADS_LIMIT));
        return getterPromoPopupLead.getRecent(popupId, safeLimit).stream()
                .map(PromoPopupLeadDTO::from)
                .toList();
    }

    @Override
    public PromoPopupOptionsDTO options() {
        List<PromoPopupOptionsDTO.Option> users = Arrays.stream(AmoTaskResponsibleUser.values())
                .filter(u -> u.getResponsibleUserId() != null)
                .map(u -> new PromoPopupOptionsDTO.Option(u.getResponsibleUserId(), u.getName()))
                .toList();
        List<PromoPopupOptionsDTO.Option> taskTypes = Arrays.stream(AmoTaskId.values())
                .filter(t -> t.getTaskId() != null)
                .map(t -> new PromoPopupOptionsDTO.Option(t.getTaskId(), t.getName()))
                .toList();
        return new PromoPopupOptionsDTO(users, taskTypes, consentVersion);
    }

    private PromoCode promoOf(PromoPopup popup) {
        return popup.getPromoCodeId() == null
                ? null
                : getterPromoCode.getById(popup.getPromoCodeId()).orElse(null);
    }

    private void apply(PromoPopup popup, PromoPopupCreateUpdateRequest request) {
        Instant validFrom = parseInstant(request.getValidFrom(), "validFrom");
        Instant validUntil = parseInstant(request.getValidUntil(), "validUntil");
        if (validFrom != null && validUntil != null && !validFrom.isBefore(validUntil)) {
            throw badRequest("Начало показа должно быть раньше окончания");
        }
        String shopSlug = request.getShopSlug() == null || request.getShopSlug().isBlank()
                ? Shop.DEFAULT_SLUG
                : request.getShopSlug().trim();

        popup.setPopupType(request.getPopupType());
        popup.setName(request.getName().trim());
        popup.setActive(request.getActive());
        popup.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        popup.setShopSlug(shopSlug);
        popup.setTitle(request.getTitle().trim());
        popup.setDescription(blankToNull(request.getDescription()));
        popup.setButtonText(request.getButtonText().trim());
        popup.setDelaySeconds(request.getDelaySeconds());
        popup.setRepeatAfterHours(request.getRepeatAfterHours());
        popup.setMaxShows(request.getMaxShows());
        popup.setValidFrom(validFrom);
        popup.setValidUntil(validUntil);
        popup.setHideForKnownContacts(request.getHideForKnownContacts() != null
                ? request.getHideForKnownContacts()
                : request.getPopupType() != PromoPopupType.PUBLIC_CODE);

        switch (request.getPopupType()) {
            case PUBLIC_CODE -> applyPublicCode(popup, request, shopSlug);
            case UNIQUE_CODE -> applyGeneratedCode(popup, request, false);
            case CONTACT -> applyGeneratedCode(popup, request, true);
        }
    }

    private void applyPublicCode(PromoPopup popup, PromoPopupCreateUpdateRequest request, String shopSlug) {
        if (request.getPromoCodeId() == null) {
            throw badRequest("Выберите промокод, который покажет попап");
        }
        PromoCode promo = getterPromoCode.getById(request.getPromoCodeId())
                .orElseThrow(() -> badRequest("Промокод не найден — возможно, его удалили"));
        if (promo.getPopupId() != null || promo.isPersonal()) {
            throw badRequest("Персональный промокод нельзя показывать всем посетителям");
        }
        if (!promo.allowedInShop(shopSlug)) {
            throw badRequest("Промокод " + promo.getCode() + " не действует в магазине " + shopSlug);
        }
        popup.setPromoCodeId(promo.getId());
        popup.setSuccessTitle(null);
        popup.setSuccessText(null);
        popup.setDiscountPercent(null);
        popup.setDiscountAmountKopecks(null);
        popup.setMinOrderKopecks(null);
        popup.setCodePrefix(null);
        popup.setCodeTtlDays(null);
        popup.setFirstOrderOnly(false);
        popup.setAmoResponsibleUserId(null);
        popup.setAmoTaskTypeId(null);
        popup.setAmoTaskDeadlineMinutes(DEFAULT_TASK_DEADLINE_MINUTES);
    }

    private void applyGeneratedCode(PromoPopup popup, PromoPopupCreateUpdateRequest request, boolean withAmo) {
        if (withAmo && (request.getSuccessTitle() == null || request.getSuccessTitle().isBlank())) {
            throw badRequest("Заполните заголовок экрана с кодом");
        }
        if (request.getDiscountPercent() == null) {
            throw badRequest("Укажите процент скидки (0 — скидка только суммой)");
        }
        boolean hasAmount = request.getDiscountAmountKopecks() != null && request.getDiscountAmountKopecks() > 0;
        if (request.getDiscountPercent() == 0 && !hasAmount) {
            throw badRequest("Скидка пустая: укажите процент или сумму");
        }
        if (request.getCodePrefix() == null || request.getCodePrefix().isBlank()) {
            throw badRequest("Укажите префикс кода");
        }
        if (request.getCodeTtlDays() == null) {
            throw badRequest("Укажите срок действия кода");
        }
        if (withAmo && (request.getAmoResponsibleUserId() == null || request.getAmoTaskTypeId() == null)) {
            throw badRequest("Выберите ответственного и тип задачи в amoCRM");
        }
        popup.setPromoCodeId(null);
        popup.setSuccessTitle(withAmo ? request.getSuccessTitle().trim() : null);
        popup.setSuccessText(withAmo ? blankToNull(request.getSuccessText()) : null);
        popup.setDiscountPercent(request.getDiscountPercent());
        popup.setDiscountAmountKopecks(request.getDiscountAmountKopecks());
        popup.setMinOrderKopecks(request.getMinOrderKopecks());
        popup.setCodePrefix(PromoCode.normalize(request.getCodePrefix()));
        popup.setCodeTtlDays(request.getCodeTtlDays());
        popup.setFirstOrderOnly(!Boolean.FALSE.equals(request.getFirstOrderOnly()));
        popup.setAmoResponsibleUserId(withAmo ? request.getAmoResponsibleUserId() : null);
        popup.setAmoTaskTypeId(withAmo ? request.getAmoTaskTypeId() : null);
        popup.setAmoTaskDeadlineMinutes(!withAmo || request.getAmoTaskDeadlineMinutes() == null
                ? DEFAULT_TASK_DEADLINE_MINUTES
                : request.getAmoTaskDeadlineMinutes());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw badRequest("Некорректная дата в поле " + field);
        }
    }
}
