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

    /** Бот поддержки по умолчанию — для anyforms и заказов без магазина. */
    public static final String DEFAULT_SUPPORT_TELEGRAM = "AnyFormsBot";

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

    /** Username телеграм-бота поддержки магазина, без @. */
    @Builder.Default
    @Column(name = "support_telegram", nullable = false, length = 64)
    private String supportTelegram = DEFAULT_SUPPORT_TELEGRAM;

    @Column(name = "amo_contact_value", length = 64)
    private String amoContactValue;

    @Column(name = "tagline")
    private String tagline;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "logo_mark_url", length = 1024)
    private String logoMarkUrl;

    @Column(name = "favicon_url", length = 1024)
    private String faviconUrl;

    @Column(name = "hero_image_url", length = 1024)
    private String heroImageUrl;

    @Column(name = "hero_image_alt")
    private String heroImageAlt;

    @Column(name = "hero_video_url", length = 1024)
    private String heroVideoUrl;

    @Column(name = "header_color", length = 7)
    private String headerColor;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
