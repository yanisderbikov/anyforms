package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Справочники для формы попапа")
public record PromoPopupOptionsDTO(
        List<Option> responsibleUsers,
        List<Option> taskTypes,
        String consentVersion
) {
    public record Option(Long id, String name) {
    }
}
