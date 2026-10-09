package ru.anyforms.model.promo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_popup_lead")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PromoPopupLead {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "popup_id", nullable = false)
    private UUID popupId;

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "phone_last10", length = 10)
    private String phoneLast10;

    @Column(name = "shop_slug", nullable = false, length = 64)
    private String shopSlug;

    @Column(name = "consent_personal_data")
    private Boolean consentPersonalData;

    @Column(name = "consent_advertising")
    private Boolean consentAdvertising;

    @Column(name = "consent_version", length = 32)
    private String consentVersion;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "page_url", length = 1024)
    private String pageUrl;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "utm_content")
    private String utmContent;

    @Column(name = "utm_term")
    private String utmTerm;

    @Column(name = "amo_lead_id")
    private Long amoLeadId;

    @Column(name = "order_id")
    private Long orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
