package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Получение персонального промокода из попапа")
public class PromoPopupClaimRequest {

    @NotBlank(message = "Укажите почту")
    @Email(message = "Проверьте адрес почты")
    @Size(max = 255, message = "Слишком длинный адрес почты")
    private String email;

    @NotBlank(message = "Укажите телефон")
    @Size(max = 32, message = "Слишком длинный номер телефона")
    private String phone;

    @NotNull(message = "Нужно согласие на обработку персональных данных")
    @AssertTrue(message = "Нужно согласие на обработку персональных данных")
    private Boolean consentPersonalData;

    @NotNull(message = "Нужно согласие на получение рекламы")
    @AssertTrue(message = "Нужно согласие на получение рекламы")
    private Boolean consentAdvertising;

    @NotBlank(message = "Не указана версия согласия")
    @Size(max = 32)
    private String consentVersion;

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

    @Size(max = 64)
    @Schema(description = "ID устройства из браузера")
    private String deviceId;

    @Schema(description = "Поле-ловушка для ботов, у людей всегда пустое")
    private String website;
}
