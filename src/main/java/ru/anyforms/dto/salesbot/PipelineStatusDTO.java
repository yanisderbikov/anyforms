package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.amo.AmoPipelineStatusInfo;

@Schema(description = "Статус (колонка) воронки amoCRM")
public record PipelineStatusDTO(@Schema(description = "status_id") Long id, String name) {
    public static PipelineStatusDTO from(AmoPipelineStatusInfo status) {
        return new PipelineStatusDTO(status.id(), status.name());
    }
}
