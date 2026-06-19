package ru.anyforms.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Тело таски на отправку письма. Сериализуется в payload {@link ru.anyforms.model.task.Task}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailTaskPayload {
    private String to;
    private String subject;
    private String html;
}
