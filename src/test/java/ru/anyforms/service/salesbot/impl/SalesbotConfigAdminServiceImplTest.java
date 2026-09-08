package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.FunnelDTO;
import ru.anyforms.dto.salesbot.FunnelRequestDTO;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты админки настроек дрип-кампании: группировка сводки и проверки уникальности
 * (одна воронка на тип, одна позиция на тип), запрет служебных типов.
 */
class SalesbotConfigAdminServiceImplTest {

    private final OrderTypeFunnelRepository funnelRepository = mock(OrderTypeFunnelRepository.class);
    private final BotSequenceRepository sequenceRepository = mock(BotSequenceRepository.class);
    private final SalesbotDirectory salesbotDirectory = mock(SalesbotDirectory.class);
    private final PipelineDirectory pipelineDirectory = mock(PipelineDirectory.class);
    private final SalesbotConfigAdminService service =
            new SalesbotConfigAdminServiceImpl(funnelRepository, sequenceRepository, salesbotDirectory, pipelineDirectory);

    @Test
    void config_groupsFunnelAndStepsPerType_listsAllDripTypes_andOnlyNonEmptyServiceTypes() {
        when(funnelRepository.findAll()).thenReturn(List.of(funnel(1L, OrderType.RETAIL, 10L, 20L)));
        when(sequenceRepository.findAllByOrderByTypeAscPositionAsc()).thenReturn(List.of(
                step(3L, OrderType.DELIVERY, 1, 555L),
                step(1L, OrderType.RETAIL, 1, 101L),
                step(2L, OrderType.RETAIL, 2, 102L)));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет + каталог"));
        when(pipelineDirectory.pipelineNames()).thenReturn(Map.of(10L, "Розница"));
        when(pipelineDirectory.statusNames()).thenReturn(Map.of(20L, "Новый заказ"));

        List<SalesbotTypeConfigDTO> config = service.config();

        // Четыре дрип-типа всегда (в порядке enum), DELIVERY — потому что по нему есть шаги, MANUAL — нет.
        assertEquals(List.of("RETAIL", "RETAIL_REPEAT", "CUSTOM", "CUSTOM_REPEAT", "DELIVERY"),
                config.stream().map(SalesbotTypeConfigDTO::type).toList());

        SalesbotTypeConfigDTO retail = config.get(0);
        assertEquals("Розница", retail.label());
        assertTrue(retail.drip());
        assertEquals(10L, retail.funnel().pipelineId());
        assertEquals("Розница", retail.funnel().pipelineName());
        assertEquals(20L, retail.funnel().statusId());
        assertEquals("Новый заказ", retail.funnel().statusName());
        assertEquals(List.of(101L, 102L), retail.steps().stream().map(BotStepDTO::botId).toList());
        // Имя подставляется из справочника amoCRM; неизвестный бот — без имени, но не ошибка.
        assertEquals("Привет + каталог", retail.steps().get(0).botName());
        assertNull(retail.steps().get(1).botName());

        SalesbotTypeConfigDTO retailRepeat = config.get(1);
        assertNull(retailRepeat.funnel());
        assertTrue(retailRepeat.steps().isEmpty());

        SalesbotTypeConfigDTO delivery = config.get(4);
        assertFalse(delivery.drip());
        assertEquals(1, delivery.steps().size());
    }

