package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Запись лога производства позиции. */
@Schema(description = "Запись лога производства позиции")
public record CustomProductLogDTO(Long id, String text, Instant createdAt) {
}
