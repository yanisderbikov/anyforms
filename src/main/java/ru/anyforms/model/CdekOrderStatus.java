package ru.anyforms.model;

/**
 * Enum со всеми возможными статусами заказа в СДЭК
 * Основан на официальной документации API СДЭК
 * https://apidoc.cdek.ru/#tag/common/Prilozheniya/Prilozhenie-1.-Statusy-zakazov-v-vebhukah
 */
public enum CdekOrderStatus {
    /**
     * Создан - заказ создан, но еще не передан в обработку
     */
    CREATED("CREATED", "Создан"),
    
    /**
     * Принят - заказ принят в обработку
     */
    ACCEPTED("ACCEPTED", "Принят"),
    
    /**
     * Принят на склад отправителя
     */
    RECEIVED_AT_SHIPMENT_WAREHOUSE("RECEIVED_AT_SHIPMENT_WAREHOUSE", "Принят на склад отправителя"),
    
    /**
     * Доставлен на склад отправителя
     */
    DELIVERED_TO_SHIPMENT_WAREHOUSE("DELIVERED_TO_SHIPMENT_WAREHOUSE", "Доставлен на склад отправителя"),
    
    /**
     * Принят на склад отправителя (альтернативный код)
     */
    ACCEPTED_AT_SHIPMENT_WAREHOUSE("ACCEPTED_AT_SHIPMENT_WAREHOUSE", "Принят на склад отправителя"),
    
    /**
     * Недействителен
     */
    INVALID("INVALID", "Недействителен"),
    
    /**
     * Принят на склад доставки
     */
    RECEIVED_AT_DELIVERY_WAREHOUSE("RECEIVED_AT_DELIVERY_WAREHOUSE", "Принят на склад доставки"),
    
    /**
     * Принят на склад доставки (альтернативный код)
     */
    ACCEPTED_AT_DELIVERY_WAREHOUSE("ACCEPTED_AT_DELIVERY_WAREHOUSE", "Принят на склад доставки"),
    
    /**
     * Доставлен на склад доставки
     */
    DELIVERED_TO_DELIVERY_WAREHOUSE("DELIVERED_TO_DELIVERY_WAREHOUSE", "Доставлен на склад доставки"),
    
    /**
     * Принят в пункте выдачи
     */
    ACCEPTED_AT_PICKUP_POINT("ACCEPTED_AT_PICKUP_POINT", "Принят в пункте выдачи"),
    
    /**
     * Доставлен в пункт выдачи
     */
    DELIVERED_TO_PICKUP_POINT("DELIVERED_TO_PICKUP_POINT", "Доставлен в пункт выдачи"),
    
    /**
     * Доставлен - посылка успешно доставлена получателю
     */
    DELIVERED("DELIVERED", "Доставлен"),
    
    /**
     * Вручен - посылка вручена получателю
     */
    HANDED_TO("HANDED_TO", "Вручен"),
    
    /**
     * Не доставлен
     */
    NOT_DELIVERED("NOT_DELIVERED", "Не доставлен"),
    
    /**
     * Отменен
     */
    CANCELLED("CANCELLED", "Отменен"),
    
    /**
     * Возвращен отправителю
     */
    RETURNED_TO_SENDER("RETURNED_TO_SENDER", "Возвращен отправителю"),
    
    /**
     * Принят в городе отправителя
     */
    RECEIVED_IN_SENDER_CITY("RECEIVED_IN_SENDER_CITY", "Принят в городе отправителя"),
    
    /**
     * Передан на доставку в городе отправителя
     */
    TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY("TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY", "Передан на доставку в городе отправителя"),
    
    /**
     * Прибыл в город получателя
     */
    ARRIVED_IN_RECIPIENT_CITY("ARRIVED_IN_RECIPIENT_CITY", "Прибыл в город получателя"),
    
    /**
     * Передан на доставку в городе получателя
     */
    TRANSFERRED_TO_DELIVERY_IN_RECIPIENT_CITY("TRANSFERRED_TO_DELIVERY_IN_RECIPIENT_CITY", "Передан на доставку в городе получателя"),
    
    /**
     * Выдан на доставку
     */
    ISSUED_FOR_DELIVERY("ISSUED_FOR_DELIVERY", "Выдан на доставку"),
    
    /**
     * Заказ не найден или уже доставлен
     */
    NOT_FOUND_OR_DELIVERED("NOT_FOUND_OR_DELIVERED", "Заказ не найден или доставлен"),
    
    /**
     * Неизвестный статус (для случаев, когда код не распознан)
     */
    UNKNOWN("UNKNOWN", "Неизвестный статус");
    
    private final String code;
    private final String description;
    
    CdekOrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * Возвращает код статуса
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Возвращает описание статуса на русском языке
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Находит статус по коду
     * @param code код статуса
     * @return соответствующий статус или UNKNOWN, если код не найден
     */
    public static CdekOrderStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return UNKNOWN;
        }
        
        String normalizedCode = code.trim().toUpperCase();
        
        for (CdekOrderStatus status : values()) {
            if (status.code.equals(normalizedCode)) {
                return status;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * Находит статус по коду или названию
     * @param codeOrName код или название статуса
     * @return соответствующий статус или UNKNOWN, если не найден
     */
    public static CdekOrderStatus fromCodeOrName(String codeOrName) {
        if (codeOrName == null || codeOrName.trim().isEmpty()) {
            return UNKNOWN;
        }
        
        String normalized = codeOrName.trim().toUpperCase();
        
        // Сначала пробуем найти по коду
        for (CdekOrderStatus status : values()) {
            if (status.code.equals(normalized)) {
                return status;
            }
        }
        
        // Затем пробуем найти по описанию (без учета регистра)
        for (CdekOrderStatus status : values()) {
            if (status.description.toUpperCase().equals(normalized)) {
                return status;
            }
        }
        
        return UNKNOWN;
    }
}

