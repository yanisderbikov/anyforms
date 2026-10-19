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
    private LocalDateTime createdAt;
    private boolean defaultShop;
}
