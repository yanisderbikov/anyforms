package ru.anyforms.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Создание магазина витрины")
public class AdminShopCreateRequestDTO extends ShopBrandingRequestDTO {

    @NotBlank(message = "Укажите slug — адрес витрины /shop/{slug}")
    @Size(max = 64, message = "Slug — не длиннее 64 символов")
    @Schema(description = "Адрес витрины: строчные латинские буквы, цифры, _ и -", required = true, example = "di_gips")
    private String slug;

    @NotBlank(message = "Укажите название магазина")
    @Size(max = 255, message = "Название — не длиннее 255 символов")
    @Schema(description = "Название магазина (витрина, письма, селекты админки)", required = true, example = "di_gips")
    private String name;

    @Size(max = 64, message = "Username бота — не длиннее 64 символов")
    @Schema(description = "Телеграм-бот поддержки без @; пусто — общий AnyFormsBot", example = "di_gips_supportbot")
    private String supportTelegram;

    @Size(max = 64, message = "Значение amo — не длиннее 64 символов")
    @Schema(description = "Значение поля «Магазин» контакта amoCRM; пусто — поле не заполняется", example = "di_gips")
    private String amoContactValue;
}
