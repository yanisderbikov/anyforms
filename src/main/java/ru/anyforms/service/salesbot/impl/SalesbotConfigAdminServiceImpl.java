package ru.anyforms.service.salesbot.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.FunnelDTO;
import ru.anyforms.dto.salesbot.FunnelRequestDTO;
import ru.anyforms.dto.salesbot.OrderTypeDTO;
import ru.anyforms.dto.salesbot.SalesbotTypeConfigDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.model.salesbot.OrderTypeFunnel;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.repository.OrderTypeFunnelRepository;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotConfigAdminService;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD настроек дрип-кампании поверх {@link OrderTypeFunnelRepository} и {@link BotSequenceRepository}.
 * <p>
 * Инварианты, которые в БД держат уникальные индексы, здесь проверяются заранее, чтобы
 * админка получала понятное сообщение, а не 500: одна воронка на тип, одна позиция на тип.
 * Служебным типам ({@link OrderType#isDrip()} == false) воронку и цепочку завести нельзя —
 * иначе прогон дрип-кампании начал бы рассылать боты лидам этих типов.
 * <p>
 * Порядок цепочки меняется только перестановкой соседей ({@link #moveStep}); руками позиция
 * не задаётся: новый бот всегда встаёт в конец. Один бот — не более одного раза на тип: журнал
 * уникален по {@code (lead_id, bot_id)}, и второй шаг с тем же ботом никогда бы не «закрылся».
 */
@Service
@RequiredArgsConstructor
class SalesbotConfigAdminServiceImpl implements SalesbotConfigAdminService {

    private final OrderTypeFunnelRepository funnelRepository;
    private final BotSequenceRepository sequenceRepository;
    private final SalesbotDirectory salesbotDirectory;
    private final PipelineDirectory pipelineDirectory;

    @Override
    public List<OrderTypeDTO> orderTypes() {
        return Arrays.stream(OrderType.values()).map(OrderTypeDTO::from).toList();
    }

    @Override
    public List<SalesbotTypeConfigDTO> config() {
        Map<OrderType, OrderTypeFunnel> funnels = funnelRepository.findAll().stream()
                .collect(Collectors.toMap(OrderTypeFunnel::getType, Function.identity(), (first, second) -> first));
        Map<Long, String> botNames = salesbotDirectory.namesById();
        Map<Long, String> pipelineNames = pipelineDirectory.pipelineNames();
        Map<Long, String> statusNames = pipelineDirectory.statusNames();
        Map<OrderType, List<BotStepDTO>> steps = sequenceRepository.findAllByOrderByTypeAscPositionAsc().stream()
                .collect(Collectors.groupingBy(BotSequence::getType,
                        Collectors.mapping(step -> BotStepDTO.from(step, botNames), Collectors.toList())));

        return Arrays.stream(OrderType.values())
                .filter(type -> type.isDrip() || funnels.containsKey(type) || steps.containsKey(type))
                .map(type -> new SalesbotTypeConfigDTO(
                        type.name(),
                        type.getLabel(),
                        type.getDescription(),
                        type.isDrip(),
                        funnels.containsKey(type) ? FunnelDTO.from(funnels.get(type), pipelineNames, statusNames) : null,
                        steps.getOrDefault(type, List.of())))
                .toList();
    }

    private FunnelDTO funnelDto(OrderTypeFunnel funnel) {
        return FunnelDTO.from(funnel, pipelineDirectory.pipelineNames(), pipelineDirectory.statusNames());
    }

    @Override
    @Transactional
    public FunnelDTO createFunnel(FunnelRequestDTO request) {
        requireDripType(request.getType());
        if (funnelRepository.findByType(request.getType()).isPresent()) {
            throw badRequest("Для типа " + request.getType().getLabel()
                    + " воронка уже задана — отредактируйте существующую.");
        }
        OrderTypeFunnel funnel = new OrderTypeFunnel();
        applyFunnel(funnel, request);
        return funnelDto(funnelRepository.save(funnel));
    }

    @Override
    @Transactional
    public FunnelDTO updateFunnel(Long id, FunnelRequestDTO request) {
        OrderTypeFunnel funnel = funnelRepository.findById(id)
                .orElseThrow(() -> notFound("Воронка не найдена: " + id));
        requireDripType(request.getType());
        funnelRepository.findByType(request.getType())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw badRequest("Для типа " + request.getType().getLabel() + " воронка уже задана.");
                });
        applyFunnel(funnel, request);
        return funnelDto(funnelRepository.save(funnel));
    }

    @Override
    @Transactional
    public void deleteFunnel(Long id) {
        if (!funnelRepository.existsById(id)) {
            throw notFound("Воронка не найдена: " + id);
        }
        funnelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public BotStepDTO createStep(BotStepCreateRequestDTO request) {
        requireDripType(request.getType());
        rejectDuplicateBot(request.getType(), request.getBotId(), null);
        int position = sequenceRepository.findFirstByTypeOrderByPositionDesc(request.getType())
                .map(last -> last.getPosition() + 1)
                .orElse(1);
        BotSequence step = new BotSequence();
        step.setType(request.getType());
        step.setPosition(position);
        step.setBotId(request.getBotId());
        return BotStepDTO.from(sequenceRepository.save(step), salesbotDirectory.namesById());
    }

    @Override
    @Transactional
    public BotStepDTO updateStep(Long id, BotStepUpdateRequestDTO request) {
        BotSequence step = sequenceRepository.findById(id)
                .orElseThrow(() -> notFound("Шаг цепочки не найден: " + id));
        rejectDuplicateBot(step.getType(), request.getBotId(), id);
        step.setBotId(request.getBotId());
        return BotStepDTO.from(sequenceRepository.save(step), salesbotDirectory.namesById());
    }

    @Override
    @Transactional
    public List<BotStepDTO> moveStep(Long id, StepMoveDirection direction) {
        BotSequence step = sequenceRepository.findById(id)
                .orElseThrow(() -> notFound("Шаг цепочки не найден: " + id));
        Optional<BotSequence> neighbour = direction == StepMoveDirection.UP
                ? sequenceRepository.findFirstByTypeAndPositionLessThanOrderByPositionDesc(step.getType(), step.getPosition())
                : sequenceRepository.findFirstByTypeAndPositionGreaterThanOrderByPositionAsc(step.getType(), step.getPosition());
        if (neighbour.isEmpty()) {
            throw badRequest(direction == StepMoveDirection.UP
                    ? "Шаг уже первый в цепочке."
                    : "Шаг уже последний в цепочке.");
        }
        BotSequence other = neighbour.get();
        int stepPosition = step.getPosition();
        int otherPosition = other.getPosition();
        // UNIQUE(type, position): меняем через временную отрицательную позицию, каждый шаг сбрасывая в БД.
        step.setPosition(-stepPosition);
        sequenceRepository.saveAndFlush(step);
        other.setPosition(stepPosition);
        sequenceRepository.saveAndFlush(other);
        step.setPosition(otherPosition);
        sequenceRepository.saveAndFlush(step);

        Map<Long, String> botNames = salesbotDirectory.namesById();
        return sequenceRepository.findByTypeOrderByPositionAsc(step.getType()).stream()
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

    private static void applyFunnel(OrderTypeFunnel funnel, FunnelRequestDTO request) {
        funnel.setType(request.getType());
        funnel.setPipelineId(request.getPipelineId());
        funnel.setStatusId(request.getStatusId());
    }

    /** Один и тот же бот не может стоять в цепочке типа дважды (кроме самого редактируемого шага). */
    private void rejectDuplicateBot(OrderType type, Long botId, Long exceptStepId) {
        sequenceRepository.findByTypeAndBotId(type, botId)
                .filter(other -> exceptStepId == null || !other.getId().equals(exceptStepId))
                .ifPresent(other -> {
                    throw badRequest("Бот " + botId + " уже есть в цепочке типа " + type.getLabel()
                            + " (шаг " + other.getPosition() + ").");
                });
    }

    private static void requireDripType(OrderType type) {
        if (!type.isDrip()) {
            throw badRequest("Тип " + type.getLabel() + " служебный: воронка и цепочка для него не настраиваются.");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
