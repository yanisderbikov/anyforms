package ru.anyforms.model.amo;

/**
 * Enum со всеми статусами сделок в AmoCRM
 * Используется для удобной работы со статусами вместо хардкода числовых значений
 */
public enum AmoTaskResponsibleUser {

    IAN(12675018L, "Ян"),
    IRINA(13161462L, "Ирина"),
    UNKNOWN(null, "Неизвестный статус");

    private final Long responsibleUserId;
    private final String name;

    AmoTaskResponsibleUser(Long responsibleUserId, String name) {
        this.responsibleUserId = responsibleUserId;
        this.name = name;
    }
    
    /**
     * Возвращает ID статуса
     * @return ID статуса или null, если статус должен быть настроен через конфигурацию
     */
    public Long getResponsibleUserId() {
        return responsibleUserId;
    }
    
    /**
     * Возвращает описание статуса на русском языке
     */
    public String getName() {
        return name;
    }
    
    /**
     * Находит статус по ID
     * @param statusId ID статуса
     * @return соответствующий статус или UNKNOWN, если ID не найден
     */
    public static AmoTaskResponsibleUser fromStatusId(Long statusId) {
        if (statusId == null) {
            return UNKNOWN;
        }
        
        for (AmoTaskResponsibleUser status : values()) {
            if (status.responsibleUserId != null && status.responsibleUserId.equals(statusId)) {
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
        if (this.responsibleUserId == null || statusId == null) {
            return false;
        }
        return this.responsibleUserId.equals(statusId);
    }
}

