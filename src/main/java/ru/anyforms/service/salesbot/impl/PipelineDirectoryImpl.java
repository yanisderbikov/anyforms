package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoPipelineInfo;
import ru.anyforms.model.amo.AmoPipelineStatusInfo;
import ru.anyforms.service.salesbot.PipelineDirectory;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PipelineDirectory} поверх {@link AmoCrmGateway#getPipelines()} с кэшем на {@link #TTL}.
 */
@Slf4j
@Component
class PipelineDirectoryImpl implements PipelineDirectory {

    static final Duration TTL = Duration.ofMinutes(5);

    private final TtlSnapshotCache<AmoPipelineInfo> cache;

    /** Конструктор для Spring: два конструктора в классе, поэтому явно указываем, какой внедрять. */
    @Autowired
    PipelineDirectoryImpl(AmoCrmGateway amoCrmGateway) {
        this(amoCrmGateway, Clock.systemUTC());
    }

    /** Для тестов: подменяемые часы. */
    PipelineDirectoryImpl(AmoCrmGateway amoCrmGateway, Clock clock) {
        this.cache = new TtlSnapshotCache<>("список воронок", amoCrmGateway::getPipelines, TTL, clock);
    }

    @Override
    public List<AmoPipelineInfo> pipelines(boolean refresh) {
        return cache.get(refresh);
    }

    @Override
    public Map<Long, String> pipelineNames() {
        Map<Long, String> names = new HashMap<>();
        for (AmoPipelineInfo pipeline : safePipelines()) {
            if (pipeline.id() != null && pipeline.name() != null) {
                names.put(pipeline.id(), pipeline.name());
            }
        }
        return names;
    }

    @Override
    public Map<Long, String> statusNames() {
        Map<Long, String> names = new HashMap<>();
        for (AmoPipelineInfo pipeline : safePipelines()) {
            for (AmoPipelineStatusInfo status : pipeline.statuses()) {
                if (status.id() != null && status.name() != null) {
                    names.put(status.id(), status.name());
                }
            }
        }
        return names;
    }

    private List<AmoPipelineInfo> safePipelines() {
        try {
            return pipelines(false);
        } catch (IllegalStateException e) {
            log.warn("Pipeline names unavailable: {}", e.getMessage());
            return List.of();
        }
    }
}
