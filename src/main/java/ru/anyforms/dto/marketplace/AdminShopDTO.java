package ru.anyforms.dto.marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AdminShopDTO {
    private UUID id;
    private String slug;
    private String name;
    private Boolean active;
    private String supportTelegram;
    private String amoContactValue;
    private String tagline;
    private String description;
    private String logoUrl;
    private String logoMarkUrl;
    private String faviconUrl;
    private String heroImageUrl;
    private String heroImageAlt;
    private String heroVideoUrl;
    private String headerColor;
    private String accentColor;
    private String backgroundColor;
    private LocalDateTime createdAt;
    private boolean defaultShop;
}
