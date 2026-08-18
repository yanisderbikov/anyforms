package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Загруженный напрямую в S3 файл позиции: ключ из presign + исходное имя. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Файл, загруженный в S3 по presigned URL")
public class CustomProductFileRefDTO {

    @NotBlank(message = "Не указан ключ файла")
    @Schema(description = "Ключ объекта, выданный ручкой presign этой позиции")
    private String key;

    @Schema(description = "Оригинальное имя файла")
    private String filename;
}
