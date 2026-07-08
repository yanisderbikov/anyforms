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

    private static final String GUIDE_SUBJECT = "Гайд - Как продавать сложный продукт через короткие видео";
    private static final String COURSE_SUBJECT = "Ваш предзаказ курса anyforms оформлен — «Самостоятельное изучение»";
    private static final String COURSE_PERSONAL_SUBJECT = "Ваш предзаказ курса anyforms оформлен — «Личное ведение»";

    @Value("${product.guide.url}")
    private String guideUrl;
    @Value("${product.course.url}")
    private String courseUrl;

    @Override
    public RenderedEmail render(String productCode) {
        return switch (productCode) {
            case PaymentProduct.CODE_GUIDE -> new RenderedEmail(GUIDE_SUBJECT, EmailTemplate.getGuideEmail(guideUrl));
            case PaymentProduct.CODE_COURSE ->
                    new RenderedEmail(COURSE_SUBJECT, EmailTemplate.getCourseEmail(courseUrl));
            case PaymentProduct.CODE_COURSE_PERSONAL ->
                    new RenderedEmail(COURSE_PERSONAL_SUBJECT, EmailTemplate.getCoursePersonalEmail(courseUrl));
            default -> throw new IllegalArgumentException("Нет шаблона письма для продукта: " + productCode);
        };
    }
}
