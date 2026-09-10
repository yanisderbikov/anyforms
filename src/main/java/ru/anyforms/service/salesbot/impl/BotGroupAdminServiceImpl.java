package ru.anyforms.service.salesbot.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotGroupDTO;
import ru.anyforms.dto.salesbot.BotGroupRequestDTO;
import ru.anyforms.dto.salesbot.BotRunTypeDTO;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;
import ru.anyforms.model.salesbot.BotGroup;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.repository.BotGroupRepository;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.service.salesbot.BotGroupAdminService;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CRUD групп и цепочек. Инварианты, которые в БД держат уникальные индексы, проверяются заранее,
 * чтобы админка получала понятное сообщение: одна позиция на группу, один бот — не более одного
 * раза на группу (журнал уникален по {@code (lead_id, bot_id)}, второй шаг с тем же ботом никогда бы
 * не «закрылся»). Порядок цепочки меняется только перестановкой соседей ({@link #moveStep}).
 */
@Service
@RequiredArgsConstructor
class BotGroupAdminServiceImpl implements BotGroupAdminService {

    private static final java.time.format.DateTimeFormatter HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private final BotGroupRepository groupRepository;
    private final BotSequenceRepository sequenceRepository;
    private final SalesbotDirectory salesbotDirectory;
    private final PipelineDirectory pipelineDirectory;

    @Override
    public List<BotRunTypeDTO> runTypes() {
        return Arrays.stream(BotRunType.values()).map(BotRunTypeDTO::from).toList();
    }

    @Override
    public List<BotGroupDTO> groups() {
        Names names = names();
        Map<Long, List<BotStepDTO>> steps = sequenceRepository.findAllByOrderByGroupIdAscPositionAsc().stream()
                .collect(Collectors.groupingBy(BotSequence::getGroupId,
                        Collectors.mapping(step -> BotStepDTO.from(step, names.bots), Collectors.toList())));
        return groupRepository.findAllByOrderByIdAsc().stream()
                .map(group -> toDto(group, steps.getOrDefault(group.getId(), List.of()), names))
                .toList();
    }

    @Override
    @Transactional
    public BotGroupDTO createGroup(BotGroupRequestDTO request) {
        validateFunnel(request);
        BotGroup group = new BotGroup();
        group.setEnabled(request.getEnabled() == null || request.getEnabled());
        apply(group, request);
        BotGroup saved = groupRepository.save(group);
        return toDto(saved, List.of(), names());
    }

    @Override
    @Transactional
    public BotGroupDTO updateGroup(Long id, BotGroupRequestDTO request) {
        BotGroup group = groupRepository.findById(id).orElseThrow(() -> notFound("Группа не найдена: " + id));
        validateFunnel(request);
        if (request.getEnabled() != null) {
            group.setEnabled(request.getEnabled());
        }
        apply(group, request);
        BotGroup saved = groupRepository.save(group);
        Names names = names();
        List<BotStepDTO> steps = sequenceRepository.findByGroupIdOrderByPositionAsc(id).stream()
                .map(step -> BotStepDTO.from(step, names.bots))
                .toList();
        return toDto(saved, steps, names);
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        if (!groupRepository.existsById(id)) {
            throw notFound("Группа не найдена: " + id);
        }
        // Шаги удаляет каскад FK; в журнале group_id обнуляется (ON DELETE SET NULL).
        groupRepository.deleteById(id);
    }

    @Override
    @Transactional
    public BotStepDTO createStep(BotStepCreateRequestDTO request) {
        if (!groupRepository.existsById(request.getGroupId())) {
            throw notFound("Группа не найдена: " + request.getGroupId());
        }
        rejectDuplicateBot(request.getGroupId(), request.getBotId(), null);
        int position = sequenceRepository.findFirstByGroupIdOrderByPositionDesc(request.getGroupId())
                .map(last -> last.getPosition() + 1)
                .orElse(1);
        BotSequence step = new BotSequence();
        step.setGroupId(request.getGroupId());
        step.setPosition(position);
        step.setBotId(request.getBotId());
        step.setDelayMinutes(request.getDelayMinutes());
        return BotStepDTO.from(sequenceRepository.save(step), salesbotDirectory.namesById());
    }

    @Override
    @Transactional
    public BotStepDTO updateStep(Long id, BotStepUpdateRequestDTO request) {
        BotSequence step = sequenceRepository.findById(id)
                .orElseThrow(() -> notFound("Шаг цепочки не найден: " + id));
        rejectDuplicateBot(step.getGroupId(), request.getBotId(), id);
        step.setBotId(request.getBotId());
        step.setDelayMinutes(request.getDelayMinutes());
        return BotStepDTO.from(sequenceRepository.save(step), salesbotDirectory.namesById());
    }

    @Override
    @Transactional
    public List<BotStepDTO> moveStep(Long id, StepMoveDirection direction) {
        BotSequence step = sequenceRepository.findById(id)
                .orElseThrow(() -> notFound("Шаг цепочки не найден: " + id));
        Optional<BotSequence> neighbour = direction == StepMoveDirection.UP
                ? sequenceRepository.findFirstByGroupIdAndPositionLessThanOrderByPositionDesc(step.getGroupId(), step.getPosition())
                : sequenceRepository.findFirstByGroupIdAndPositionGreaterThanOrderByPositionAsc(step.getGroupId(), step.getPosition());
        if (neighbour.isEmpty()) {
            throw badRequest(direction == StepMoveDirection.UP
                    ? "Шаг уже первый в цепочке."
                    : "Шаг уже последний в цепочке.");
        }
        BotSequence other = neighbour.get();
        int stepPosition = step.getPosition();
        int otherPosition = other.getPosition();
        // UNIQUE(group_id, position): меняем через временную отрицательную позицию, каждый шаг сбрасывая в БД.
        step.setPosition(-stepPosition);
        sequenceRepository.saveAndFlush(step);
        other.setPosition(stepPosition);
        sequenceRepository.saveAndFlush(other);
        step.setPosition(otherPosition);
        sequenceRepository.saveAndFlush(step);

        Map<Long, String> botNames = salesbotDirectory.namesById();
        return sequenceRepository.findByGroupIdOrderByPositionAsc(step.getGroupId()).stream()
                .map(s -> BotStepDTO.from(s, botNames))
                .toList();
    }

    @Override
    @Transactional
    public void deleteStep(Long id) {
        if (!sequenceRepository.existsById(id)) {
            throw notFound("Шаг цепочки не найден: " + id);
        }
        sequenceRepository.deleteById(id);
    }

    private static void validateFunnel(BotGroupRequestDTO request) {
        if ((request.getPipelineId() == null) != (request.getStatusId() == null)) {
            throw badRequest("Укажите и воронку, и статус — либо оставьте оба пустыми.");
        }
        boolean fromSet = request.getSendFrom() != null && !request.getSendFrom().isBlank();
        boolean toSet = request.getSendTo() != null && !request.getSendTo().isBlank();
        if (fromSet != toSet) {
            throw badRequest("Окно отправки: укажите и начало, и конец — либо оставьте оба пустыми.");
        }
        if (fromSet && !LocalTime.parse(request.getSendFrom()).isBefore(LocalTime.parse(request.getSendTo()))) {
            throw badRequest("Окно отправки: начало должно быть раньше конца.");
        }
    }

    private static void apply(BotGroup group, BotGroupRequestDTO request) {
        group.setName(request.getName().trim());
        group.setPipelineId(request.getPipelineId());
        group.setStatusId(request.getStatusId());
        boolean windowSet = request.getSendFrom() != null && !request.getSendFrom().isBlank();
        group.setSendWindow(windowSet ? LocalTime.parse(request.getSendFrom()) : null,
                windowSet ? LocalTime.parse(request.getSendTo()) : null);
    }

    /** Один и тот же бот не может стоять в цепочке группы дважды (кроме самого редактируемого шага). */
    private void rejectDuplicateBot(Long groupId, Long botId, Long exceptStepId) {
        sequenceRepository.findByGroupIdAndBotId(groupId, botId)
                .filter(other -> exceptStepId == null || !other.getId().equals(exceptStepId))
                .ifPresent(other -> {
                    throw badRequest("Бот " + botId + " уже есть в этой цепочке (шаг " + other.getPosition() + ").");
                });
    }

    private Names names() {
        return new Names(salesbotDirectory.namesById(), pipelineDirectory.pipelineNames(), pipelineDirectory.statusNames());
    }

    private static BotGroupDTO toDto(BotGroup group, List<BotStepDTO> steps, Names names) {
        return new BotGroupDTO(
                group.getId(),
                group.getName(),
                group.isEnabled(),
                group.getPipelineId(),
                group.getPipelineId() != null ? names.pipelines.get(group.getPipelineId()) : null,
                group.getStatusId(),
                group.getStatusId() != null ? names.statuses.get(group.getStatusId()) : null,
                group.hasSendWindow() ? HH_MM.format(group.sendFromTime()) : null,
                group.hasSendWindow() ? HH_MM.format(group.sendToTime()) : null,
                steps);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record Names(Map<Long, String> bots, Map<Long, String> pipelines, Map<Long, String> statuses) {
    }
}
