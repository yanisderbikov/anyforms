package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Новая запись лога производства. */
@Data
@Schema(description = "Новая запись лога производства позиции")
public class CustomProductLogRequestDTO {

    @NotBlank
    @Schema(description = "Что сделали", example = "Отлили 20 шт, отдали на покраску")
    private String text;
}
