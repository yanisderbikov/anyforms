package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Запрос на создание заявки из лендинга")
public class LandingLeadRequestDTO {
    @Schema(description = "Название сделки", example = "заявка с лендинга")
    private String leadName;

    @Schema(description = "Имя клиента", example = "Иван")
    private String name;

    @NotBlank
    @Schema(description = "Телефон клиента", required = true, example = "+79991234567")
    private String phone;
}
