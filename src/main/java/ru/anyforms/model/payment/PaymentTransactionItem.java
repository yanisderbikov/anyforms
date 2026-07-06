package ru.anyforms.model.payment;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Позиция корзины — снимок товара на момент создания платежа маркетплейса.
 * Цена фиксируется в копейках (серверная цена, а не присланная клиентом).
 */
@Entity
@Table(name = "payment_transaction_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "transaction")
@EqualsAndHashCode(exclude = "transaction")
public class PaymentTransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PaymentTransaction transaction;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "price_kopecks", nullable = false)
    private Long priceKopecks;

    @Column(nullable = false)
    private Integer quantity;
}
