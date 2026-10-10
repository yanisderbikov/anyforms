package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Получение одноразового кода из попапа")
public class PromoPopupIssueRequest {

    @NotBlank(message = "Не удалось определить устройство. Обновите страницу.")
    @Size(max = 64)
    private String deviceId;

    @Size(max = 32)
    @Schema(description = "Телефон, который посетитель уже вводил на сайте")
    private String phone;

    @Size(max = 255)
    @Schema(description = "Почта, которую посетитель уже вводил на сайте")
    private String email;

    @Size(max = 1024)
    private String pageUrl;

    @Size(max = 255)
    private String utmSource;

    @Size(max = 255)
    private String utmMedium;

    @Size(max = 255)
    private String utmCampaign;

    @Size(max = 255)
    private String utmContent;

    @Size(max = 255)
    private String utmTerm;

    @Schema(description = "Поле-ловушка для ботов, у людей всегда пустое")
    private String website;
}
