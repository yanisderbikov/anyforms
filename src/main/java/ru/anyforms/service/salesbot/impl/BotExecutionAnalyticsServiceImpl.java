package ru.anyforms.service.salesbot.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotAnalyticsDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsStepDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsTotalsDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsTypeDTO;
import ru.anyforms.dto.salesbot.BotExecutionLogDTO;
import ru.anyforms.dto.salesbot.BotExecutionLogPageDTO;
import ru.anyforms.model.salesbot.BotExecutionLog;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.repository.BotStepStatusCount;
import ru.anyforms.service.salesbot.BotExecutionAnalyticsService;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Аналитика и журнал поверх {@link BotExecutionLogRepository}.
 * <p>
 * «Заблокировали на шаге» = записи {@link BotExecutionStatus#MESSAGE_SEND_FAILED}: бот был
 * запущен, но amoCRM прислал вебхук о недоставке — этот статус ставится на последнюю запись
 * лида, т.е. ровно на тот шаг, после которого сообщения перестали доходить.
 * Шаги из текущей {@code bot_sequence} показываются даже с нулями — так видно, до какой
 * позиции лиды вообще доходят.
 */
@Service
@RequiredArgsConstructor
class BotExecutionAnalyticsServiceImpl implements BotExecutionAnalyticsService {

    /** Период в админке задаётся календарными днями по Москве. */
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final int MAX_PAGE_SIZE = 200;

    private final BotExecutionLogRepository logRepository;
    private final BotSequenceRepository sequenceRepository;
    private final SalesbotDirectory salesbotDirectory;

    @Override
    public BotAnalyticsDTO analytics(LocalDate from, LocalDate to) {
        Instant fromInstant = fromInstant(from);
        Instant toInstant = toInstant(to);
        if (!fromInstant.isBefore(toInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Начало периода должно быть раньше конца.");
        }

        // (тип, позиция, бот) -> счётчики; TreeMap — чтобы шаги шли по позиции, затем по боту.
        Map<OrderType, Map<StepKey, StepCounters>> byType = new HashMap<>();
        for (BotStepStatusCount count : logRepository.countByStepAndStatus(fromInstant, toInstant)) {
            StepKey key = new StepKey(count.position(), count.botId());
            byType.computeIfAbsent(count.type(), t -> new TreeMap<>())
                    .computeIfAbsent(key, k -> new StepCounters())
                    .add(count.status(), count.count() == null ? 0 : count.count());
        }
        // Шаги текущей цепочки: даже без записей за период — с нулями.
        Map<OrderType, Map<StepKey, Boolean>> configured = new HashMap<>();
        sequenceRepository.findAll().forEach(step -> {
            StepKey key = new StepKey(step.getPosition(), step.getBotId());
            configured.computeIfAbsent(step.getType(), t -> new HashMap<>()).put(key, Boolean.TRUE);
            byType.computeIfAbsent(step.getType(), t -> new TreeMap<>()).computeIfAbsent(key, k -> new StepCounters());
        });

        Map<Long, String> botNames = salesbotDirectory.namesById();
        long totalSent = 0;
        long totalBlocked = 0;
        long totalFailed = 0;
        List<BotAnalyticsTypeDTO> types = new ArrayList<>();
        for (OrderType type : OrderType.values()) {
            Map<StepKey, StepCounters> steps = byType.get(type);
            if (steps == null || steps.isEmpty()) {
                continue;
            }
            Map<StepKey, Boolean> configuredSteps = configured.getOrDefault(type, Map.of());
            long typeSent = 0;
            long typeBlocked = 0;
            long typeFailed = 0;
            List<BotAnalyticsStepDTO> stepDtos = new ArrayList<>();
            for (Map.Entry<StepKey, StepCounters> entry : steps.entrySet()) {
                StepCounters c = entry.getValue();
                typeSent += c.sent;
                typeBlocked += c.blocked;
                typeFailed += c.failed;
                stepDtos.add(new BotAnalyticsStepDTO(
                        entry.getKey().position(),
                        entry.getKey().botId(),
                        botNames.get(entry.getKey().botId()),
                        c.sent,
                        c.blocked,
                        c.failed,
                        c.blockedShare(),
                        configuredSteps.containsKey(entry.getKey())));
            }
            totalSent += typeSent;
            totalBlocked += typeBlocked;
            totalFailed += typeFailed;
            types.add(new BotAnalyticsTypeDTO(type.name(), type.getLabel(), typeSent, typeBlocked, typeFailed, stepDtos));
        }

        long leads = logRepository.countDistinctLeads(fromInstant, toInstant);
        return new BotAnalyticsDTO(
                fromInstant.toString(),
                toInstant.toString(),
                new BotAnalyticsTotalsDTO(totalSent, totalBlocked, totalFailed, leads),
                types);
    }

    @Override
    public BotExecutionLogPageDTO logs(OrderType type, BotExecutionStatus status, Long leadId,
                                       LocalDate from, LocalDate to, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Instant fromInstant = from == null ? null : fromInstant(from);
        Instant toInstant = to == null ? null : toInstant(to);

        Specification<BotExecutionLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (leadId != null) {
                predicates.add(cb.equal(root.get("leadId"), leadId));
            }
            if (fromInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateExecuted"), fromInstant));
            }
            if (toInstant != null) {
                predicates.add(cb.lessThan(root.get("dateExecuted"), toInstant));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<BotExecutionLog> result = logRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "dateExecuted", "id")));
        Map<Long, String> botNames = salesbotDirectory.namesById();
        return new BotExecutionLogPageDTO(
                result.getContent().stream().map(log -> BotExecutionLogDTO.from(log, botNames)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /** Начало календарного дня по Москве; без даты — с начала времён. */
    private static Instant fromInstant(LocalDate from) {
        return from == null ? Instant.EPOCH : from.atStartOfDay(MSK).toInstant();
    }

    /** Исключительная граница: начало следующего дня по Москве; без даты — «до завтра». */
    private static Instant toInstant(LocalDate to) {
        return to == null
                ? Instant.now().plus(1, ChronoUnit.DAYS)
                : to.plusDays(1).atStartOfDay(MSK).toInstant();
    }

    /** Ключ шага; сортировка — по позиции, затем по боту (в журнале на одной позиции могут быть разные боты). */
    private record StepKey(Integer position, Long botId) implements Comparable<StepKey> {
        private static final Comparator<StepKey> ORDER = Comparator
                .comparing(StepKey::position, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(StepKey::botId, Comparator.nullsFirst(Comparator.naturalOrder()));

        @Override
        public int compareTo(StepKey other) {
            return ORDER.compare(this, other);
        }
    }

    private static final class StepCounters {
        private long sent;
        private long blocked;
        private long failed;

        void add(BotExecutionStatus status, long count) {
            switch (status) {
                case SUCCESS -> sent += count;
                case MESSAGE_SEND_FAILED -> blocked += count;
                case FAILED -> failed += count;
            }
        }

        /** Доля недоставленных среди запущенных ботов; null — запусков не было. */
        Double blockedShare() {
            long launched = sent + blocked;
            return launched == 0 ? null : (double) blocked / launched;
        }
    }
}
