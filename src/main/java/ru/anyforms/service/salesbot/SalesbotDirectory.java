package ru.anyforms.service.salesbot;

import ru.anyforms.model.amo.AmoSalesbot;

import java.util.List;
import java.util.Map;

/**
 * Справочник SalesBot'ов аккаунта amoCRM для админки: выбор бота по имени и подписи
 * к {@code bot_id} в цепочках, аналитике и журнале. Ответ amoCRM кэшируется на несколько
 * минут — список ботов меняется редко, а страницы админки дёргают его часто.
 */
public interface SalesbotDirectory {

    /**
     * Все боты аккаунта. При {@code refresh} кэш игнорируется.
     *
     * @throws IllegalStateException если amoCRM недоступен и кэша ещё нет
     */
    List<AmoSalesbot> bots(boolean refresh);

    /**
     * {@code bot_id → название}. Никогда не бросает: при недоступности amoCRM и пустом кэше
     * возвращает пустую карту — страницы админки должны открываться и без имён.
     */
    Map<Long, String> namesById();
}
