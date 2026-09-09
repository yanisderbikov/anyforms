package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание/обновление группы дрип-кампании. Воронка и статус — оба или ни одного")
public class BotGroupRequestDTO {

    @NotBlank(message = "Название группы обязательно")
    @Size(max = 128, message = "Название не длиннее 128 символов")
    private String name;

    @Positive(message = "ID воронки — положительное число")
    @Schema(description = "ID воронки в amoCRM; null — не задана", example = "10557858")
    private Long pipelineId;

    @Positive(message = "ID статуса — положительное число")
    @Schema(description = "ID статуса (колонки) в amoCRM; null — не задан", example = "86451842")
    private Long statusId;

    @Schema(description = "Участвует ли группа в прогоне; null при создании = true")
    private Boolean enabled;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Начало окна — время HH:mm")
    @Schema(description = "Окно отправки по Москве, начало HH:mm; вместе с sendTo или оба пустые", example = "10:00")
    private String sendFrom;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Конец окна — время HH:mm")
    @Schema(description = "Окно отправки по Москве, конец HH:mm (исключительно)", example = "17:00")
    private String sendTo;
}
