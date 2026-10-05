package ru.anyforms.model.promo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_popup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PromoPopup {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "popup_type", nullable = false, length = 16)
    private PromoPopupType popupType;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "shop_slug", nullable = false, length = 64)
    private String shopSlug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "button_text", nullable = false, length = 64)
    private String buttonText;

    @Column(name = "success_title", length = 200)
    private String successTitle;

    @Column(name = "success_text", columnDefinition = "TEXT")
    private String successText;

    @Column(name = "delay_seconds", nullable = false)
    private Integer delaySeconds;

    @Column(name = "repeat_after_hours", nullable = false)
    private Integer repeatAfterHours;

    @Column(name = "max_shows")
    private Integer maxShows;

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(name = "hide_for_known_contacts", nullable = false)
    private Boolean hideForKnownContacts;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_amount_kopecks")
    private Long discountAmountKopecks;

    @Column(name = "min_order_kopecks")
    private Long minOrderKopecks;

    @Column(name = "code_prefix", length = 16)
    private String codePrefix;

    @Column(name = "code_ttl_days")
    private Integer codeTtlDays;

    @Column(name = "first_order_only", nullable = false)
    private Boolean firstOrderOnly;

    @Column(name = "amo_responsible_user_id")
    private Long amoResponsibleUserId;

    @Column(name = "amo_task_type_id")
    private Long amoTaskTypeId;

    @Column(name = "amo_task_deadline_minutes", nullable = false)
    private Integer amoTaskDeadlineMinutes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isPublicCode() {
        return popupType == PromoPopupType.PUBLIC_CODE;
    }

    public boolean isUniqueCode() {
        return popupType == PromoPopupType.UNIQUE_CODE;
    }

    public boolean isContact() {
        return popupType == PromoPopupType.CONTACT;
    }

    public boolean issuesCodes() {
        return isContact() || isUniqueCode();
    }

    public boolean hidesKnownContacts() {
        return !Boolean.FALSE.equals(hideForKnownContacts);
    }

    public boolean isLiveAt(Instant now) {
        return Boolean.TRUE.equals(active)
                && (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }
}
