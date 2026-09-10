package ru.anyforms.service.salesbot;

import ru.anyforms.dto.salesbot.BotGroupDTO;
import ru.anyforms.dto.salesbot.BotGroupRequestDTO;
import ru.anyforms.dto.salesbot.BotRunTypeDTO;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;

import java.util.List;

/**
 * Админка дрип-кампании: группы ({@code bot_group}) и их цепочки ботов ({@code bot_sequence}).
 */
public interface BotGroupAdminService {

    /** Справочник типов записей журнала (для фильтров). */
    List<BotRunTypeDTO> runTypes();

    /** Все группы с цепочками, по возрастанию id. */
    List<BotGroupDTO> groups();

    BotGroupDTO createGroup(BotGroupRequestDTO request);

    BotGroupDTO updateGroup(Long id, BotGroupRequestDTO request);

    /** Удаляет группу вместе с цепочкой; журнал остаётся (group_id обнуляется). */
    void deleteGroup(Long id);

    /** Добавляет бота в конец цепочки группы (позиция = последняя + 1). */
    BotStepDTO createStep(BotStepCreateRequestDTO request);

    /** Меняет бота и задержку на шаге; позиция и группа остаются. */
    BotStepDTO updateStep(Long id, BotStepUpdateRequestDTO request);

    /** Меняет шаг местами с соседним (выше или ниже); сосед — по порядку, а не по «±1». */
    List<BotStepDTO> moveStep(Long id, StepMoveDirection direction);

    void deleteStep(Long id);
}
