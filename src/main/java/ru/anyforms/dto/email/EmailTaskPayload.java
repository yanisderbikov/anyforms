package ru.anyforms.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тело таски на отправку письма о покупке. Намеренно НЕ содержит готового письма —
 * только адресата и код продукта. Тему и HTML рендерит раннер в момент исполнения таски
 * по шаблону, который соответствует продукту (гайд → шаблон гайда, курс → шаблон курса).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailTaskPayload {
    private String to;
    private String productCode;
}
