package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.PromoCodeCreateUpdateRequest;
import ru.anyforms.dto.payment.PromoCodeDTO;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.PromoCodeDeleter;
import ru.anyforms.repository.SaverPromoCode;
import ru.anyforms.service.payment.PromoCodeAdminService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PromoCodeAdminServiceImpl implements PromoCodeAdminService {

    private final GetterPromoCode getterPromoCode;
    private final SaverPromoCode saverPromoCode;
    private final PromoCodeDeleter promoCodeDeleter;

    @Override
    public List<PromoCodeDTO> listNotExpired() {
        return getterPromoCode.getAllNotExpired(Instant.now()).stream()
                .map(PromoCodeDTO::from)
                .toList();
    }

    @Override
    public PromoCodeDTO create(PromoCodeCreateUpdateRequest request) {
        String code = normalizedCode(request);
        if (getterPromoCode.getByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Промокод " + code + " уже существует.");
        }
        PromoCode promo = new PromoCode();
        applyRequest(promo, request, code);
        return PromoCodeDTO.from(saverPromoCode.save(promo));
    }

    @Override
    public PromoCodeDTO update(UUID id, PromoCodeCreateUpdateRequest request) {
        PromoCode promo = getterPromoCode.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Промокод не найден: " + id));
        String code = normalizedCode(request);
        Optional<PromoCode> sameCode = getterPromoCode.getByCode(code);
        if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Промокод " + code + " уже существует.");
        }
        applyRequest(promo, request, code);
        return PromoCodeDTO.from(saverPromoCode.save(promo));
    }

    @Override
    public void delete(UUID id) {
        if (getterPromoCode.getById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Промокод не найден: " + id);
        }
        promoCodeDeleter.deleteById(id);
    }

    private String normalizedCode(PromoCodeCreateUpdateRequest request) {
        String code = PromoCode.normalize(request.getCode());
        if (code == null || code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код промокода пустой.");
        }
        return code;
    }

    private void applyRequest(PromoCode promo, PromoCodeCreateUpdateRequest request, String code) {
        if (request.getDiscountPercent() == 0
                && (request.getDiscountAmountKopecks() == null || request.getDiscountAmountKopecks() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Скидка пустая: укажите процент или фиксированную сумму.");
        }
        Instant validFrom = parseInstant(request.getValidFrom(), "validFrom");
        Instant validUntil = parseInstant(request.getValidUntil(), "validUntil");
        if (validFrom != null && validUntil != null && !validFrom.isBefore(validUntil)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Начало действия должно быть раньше окончания.");
        }
        promo.setCode(code);
        promo.setDiscountPercent(request.getDiscountPercent());
        promo.setDiscountAmountKopecks(request.getDiscountAmountKopecks());
        promo.setMinOrderKopecks(request.getMinOrderKopecks());
        promo.setActive(request.getActive());
        promo.setValidFrom(validFrom);
        promo.setValidUntil(validUntil);
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некорректная дата " + field + ": " + value + " (нужен ISO-8601, например 2026-08-31T21:00:00Z)");
        }
    }
}
