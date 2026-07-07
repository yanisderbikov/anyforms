package ru.anyforms.dto.amo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Товар каталога АМО для выпадающего списка в админке маркетплейса. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmoProductDTO {
    private Long id;
    private String name;
}
