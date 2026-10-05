package ru.anyforms.model.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "promo_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PromoCode {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Код в верхнем регистре, например {@code ГАЙД}. */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** Скидка в процентах, 0–100; 0 — промокод только на фиксированную сумму. */
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    /** Фиксированная скидка в копейках, применяется после процента; null — не задана. */
    @Column(name = "discount_amount_kopecks")
    private Long discountAmountKopecks;

    /** Минимальная сумма заказа (до скидок) в копейках; null — без порога. */
    @Column(name = "min_order_kopecks")
    private Long minOrderKopecks;

    @Column(nullable = false)
    private Boolean active;

    /** Начало действия; null — без нижней границы. */
    @Column(name = "valid_from")
    private Instant validFrom;

    /** Окончание действия (исключительно); null — бессрочно. */
    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "popup_id")
    private UUID popupId;

    @Column(name = "shop_slug", length = 64)
    private String shopSlug;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "owner_phone_last10", length = 10)
    private String ownerPhoneLast10;

    @Column(name = "owner_device_id", length = 64)
    private String ownerDeviceId;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "first_order_only", nullable = false)
    private Boolean firstOrderOnly = Boolean.FALSE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Нормализация пользовательского ввода: пробелы по краям и верхний регистр. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isCurrentlyValid() {
        Instant now = Instant.now();
        return Boolean.TRUE.equals(active)
                && (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }

    public boolean isPersonal() {
        return ownerEmail != null || ownerPhoneLast10 != null;
    }

    public boolean belongsTo(String email, String phoneLast10) {
        if (!isPersonal()) {
            return true;
        }
        boolean emailMatches = ownerEmail != null && email != null && ownerEmail.equalsIgnoreCase(email.trim());
        boolean phoneMatches = ownerPhoneLast10 != null && ownerPhoneLast10.equals(phoneLast10);
        return emailMatches || phoneMatches;
    }

    public boolean allowedInShop(String shopSlug) {
        return this.shopSlug == null || this.shopSlug.equals(shopSlug);
    }

    public boolean isFirstOrderOnly() {
        return Boolean.TRUE.equals(firstOrderOnly);
    }

    public boolean hasAmountDiscount() {
        return discountAmountKopecks != null && discountAmountKopecks > 0;
    }

    /** Порог считается по сумме заказа до применения скидок. */
    public boolean meetsMinOrder(long orderTotalKopecks) {
        return minOrderKopecks == null || orderTotalKopecks >= minOrderKopecks;
    }
}
