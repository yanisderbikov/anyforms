package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Подсказка клиента (для предзаполнения нового заказа по существующим заказам). */
@Schema(description = "Подсказка клиента")
public record ContactSuggestionDTO(
        String contactName,
        String contactPhone,
        String pvzSdekCity,
        String pvzSdekStreet
) {
}
