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
import ru.anyforms.model.salesbot.BotGroup;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.repository.BotGroupRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Аналитика и журнал поверх {@link BotExecutionLogRepository}.
 * <p>
 * «Заблокировали на шаге» = записи {@link BotExecutionStatus#MESSAGE_SEND_FAILED}: бот был
 * запущен, но amoCRM прислал вебхук о недоставке — этот статус ставится на последнюю запись
 * лида, т.е. ровно на тот шаг, после которого сообщения перестали доходить.
 * Разделы — группы (все, даже без записей: видно, до какой позиции лиды доходят) и служебные
 * типы запусков, по которым есть записи.
 */
@Service
@RequiredArgsConstructor
class BotExecutionAnalyticsServiceImpl implements BotExecutionAnalyticsService {

    /** Период в админке задаётся календарными днями по Москве. */
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final int MAX_PAGE_SIZE = 200;

    private final BotExecutionLogRepository logRepository;
    private final BotSequenceRepository sequenceRepository;
    private final BotGroupRepository groupRepository;
    private final SalesbotDirectory salesbotDirectory;

    @Override
    public BotAnalyticsDTO analytics(LocalDate from, LocalDate to) {
        Instant fromInstant = fromInstant(from);
        Instant toInstant = toInstant(to);
        if (!fromInstant.isBefore(toInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Начало периода должно быть раньше конца.");
        }

        Map<Long, BotGroup> groups = groupRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(BotGroup::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        // Разделы в порядке: группы по id, затем служебные типы по порядку enum; внутри — шаги по позиции/боту.
        Map<SectionKey, Map<StepKey, StepCounters>> sections = new TreeMap<>();
        for (BotGroup group : groups.values()) {
            sections.put(new SectionKey(BotRunType.DRIP, group.getId()), new TreeMap<>());
        }
        for (BotStepStatusCount count : logRepository.countByStepAndStatus(fromInstant, toInstant)) {
            SectionKey section = new SectionKey(count.type(), count.type() == BotRunType.DRIP ? count.groupId() : null);
            sections.computeIfAbsent(section, k -> new TreeMap<>())
                    .computeIfAbsent(new StepKey(count.position(), count.botId()), k -> new StepCounters())
                    .add(count.status(), count.count() == null ? 0 : count.count());
        }
        // Шаги текущих цепочек: даже без записей за период — с нулями.
        Map<Long, Map<StepKey, Boolean>> configured = new HashMap<>();
        sequenceRepository.findAllByOrderByGroupIdAscPositionAsc().forEach(step -> {
            StepKey key = new StepKey(step.getPosition(), step.getBotId());
            configured.computeIfAbsent(step.getGroupId(), g -> new HashMap<>()).put(key, Boolean.TRUE);
            sections.computeIfAbsent(new SectionKey(BotRunType.DRIP, step.getGroupId()), k -> new TreeMap<>())
                    .computeIfAbsent(key, k -> new StepCounters());
        });

        Map<Long, String> botNames = salesbotDirectory.namesById();
        long totalSent = 0;
        long totalBlocked = 0;
        long totalFailed = 0;
        List<BotAnalyticsTypeDTO> result = new ArrayList<>();
        for (Map.Entry<SectionKey, Map<StepKey, StepCounters>> sectionEntry : sections.entrySet()) {
            SectionKey section = sectionEntry.getKey();
            Map<StepKey, StepCounters> steps = sectionEntry.getValue();
            if (steps.isEmpty()) {
                continue; // группа без цепочки и без записей
            }
            Map<StepKey, Boolean> configuredSteps = section.groupId() != null
                    ? configured.getOrDefault(section.groupId(), Map.of()) : Map.of();
            long sectionSent = 0;
            long sectionBlocked = 0;
            long sectionFailed = 0;
            List<BotAnalyticsStepDTO> stepDtos = new ArrayList<>();
            for (Map.Entry<StepKey, StepCounters> entry : steps.entrySet()) {
                StepCounters c = entry.getValue();
                sectionSent += c.sent;
                sectionBlocked += c.blocked;
                sectionFailed += c.failed;
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
            totalSent += sectionSent;
            totalBlocked += sectionBlocked;
            totalFailed += sectionFailed;

            BotGroup group = section.groupId() != null ? groups.get(section.groupId()) : null;
            boolean groupDeleted = section.type() == BotRunType.DRIP && section.groupId() != null && group == null;
            String label = group != null ? group.getName()
                    : groupDeleted ? "Удалённая группа #" + section.groupId()
                    : section.type().getLabel();
            String key = section.groupId() != null ? "group:" + section.groupId() : section.type().name();
            result.add(new BotAnalyticsTypeDTO(key, section.type(), section.groupId(), label, groupDeleted,
                    sectionSent, sectionBlocked, sectionFailed, stepDtos));
        }

        long leads = logRepository.countDistinctLeads(fromInstant, toInstant);
        return new BotAnalyticsDTO(
                fromInstant.toString(),
                toInstant.toString(),
                new BotAnalyticsTotalsDTO(totalSent, totalBlocked, totalFailed, leads),
                result);
    }

    @Override
    public BotExecutionLogPageDTO logs(BotRunType type, Long groupId, BotExecutionStatus status, Long leadId,
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
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
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
        Map<Long, String> groupNames = groupRepository.findAll().stream()
                .collect(Collectors.toMap(BotGroup::getId, BotGroup::getName, (a, b) -> a));
        return new BotExecutionLogPageDTO(
                result.getContent().stream().map(log -> BotExecutionLogDTO.from(log, botNames, groupNames)).toList(),
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

    /** Раздел аналитики: DRIP-группы сначала (по id), затем служебные типы по порядку enum. */
    private record SectionKey(BotRunType type, Long groupId) implements Comparable<SectionKey> {
        private static final Comparator<SectionKey> ORDER = Comparator
                .comparing((SectionKey k) -> k.type() == BotRunType.DRIP ? 0 : 1)
                .thenComparing(SectionKey::type)
                .thenComparing(SectionKey::groupId, Comparator.nullsLast(Comparator.naturalOrder()));

        @Override
        public int compareTo(SectionKey other) {
            return ORDER.compare(this, other);
        }
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
