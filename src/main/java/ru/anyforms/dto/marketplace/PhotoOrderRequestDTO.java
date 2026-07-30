package ru.anyforms.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Новый порядок фотографий товара: имена файлов из папки товара, первое — главное фото. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Порядок фотографий товара")
public class PhotoOrderRequestDTO {

    @NotEmpty
    @Schema(description = "Имена файлов в нужном порядке")
    private List<String> fileNames;
}
