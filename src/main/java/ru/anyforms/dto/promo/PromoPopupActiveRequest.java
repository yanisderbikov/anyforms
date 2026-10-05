package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Какой попап показать посетителю витрины")
public class PromoPopupActiveRequest {

    @Size(max = 64)
    @Schema(description = "Slug магазина; пусто — anyforms")
    private String shop;

    @Size(max = 64)
    @Schema(description = "ID устройства из браузера")
    private String deviceId;

    @Size(max = 32)
    @Schema(description = "Телефон, который посетитель уже вводил на сайте")
    private String phone;

    @Size(max = 255)
    @Schema(description = "Почта, которую посетитель уже вводил на сайте")
    private String email;
}
