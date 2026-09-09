package ru.anyforms.model.amo;

/**
 * Дополнительный отбор лидов внутри воронки/статуса: по тегу, по значению кастомного поля
 * сделки и/или по дате закрытия. Все части необязательны; {@link #NONE} — без отбора.
 *
 * @param tagName      имя тега (сравнение без учёта регистра); {@code null} — не фильтровать
 * @param fieldId      ID кастомного поля сделки; {@code null} — не фильтровать по полю
 * @param fieldValue   ожидаемое значение поля. {@code "true"}/{@code "false"} трактуются как
 *                     чекбокс (отсутствие поля у лида = «нет»); иначе — совпадение с value /
 *                     enum_code / enum_id без учёта регистра
 * @param closedBefore только сделки, закрытые не позже этого момента (epoch seconds, как
 *                     {@code closed_at} в amoCRM); {@code null} — не фильтровать. Уходит в запрос
 *                     как {@code filter[closed_at][to]}
 */
public record LeadFilter(String tagName, Long fieldId, String fieldValue, Long closedBefore) {

    public static final LeadFilter NONE = new LeadFilter(null, null, null, null);

    public LeadFilter(String tagName, Long fieldId, String fieldValue) {
        this(tagName, fieldId, fieldValue, null);
    }

    public static LeadFilter byTag(String tagName) {
        return new LeadFilter(normalize(tagName), null, null, null);
    }

    /** Сделки, закрытые не позже {@code closedBefore} (epoch seconds). */
    public static LeadFilter closedBefore(long closedBefore) {
        return new LeadFilter(null, null, null, closedBefore);
    }

    /**
     * Отбор для ручного запуска: тег + признак «Розница» (поле {@link AmoCrmFieldId#RETAIL}).
     *
     * @param retail {@code true} — только розница, {@code false} — только не розница, {@code null} — любые
     */
    public static LeadFilter forManualRun(String tagName, Boolean retail) {
        return new LeadFilter(normalize(tagName),
                retail == null ? null : AmoCrmFieldId.RETAIL.getId(),
                retail == null ? null : retail.toString(),
                null);
    }

    public boolean hasTag() {
        return tagName != null;
    }

    public boolean hasField() {
        return fieldId != null && fieldValue != null;
    }

    public boolean hasClosedBefore() {
        return closedBefore != null;
    }

    private static String normalize(String tagName) {
        return tagName == null || tagName.isBlank() ? null : tagName.trim();
    }
}
