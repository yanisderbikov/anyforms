package ru.anyforms.service.email.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.service.email.EmailTemplate;
import ru.anyforms.service.email.PurchaseEmailRenderer;

@Slf4j
@Component
class PurchaseEmailRendererImpl implements PurchaseEmailRenderer {

    private static final String GUIDE_SUBJECT = "Ваш гайд anyforms";
    private static final String COURSE_SUBJECT = "Ваш курс anyforms";

    @Value("${product.guide.url}")
    private String guideUrl;
    @Value("${product.course.url}")
    private String courseUrl;

    @Override
    public RenderedEmail render(String productCode) {
        return switch (productCode) {
            case PaymentProduct.CODE_GUIDE -> new RenderedEmail(GUIDE_SUBJECT, EmailTemplate.getGuideEmail(guideUrl));
            case PaymentProduct.CODE_COURSE -> new RenderedEmail(COURSE_SUBJECT, EmailTemplate.getCourseEmail(courseUrl));
            default -> throw new IllegalArgumentException("Нет шаблона письма для продукта: " + productCode);
        };
    }
}
