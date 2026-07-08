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

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    @Column(nullable = false)
    private Boolean active;

    /** Начало действия; null — без нижней границы. */
    @Column(name = "valid_from")
    private Instant validFrom;

    /** Окончание действия (исключительно); null — бессрочно. */
    @Column(name = "valid_until")
    private Instant validUntil;

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
}