    @Test
    void createFunnel_rejectsSecondFunnelForSameType() {
        when(funnelRepository.findByType(OrderType.RETAIL))
                .thenReturn(Optional.of(funnel(1L, OrderType.RETAIL, 10L, 20L)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createFunnel(funnelRequest(OrderType.RETAIL, 11L, 21L)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(funnelRepository, never()).save(any());
    }

    @Test
    void createFunnel_rejectsServiceType() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createFunnel(funnelRequest(OrderType.MANUAL, 11L, 21L)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(funnelRepository, never()).save(any());
    }

    @Test
    void createFunnel_savesAndReturnsDto() {
        when(funnelRepository.findByType(OrderType.CUSTOM)).thenReturn(Optional.empty());
        when(funnelRepository.save(any(OrderTypeFunnel.class))).thenAnswer(inv -> {
            OrderTypeFunnel saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        FunnelDTO dto = service.createFunnel(funnelRequest(OrderType.CUSTOM, 11L, 21L));

        assertEquals(42L, dto.id());
        assertEquals(OrderType.CUSTOM, dto.type());
        assertEquals(11L, dto.pipelineId());
        assertEquals(21L, dto.statusId());
    }

    @Test
    void updateFunnel_allowsKeepingOwnType_andRejectsTypeOfAnotherFunnel() {
        OrderTypeFunnel retail = funnel(1L, OrderType.RETAIL, 10L, 20L);
        OrderTypeFunnel custom = funnel(2L, OrderType.CUSTOM, 30L, 40L);
        when(funnelRepository.findById(1L)).thenReturn(Optional.of(retail));
        when(funnelRepository.findByType(OrderType.RETAIL)).thenReturn(Optional.of(retail));
        when(funnelRepository.findByType(OrderType.CUSTOM)).thenReturn(Optional.of(custom));
        when(funnelRepository.save(retail)).thenReturn(retail);

        FunnelDTO updated = service.updateFunnel(1L, funnelRequest(OrderType.RETAIL, 11L, 21L));
        assertEquals(11L, updated.pipelineId());
        assertEquals(21L, updated.statusId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateFunnel(1L, funnelRequest(OrderType.CUSTOM, 11L, 21L)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void deleteFunnel_notFound() {
        when(funnelRepository.existsById(7L)).thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deleteFunnel(7L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(funnelRepository, never()).deleteById(any());
    }

    @Test
    void createStep_appendsAfterLastPosition() {
        when(sequenceRepository.findByTypeAndBotId(OrderType.RETAIL, 103L)).thenReturn(Optional.empty());
        when(sequenceRepository.findFirstByTypeOrderByPositionDesc(OrderType.RETAIL))
                .thenReturn(Optional.of(step(2L, OrderType.RETAIL, 5, 102L))); // позиции с пропусками: последняя 5
        when(sequenceRepository.save(any(BotSequence.class))).thenAnswer(inv -> {
            BotSequence saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        BotStepDTO dto = service.createStep(createRequest(OrderType.RETAIL, 103L));

        assertEquals(9L, dto.id());
        assertEquals(6, dto.position());
        assertEquals(103L, dto.botId());
    }

    @Test
    void createStep_startsAtOne_forEmptyChain() {
        when(sequenceRepository.findFirstByTypeOrderByPositionDesc(OrderType.CUSTOM)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(BotSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(1, service.createStep(createRequest(OrderType.CUSTOM, 201L)).position());
    }

    @Test
    void createStep_rejectsBotAlreadyInChain() {
        when(sequenceRepository.findByTypeAndBotId(OrderType.RETAIL, 101L))
                .thenReturn(Optional.of(step(1L, OrderType.RETAIL, 1, 101L)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createStep(createRequest(OrderType.RETAIL, 101L)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(sequenceRepository, never()).save(any());
    }

    @Test
    void createStep_rejectsServiceType() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createStep(createRequest(OrderType.DELIVERY, 5001L)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void updateStep_changesBotOnly_andRejectsBotOfAnotherStep() {
        BotSequence first = step(1L, OrderType.RETAIL, 1, 101L);
        BotSequence second = step(2L, OrderType.RETAIL, 2, 102L);
        when(sequenceRepository.findById(1L)).thenReturn(Optional.of(first));
        when(sequenceRepository.findByTypeAndBotId(OrderType.RETAIL, 777L)).thenReturn(Optional.empty());
        when(sequenceRepository.findByTypeAndBotId(OrderType.RETAIL, 101L)).thenReturn(Optional.of(first));
        when(sequenceRepository.findByTypeAndBotId(OrderType.RETAIL, 102L)).thenReturn(Optional.of(second));
        when(sequenceRepository.save(first)).thenReturn(first);

        BotStepDTO updated = service.updateStep(1L, updateRequest(777L));
        assertEquals(777L, updated.botId());
        assertEquals(1, updated.position());

        // Свой же бот (нашёлся тот же шаг) — не дубликат.
        first.setBotId(101L);
        assertEquals(101L, service.updateStep(1L, updateRequest(101L)).botId());

        // Бот другого шага — дубликат.
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateStep(1L, updateRequest(102L)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void moveStep_swapsPositionsWithNeighbour_evenAcrossGaps() {
        BotSequence step = step(2L, OrderType.RETAIL, 3, 102L);
        BotSequence above = step(1L, OrderType.RETAIL, 1, 101L); // позиция 2 пропущена
        when(sequenceRepository.findById(2L)).thenReturn(Optional.of(step));
        when(sequenceRepository.findFirstByTypeAndPositionLessThanOrderByPositionDesc(OrderType.RETAIL, 3))
                .thenReturn(Optional.of(above));
        when(sequenceRepository.findByTypeOrderByPositionAsc(OrderType.RETAIL)).thenReturn(List.of(step, above));

        List<BotStepDTO> chain = service.moveStep(2L, StepMoveDirection.UP);

        assertEquals(1, step.getPosition());
        assertEquals(3, above.getPosition());
        assertEquals(2, chain.size());
        verify(sequenceRepository, times(3)).saveAndFlush(any(BotSequence.class));
    }

    @Test
    void moveStep_rejectsMovingFirstUp_andLastDown() {
        BotSequence only = step(1L, OrderType.RETAIL, 1, 101L);
        when(sequenceRepository.findById(1L)).thenReturn(Optional.of(only));
        when(sequenceRepository.findFirstByTypeAndPositionLessThanOrderByPositionDesc(OrderType.RETAIL, 1))
                .thenReturn(Optional.empty());
        when(sequenceRepository.findFirstByTypeAndPositionGreaterThanOrderByPositionAsc(OrderType.RETAIL, 1))
                .thenReturn(Optional.empty());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.moveStep(1L, StepMoveDirection.UP)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.moveStep(1L, StepMoveDirection.DOWN)).getStatusCode());
        verify(sequenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteStep_notFound() {
        when(sequenceRepository.existsById(7L)).thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deleteStep(7L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(sequenceRepository, never()).deleteById(any());
    }

    private static OrderTypeFunnel funnel(Long id, OrderType type, Long pipelineId, Long statusId) {
        OrderTypeFunnel f = new OrderTypeFunnel();
        f.setId(id);
        f.setType(type);
        f.setPipelineId(pipelineId);
        f.setStatusId(statusId);
        return f;
    }

    private static BotSequence step(Long id, OrderType type, int position, Long botId) {
        BotSequence s = new BotSequence();
        s.setId(id);
        s.setType(type);
        s.setPosition(position);
        s.setBotId(botId);
        return s;
    }

    private static FunnelRequestDTO funnelRequest(OrderType type, Long pipelineId, Long statusId) {
        return FunnelRequestDTO.builder().type(type).pipelineId(pipelineId).statusId(statusId).build();
    }

    private static BotStepCreateRequestDTO createRequest(OrderType type, Long botId) {
        return BotStepCreateRequestDTO.builder().type(type).botId(botId).build();
    }

    private static BotStepUpdateRequestDTO updateRequest(Long botId) {
        return BotStepUpdateRequestDTO.builder().botId(botId).build();
    }
}
