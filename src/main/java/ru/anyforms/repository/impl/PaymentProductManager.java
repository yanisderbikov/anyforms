package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.repository.GetterPaymentProduct;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class PaymentProductManager implements GetterPaymentProduct {

    private final PaymentProductRepo paymentProductRepo;

    @Override
    public Optional<PaymentProduct> getByCode(String code) {
        try {
            return paymentProductRepo.findByCode(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentProduct> getAllActive() {
        try {
            return paymentProductRepo.findByActiveIsTrueOrderByPriceKopecksAsc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
