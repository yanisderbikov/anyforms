package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Создание под-заказа без CRM (с нуля). Все поля необязательны. */
@Data
@Schema(description = "Создание под-заказа без CRM")
public class CustomOrderCreateRequestDTO {

    @Schema(description = "ФИО клиента")
    private String contactName;

    @Schema(description = "Телефон")
    private String contactPhone;

    @Schema(description = "Город (ПВЗ СДЭК)")
    private String pvzSdekCity;

    @Schema(description = "Улица/адрес (ПВЗ СДЭК)")
    private String pvzSdekStreet;
}
