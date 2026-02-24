package ru.anyforms.model.amo;

/**
 * Enum со всеми статусами сделок в AmoCRM
 * Используется для удобной работы со статусами вместо хардкода числовых значений
 */
public enum AmoTaskId {

    REACH_OUT(1L, "Связаться"),
    MEETING(2L, "Встреча"),
    LOST_MESSAGE(3986070L, "Пропущенное"),
    UNKNOWN(null, "Неизвестный статус");

    private final Long taskId;
    private final String name;

    AmoTaskId(Long taskId, String name) {
        this.taskId = taskId;
        this.name = name;
    }
    
    /**
     * Возвращает ID статуса
     * @return ID статуса или null, если статус должен быть настроен через конфигурацию
     */
    public Long getTaskId() {
        return taskId;
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
    public static AmoTaskId fromStatusId(Long statusId) {
        if (statusId == null) {
            return UNKNOWN;
        }
        
        for (AmoTaskId status : values()) {
            if (status.taskId != null && status.taskId.equals(statusId)) {
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
        if (this.taskId == null || statusId == null) {
            return false;
        }
        return this.taskId.equals(statusId);
    }
}

