package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.ManualRunStatus;

@Schema(description = "Ручной массовый запуск бота: параметры и живые счётчики")
public record ManualRunDTO(
        long id,
        ManualRunStatus status,
        @Schema(description = "ISO-8601 instant") String startedAt,
        @Schema(description = "ISO-8601 instant; null — ещё идёт") String finishedAt,
        Long pipelineId,
        String pipelineName,
        Long statusId,
        String statusName,
        Long botId,
        String botName,
        @Schema(description = "Тег-фильтр; null — все лиды статуса") String tagName,
        @Schema(description = "Фильтр по полю «Розница»: true — только розница, false — только не розница, null — любые") Boolean retail,
        @Schema(description = "Лидов найдено в статусе; -1 — ещё не посчитано") int total,
        @Schema(description = "Бот запущен") int sent,
        @Schema(description = "Пропущено: бот этому лиду уже уходил раньше") int skipped,
        @Schema(description = "Запуск не удался") int failed,
        String error,
        String startedBy
) {
}
