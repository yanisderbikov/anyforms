package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Файл кастомной позиции: id (для удаления) + presigned URL + имя. */
@Schema(description = "Файл кастомной позиции")
public record CustomProductFileDTO(Long id, String url, String filename) {
}
