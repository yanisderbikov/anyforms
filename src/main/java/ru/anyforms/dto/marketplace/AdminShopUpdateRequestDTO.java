package ru.anyforms.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Изменение магазина витрины; slug не меняется")
public class AdminShopUpdateRequestDTO extends ShopBrandingRequestDTO {

    @NotBlank(message = "Укажите название магазина")
    @Size(max = 255, message = "Название — не длиннее 255 символов")
    @Schema(description = "Название магазина", required = true, example = "di_gips")
    private String name;

    @Size(max = 64, message = "Username бота — не длиннее 64 символов")
    @Schema(description = "Телеграм-бот поддержки без @; пусто — общий AnyFormsBot", example = "di_gips_supportbot")
    private String supportTelegram;

    @Size(max = 64, message = "Значение amo — не длиннее 64 символов")
    @Schema(description = "Значение поля «Магазин» контакта amoCRM; пусто — поле не заполняется", example = "di_gips")
    private String amoContactValue;

    @NotNull(message = "Укажите, включён ли магазин")
    @Schema(description = "Выключенный магазин скрыт с витрины и в селектах", required = true, example = "true")
    private Boolean active;
}
