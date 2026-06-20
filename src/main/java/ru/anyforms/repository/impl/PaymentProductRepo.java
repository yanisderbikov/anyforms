package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PaymentProduct;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface PaymentProductRepo extends JpaRepository<PaymentProduct, UUID> {
    Optional<PaymentProduct> findByCode(String code);
    List<PaymentProduct> findByActiveIsTrueOrderByPriceKopecksAsc();
}
