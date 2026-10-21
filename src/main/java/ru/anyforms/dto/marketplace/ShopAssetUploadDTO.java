package ru.anyforms.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Presigned URL для загрузки логотипа/фото/видео магазина в S3")
public record ShopAssetUploadDTO(
        @Schema(description = "URL для PUT-запроса с файлом, живёт 30 минут") String uploadUrl,
        @Schema(description = "Ключ объекта в бакете") String key,
        @Schema(description = "Публичная ссылка на файл — её сохраняют в настройках магазина") String publicUrl) {
}
