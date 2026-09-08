package ru.anyforms.service.salesbot;

import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.FunnelDTO;
import ru.anyforms.dto.salesbot.FunnelRequestDTO;
import ru.anyforms.dto.salesbot.OrderTypeDTO;
import ru.anyforms.dto.salesbot.SalesbotTypeConfigDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;

import java.util.List;

/**
 * Админка настроек дрип-кампании: воронки типов ({@code order_type_funnel})
 * и цепочки ботов ({@code bot_sequence}). Раньше это правилось руками в БД.
 */
public interface SalesbotConfigAdminService {

    /** Справочник типов заказа с подписями (для выпадающих списков). */
    List<OrderTypeDTO> orderTypes();

    /**
     * Сводка по типам: воронка + цепочка. Дрип-типы отдаются всегда (даже пустые),
     * служебные ({@code MANUAL}, {@code DELIVERY}) — только если по ним что-то заведено.
     */
    List<SalesbotTypeConfigDTO> config();

    FunnelDTO createFunnel(FunnelRequestDTO request);

    FunnelDTO updateFunnel(Long id, FunnelRequestDTO request);

    /** Удаляет воронку типа: тип перестаёт обрабатываться, цепочка остаётся. */
    void deleteFunnel(Long id);

    /** Добавляет бота в конец цепочки типа (позиция = последняя + 1). */
    BotStepDTO createStep(BotStepCreateRequestDTO request);

    /** Меняет бота на шаге; позиция и тип остаются. */
    BotStepDTO updateStep(Long id, BotStepUpdateRequestDTO request);

    /**
     * Меняет шаг местами с соседним (выше или ниже). Позиции могут идти с пропусками —
     * сосед определяется по порядку, а не по «±1».
     *
     * @return цепочка типа после перестановки
     */
    List<BotStepDTO> moveStep(Long id, StepMoveDirection direction);

    void deleteStep(Long id);
}
