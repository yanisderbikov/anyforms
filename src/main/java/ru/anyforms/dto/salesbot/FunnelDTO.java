package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.model.salesbot.OrderTypeFunnel;

import java.util.Map;

@Schema(description = "Воронка/статус amoCRM, где дрип-кампания ищет лидов типа (order_type_funnel)")
public record FunnelDTO(
        Long id,
        OrderType type,
        @Schema(description = "ID воронки в amoCRM") Long pipelineId,
        @Schema(description = "Название воронки из amoCRM; null — неизвестно") String pipelineName,
        @Schema(description = "ID статуса (колонки) в amoCRM") Long statusId,
        @Schema(description = "Название статуса из amoCRM; null — неизвестно") String statusName
) {
    public static FunnelDTO from(OrderTypeFunnel funnel, Map<Long, String> pipelineNames, Map<Long, String> statusNames) {
        return new FunnelDTO(funnel.getId(), funnel.getType(),
                funnel.getPipelineId(), pipelineNames.get(funnel.getPipelineId()),
                funnel.getStatusId(), statusNames.get(funnel.getStatusId()));
    }
}
