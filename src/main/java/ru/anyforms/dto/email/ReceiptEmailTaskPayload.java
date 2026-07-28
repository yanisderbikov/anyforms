package ru.anyforms.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тело таски на письмо со ссылкой на чек Юкассы. Email и ссылку админ вставляет
 * вручную в админке («Чеки Юра»), письмо рендерит раннер в момент исполнения таски.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReceiptEmailTaskPayload {
    private String to;
    private String link;
}
