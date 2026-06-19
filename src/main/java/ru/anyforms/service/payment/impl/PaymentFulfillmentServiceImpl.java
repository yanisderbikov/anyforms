package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.email.EmailTemplate;
import ru.anyforms.service.payment.PaymentFulfillmentService;
import ru.anyforms.service.task.TaskAdder;

/**
 * Выдача продукта после успешной оплаты. Сейчас реализован гайд: ставит таску с письмом,
 * содержащим ссылку на гайд. Курс и комбо добавляются ветками switch ниже.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class PaymentFulfillmentServiceImpl implements PaymentFulfillmentService {

    private static final String GUIDE_SUBJECT = "Ваш гайд anyforms";

    private final TaskAdder taskAdder;

    @Value("${product.guide.url}")
    private String guideUrl;

    @Override
    public void fulfill(PaymentTransaction transaction) {
        PaymentProduct product = transaction.getProduct();
        switch (product) {
            case GUIDE -> fulfillGuide(transaction);
            default -> log.warn("Нет реализации выдачи для продукта {} (транзакция {})",
                    product, transaction.getId());
        }
    }

    private void fulfillGuide(PaymentTransaction transaction) {
        EmailTaskPayload payload = EmailTaskPayload.builder()
                .to(transaction.getEmail())
                .subject(GUIDE_SUBJECT)
                .html(EmailTemplate.getGuideEmail(guideUrl))
                .build();
        taskAdder.addTask(payload);
        log.info("Поставлена таска на отправку гайда на {} (транзакция {})",
                transaction.getEmail(), transaction.getId());
    }
}
