package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

public interface CourseAmoLeadService {

    void pushCoursePurchase(PaymentTransaction transaction);
}
