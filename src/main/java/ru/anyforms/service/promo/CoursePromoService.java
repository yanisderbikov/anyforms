package ru.anyforms.service.promo;

import ru.anyforms.dto.promo.CoursePromoOutcome;
import ru.anyforms.dto.promo.PromoPopupAfterPurchaseRequest;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PromoCode;

import java.util.Optional;

public interface CoursePromoService {

    CoursePromoOutcome afterPurchase(PromoPopupAfterPurchaseRequest request, String ip);

    Optional<String> previewRejection(PromoCode promo, PaymentProduct product, PromoClient client);

    Optional<String> checkoutRejection(PromoCode promo, PaymentProduct product, PromoClient client);
}
