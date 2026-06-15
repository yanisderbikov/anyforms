package ru.anyforms.service.salesbot;

import java.util.List;

/**
 * Поставщик лидов в целевом статусе (запрос №1 к amoCRM).
 * Абстрагирует оркестратор от деталей amo-клиента (DIP).
 */
public interface LeadProvider {

    /**
     * Возвращает ID всех лидов, находящихся в заданной воронке/статусе.
     * <p>
     * TODO(пагинация): сейчас без постраничной выборки (лидов десятки). При росте объёма
     * добавить пагинацию по amoCRM ({@code page}/{@code limit}).
     *
     * @param target целевая воронка/статус
     * @return список lead_id (может быть пустым)
     */
    List<Long> leadsInStatus(FunnelTarget target);
}
