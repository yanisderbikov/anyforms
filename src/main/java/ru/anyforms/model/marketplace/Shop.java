package ru.anyforms.model.marketplace;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Магазин витрины. anyforms — основной магазин и общая витрина /shop,
 * партнёрские магазины (af_pastry) получают свою страницу /shop/{slug}.
 */
@Entity
@Table(name = "shop")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Shop {

    /** Магазин по умолчанию: общая витрина /shop и владелец товаров без явного магазина. */
    public static final String DEFAULT_SLUG = "anyforms";

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NonNull
    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @NonNull
    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
