package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotGroupDTO;
import ru.anyforms.dto.salesbot.BotGroupRequestDTO;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;
import ru.anyforms.model.salesbot.BotGroup;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.repository.BotGroupRepository;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.service.salesbot.BotGroupAdminService;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Тесты админки групп: сводка с именами, валидация воронки, добавление в конец, дубликаты, перестановка. */
class BotGroupAdminServiceImplTest {

    private final BotGroupRepository groupRepository = mock(BotGroupRepository.class);
    private final BotSequenceRepository sequenceRepository = mock(BotSequenceRepository.class);
    private final SalesbotDirectory salesbotDirectory = mock(SalesbotDirectory.class);
    private final PipelineDirectory pipelineDirectory = mock(PipelineDirectory.class);
    private final BotGroupAdminService service =
            new BotGroupAdminServiceImpl(groupRepository, sequenceRepository, salesbotDirectory, pipelineDirectory);

    @Test
    void groups_listsAllGroupsWithStepsAndNames() {
        when(groupRepository.findAllByOrderByIdAsc()).thenReturn(List.of(group(1L, "Розница", 10L, 20L), group(2L, "Новая", null, null)));
        when(sequenceRepository.findAllByOrderByGroupIdAscPositionAsc()).thenReturn(List.of(step(1L, 1L, 1, 101L), step(2L, 1L, 2, 102L)));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет"));
        when(pipelineDirectory.pipelineNames()).thenReturn(Map.of(10L, "Розница"));
        when(pipelineDirectory.statusNames()).thenReturn(Map.of(20L, "Новый заказ"));

        List<BotGroupDTO> groups = service.groups();

        assertEquals(2, groups.size());
        BotGroupDTO retail = groups.get(0);
        assertEquals("Розница", retail.pipelineName());
        assertEquals("Новый заказ", retail.statusName());
        assertEquals(List.of(101L, 102L), retail.steps().stream().map(BotStepDTO::botId).toList());
        assertEquals("Привет", retail.steps().get(0).botName());
        assertNull(retail.steps().get(1).botName());
        assertNull(groups.get(1).pipelineId());
        assertTrue(groups.get(1).steps().isEmpty());
    }

    @Test
    void createGroup_trimsName_defaultsEnabled_andRejectsHalfFunnel() {
        when(groupRepository.save(any(BotGroup.class))).thenAnswer(inv -> {
            BotGroup g = inv.getArgument(0);
            g.setId(5L);
            return g;
        });

        BotGroupDTO created = service.createGroup(BotGroupRequestDTO.builder().name("  Повтор ").build());
        assertEquals(5L, created.id());
        assertEquals("Повтор", created.name());
        assertTrue(created.enabled());
        assertNull(created.pipelineId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createGroup(BotGroupRequestDTO.builder().name("x").pipelineId(10L).build()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void updateGroup_changesNameFunnelAndEnabled() {
        BotGroup group = group(1L, "Розница", 10L, 20L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.save(group)).thenReturn(group);

        BotGroupDTO updated = service.updateGroup(1L,
                BotGroupRequestDTO.builder().name("Розница 2").pipelineId(11L).statusId(21L).enabled(false)
                        .sendFrom("10:00").sendTo("17:00").build());

        assertEquals("Розница 2", updated.name());
        assertEquals(11L, updated.pipelineId());
        assertFalse(updated.enabled());
        assertEquals("10:00", updated.sendFrom());
        assertEquals("17:00", updated.sendTo());

        // Половина окна или окно задом наперёд — ошибка; пустое окно — сброс на умолчание.
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.updateGroup(1L,
                BotGroupRequestDTO.builder().name("x").sendFrom("10:00").build())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.updateGroup(1L,
                BotGroupRequestDTO.builder().name("x").sendFrom("17:00").sendTo("10:00").build())).getStatusCode());
        assertNull(service.updateGroup(1L, BotGroupRequestDTO.builder().name("x").build()).sendFrom());
    }

    @Test
    void deleteGroup_notFound() {
        when(groupRepository.existsById(7L)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.deleteGroup(7L)).getStatusCode());
        verify(groupRepository, never()).deleteById(any());
    }

    @Test
    void createStep_appendsAfterLastPosition_andRejectsDuplicateBot() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(sequenceRepository.findFirstByGroupIdOrderByPositionDesc(1L)).thenReturn(Optional.of(step(9L, 1L, 5, 102L)));
        when(sequenceRepository.save(any(BotSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        BotStepDTO dto = service.createStep(BotStepCreateRequestDTO.builder().groupId(1L).botId(103L).delayMinutes(180).build());
        assertEquals(6, dto.position());
        assertEquals(1L, dto.groupId());
        assertEquals(180, dto.delayMinutes());

        when(sequenceRepository.findByGroupIdAndBotId(1L, 101L)).thenReturn(Optional.of(step(1L, 1L, 1, 101L)));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.createStep(BotStepCreateRequestDTO.builder().groupId(1L).botId(101L).build())).getStatusCode());
    }

    @Test
    void createStep_unknownGroup_404() {
        when(groupRepository.existsById(42L)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.createStep(BotStepCreateRequestDTO.builder().groupId(42L).botId(1L).build())).getStatusCode());
    }

    @Test
    void updateStep_changesBotOnly_andRejectsBotOfAnotherStep() {
        BotSequence first = step(1L, 1L, 1, 101L);
        when(sequenceRepository.findById(1L)).thenReturn(Optional.of(first));
        when(sequenceRepository.findByGroupIdAndBotId(1L, 102L)).thenReturn(Optional.of(step(2L, 1L, 2, 102L)));
        when(sequenceRepository.save(first)).thenReturn(first);

        assertEquals(777L, service.updateStep(1L, BotStepUpdateRequestDTO.builder().botId(777L).delayMinutes(60).build()).botId());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.updateStep(1L, BotStepUpdateRequestDTO.builder().botId(102L).build())).getStatusCode());
    }

    @Test
    void moveStep_swapsWithNeighbour_andRejectsAtEdges() {
        BotSequence step = step(2L, 1L, 3, 102L);
        BotSequence above = step(1L, 1L, 1, 101L);
        when(sequenceRepository.findById(2L)).thenReturn(Optional.of(step));
        when(sequenceRepository.findFirstByGroupIdAndPositionLessThanOrderByPositionDesc(1L, 3)).thenReturn(Optional.of(above));
        when(sequenceRepository.findByGroupIdOrderByPositionAsc(1L)).thenReturn(List.of(step, above));

        List<BotStepDTO> chain = service.moveStep(2L, StepMoveDirection.UP);
        assertEquals(1, step.getPosition());
        assertEquals(3, above.getPosition());
        assertEquals(2, chain.size());
        verify(sequenceRepository, times(3)).saveAndFlush(any(BotSequence.class));

        when(sequenceRepository.findFirstByGroupIdAndPositionGreaterThanOrderByPositionAsc(1L, 1)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.moveStep(2L, StepMoveDirection.DOWN)).getStatusCode());
    }

    private static BotGroup group(Long id, String name, Long pipelineId, Long statusId) {
        BotGroup g = new BotGroup();
        g.setId(id);
        g.setName(name);
        g.setPipelineId(pipelineId);
        g.setStatusId(statusId);
        return g;
    }

    private static BotSequence step(Long id, Long groupId, int position, Long botId) {
        BotSequence s = new BotSequence();
        s.setId(id);
        s.setGroupId(groupId);
        s.setPosition(position);
        s.setBotId(botId);
        return s;
    }
}
