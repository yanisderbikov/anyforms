package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.amo.AmoPipelineInfo;

import java.util.List;

@Schema(description = "Воронка amoCRM со статусами — для выбора по имени; в настройках хранятся id")
public record PipelineDTO(@Schema(description = "pipeline_id") Long id, String name, List<PipelineStatusDTO> statuses) {
    public static PipelineDTO from(AmoPipelineInfo pipeline) {
        return new PipelineDTO(pipeline.id(), pipeline.name(),
                pipeline.statuses().stream().map(PipelineStatusDTO::from).toList());
    }
}
