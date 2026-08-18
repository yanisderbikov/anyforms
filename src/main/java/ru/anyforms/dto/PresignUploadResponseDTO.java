package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Подписанный URL: файл уходит из браузера сразу в бакет (PUT по uploadUrl), key сохраняется у нас. */
@Schema(description = "Presigned URL для прямой загрузки в S3")
public record PresignUploadResponseDTO(
        @Schema(description = "URL для PUT-запроса с файлом, живёт 30 минут") String uploadUrl,
        @Schema(description = "Ключ объекта в бакете") String key) {
}
