package ru.anyforms.service.promo;

import ru.anyforms.dto.promo.AfterPurchasePromoOutcome;
import ru.anyforms.dto.promo.PromoPopupActiveRequest;
import ru.anyforms.dto.promo.PromoPopupClaimRequest;
import ru.anyforms.dto.promo.PromoPopupAfterPurchaseRequest;
import ru.anyforms.dto.promo.PromoPopupClaimResponse;
import ru.anyforms.dto.promo.PromoPopupIssueRequest;
import ru.anyforms.dto.promo.PublicPromoPopupDTO;

import java.util.Optional;
import java.util.UUID;

public interface PromoPopupPublicService {

    Optional<PublicPromoPopupDTO> getActive(PromoPopupActiveRequest request);

    void recordView(UUID popupId, String deviceId);

    PromoPopupClaimResponse claim(UUID popupId, PromoPopupClaimRequest request, String ip, String userAgent);

    PromoPopupClaimResponse issue(UUID popupId, PromoPopupIssueRequest request, String ip, String userAgent);

    AfterPurchasePromoOutcome afterPurchase(PromoPopupAfterPurchaseRequest request, String ip);
}
