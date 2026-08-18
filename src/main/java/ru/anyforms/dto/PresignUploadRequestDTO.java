package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Запрос подписанного URL для прямой загрузки файла из браузера в S3. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос presigned URL для прямой загрузки в S3")
public class PresignUploadRequestDTO {

    @NotBlank(message = "Не указано имя файла")
    @Schema(description = "Имя файла — от него берётся только расширение, ключ генерирует сервер")
    private String filename;

    @Schema(description = "MIME-тип файла; входит в подпись, PUT должен идти с тем же Content-Type")
    private String contentType;
}
