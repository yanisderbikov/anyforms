package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Создание под-заказа без CRM (с нуля). Все поля необязательны. */
@Data
@Schema(description = "Создание под-заказа без CRM")
public class CustomOrderCreateRequestDTO {

    @Schema(description = "Имя клиента (необязательно)")
    private String contactName;

    @Schema(description = "Телефон (необязательно)")
    private String contactPhone;
}
