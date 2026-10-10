package ru.anyforms.service.promo;

import ru.anyforms.dto.promo.PromoPopupCreateUpdateRequest;
import ru.anyforms.dto.promo.PromoPopupDTO;
import ru.anyforms.dto.promo.PromoPopupLeadDTO;
import ru.anyforms.dto.promo.PromoPopupOptionsDTO;

import java.util.List;
import java.util.UUID;

public interface PromoPopupAdminService {

    List<PromoPopupDTO> list();

    PromoPopupDTO create(PromoPopupCreateUpdateRequest request);

    PromoPopupDTO update(UUID id, PromoPopupCreateUpdateRequest request);

    void delete(UUID id);

    List<PromoPopupLeadDTO> leads(UUID popupId, int limit);

    PromoPopupOptionsDTO options();
}
