package ru.anyforms.model;

/**
 * Enum со всеми статусами сделок в AmoCRM
 * Используется для удобной работы со статусами вместо хардкода числовых значений
 */
public enum AmoLeadStatus {

    /**
     * Оплачен - заказ оплачен клиентом
     * Значение может быть переопределено через amocrm.status.paid.id в application.properties
     */
    PAID(78318930L, "Оплачен"),

    /**
     * Готов к отправке - заказ готов к отправке
     * Значение может быть переопределено через amocrm.status.ready.to.ship.id в application.properties
     */
    READY_TO_SHIP(78318938L, "Готов к отправке"),

    /**
     * Отправлен - заказ был отправлен клиенту
     */
    SENT(78318946L, "Отправлен"),

    /**
     * Неизвестный статус (для случаев, когда статус не распознан)
     */
    UNKNOWN(null, "Неизвестный статус");
    
    private final Long statusId;
    private final String description;
    
    AmoLeadStatus(Long statusId, String description) {
        this.statusId = statusId;
        this.description = description;
    }
    
    /**
     * Возвращает ID статуса
     * @return ID статуса или null, если статус должен быть настроен через конфигурацию
     */
    public Long getStatusId() {
        return statusId;
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
    public static AmoLeadStatus fromStatusId(Long statusId) {
        if (statusId == null) {
            return UNKNOWN;
        }
        
        for (AmoLeadStatus status : values()) {
            if (status.statusId != null && status.statusId.equals(statusId)) {
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
        if (this.statusId == null || statusId == null) {
            return false;
        }
        return this.statusId.equals(statusId);
    }
}

