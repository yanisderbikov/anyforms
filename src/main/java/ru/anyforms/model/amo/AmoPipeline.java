package ru.anyforms.model.amo;

/**
 * Enum со всеми статусами сделок в AmoCRM
 * Используется для удобной работы со статусами вместо хардкода числовых значений
 */
public enum AmoPipeline {

    TRASH(9855286L, "Мусор"),
    MAIN(9784138L, "Под заказ"),
    RETAIL(10557858L, "Розница"),
    UNKNOWN(null, "Неизвестный статус");

    private final Long pipelineId;
    private final String description;

    AmoPipeline(Long pipelineId, String description) {
        this.pipelineId = pipelineId;
        this.description = description;
    }
    
    /**
     * Возвращает ID статуса
     * @return ID статуса или null, если статус должен быть настроен через конфигурацию
     */
    public Long getPipelineId() {
        return pipelineId;
    }
    
    /**
     * Возвращает описание статуса на русском языке
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Находит статус по ID
     * @param statusId ID статуса
     * @return соответствующий статус или UNKNOWN, если ID не найден
     */
    public static AmoPipeline fromStatusId(Long statusId) {
        if (statusId == null) {
            return UNKNOWN;
        }
        
        for (AmoPipeline status : values()) {
            if (status.pipelineId != null && status.pipelineId.equals(statusId)) {
                return status;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * Проверяет, соответствует ли ID статуса данному статусу
     * @param statusId ID статуса для проверки
     * @return true если ID соответствует данному статусу
     */
    public boolean matches(Long statusId) {
        if (this.pipelineId == null || statusId == null) {
            return false;
        }
        return this.pipelineId.equals(statusId);
    }
}

