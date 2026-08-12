package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.PromoCodeCreateUpdateRequest;
import ru.anyforms.dto.payment.PromoCodeDTO;

import java.util.List;
import java.util.UUID;

public interface PromoCodeAdminService {

    /** Промокоды без протухших: срок не истёк или бессрочные. Новые сверху. */
    List<PromoCodeDTO> listNotExpired();

    PromoCodeDTO create(PromoCodeCreateUpdateRequest request);

    PromoCodeDTO update(UUID id, PromoCodeCreateUpdateRequest request);

    void delete(UUID id);
}
