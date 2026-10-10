package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Ячейка хранения позиции; пустая строка или null — очистить. */
@Data
@Schema(description = "Ячейка хранения материалов позиции")
public class StorageCellUpdateRequestDTO {

    @Schema(description = "Где хранятся мастер-модель и материалы", example = "Стеллаж 2, полка 3, коробка «Иванов»")
    private String storageCell;
}
