package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Привязка к позиции файлов, загруженных напрямую в S3 по presigned URL. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Файлы, загруженные в S3, для привязки к позиции")
public class ConfirmFilesRequestDTO {

    @Valid
    @NotEmpty(message = "Файлы не переданы")
    @Schema(description = "Ключи и имена загруженных файлов")
    private List<CustomProductFileRefDTO> files;
}
