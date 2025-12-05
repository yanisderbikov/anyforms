package ru.anyforms.model;

/**
 * Enum со всеми возможными статусами заказа в СДЭК
 * Основан на официальной документации API СДЭК
 * https://apidoc.cdek.ru/#tag/common/Prilozheniya/Prilozhenie-1.-Statusy-zakazov-v-vebhukah
 */
public enum CdekOrderStatus {
    /**
     * Статус код: 1
     * Создан - Заказ зарегистрирован в базе данных СДЭК
     */
    CREATED("CREATED", "Создан"),
    
    /**
     * Статус код: 2
     * Удален - Заказ отменен ИМ после регистрации в системе до прихода груза на склад СДЭК в городе-отправителе
     */
    REMOVED("REMOVED", "Удален"),
    
    /**
     * Статус код: 3
     * Принят на склад отправителя - Оформлен приход на склад СДЭК в городе-отправителе
     */
    RECEIVED_AT_SHIPMENT_WAREHOUSE("RECEIVED_AT_SHIPMENT_WAREHOUSE", "Принят на склад отправителя"),
    
    /**
     * @deprecated Используйте RECEIVED_AT_SHIPMENT_WAREHOUSE
     * Принят - заказ принят в обработку (устаревший статус для обратной совместимости)
     */
    @Deprecated
    ACCEPTED("ACCEPTED", "Принят"),
    
    /**
     * @deprecated Используйте RECEIVED_AT_SHIPMENT_WAREHOUSE
     * Принят на склад отправителя (альтернативный код для обратной совместимости)
     */
    @Deprecated
    ACCEPTED_AT_SHIPMENT_WAREHOUSE("ACCEPTED_AT_SHIPMENT_WAREHOUSE", "Принят на склад отправителя"),
    
    /**
     * @deprecated Используйте RECEIVED_AT_SHIPMENT_WAREHOUSE
     * Доставлен на склад отправителя (устаревший статус для обратной совместимости)
     */
    @Deprecated
    DELIVERED_TO_SHIPMENT_WAREHOUSE("DELIVERED_TO_SHIPMENT_WAREHOUSE", "Доставлен на склад отправителя"),
    
    /**
     * @deprecated Используйте RECEIVED_AT_SHIPMENT_WAREHOUSE
     * Принят в городе отправителя (устаревший статус для обратной совместимости)
     */
    @Deprecated
    RECEIVED_IN_SENDER_CITY("RECEIVED_IN_SENDER_CITY", "Принят в городе отправителя"),
    
    /**
     * @deprecated Используйте READY_TO_SHIP_AT_SENDING_OFFICE или TAKEN_BY_COURIER
     * Передан на доставку в городе отправителя (устаревший статус для обратной совместимости)
     */
    @Deprecated
    TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY("TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY", "Передан на доставку в городе отправителя"),
    
    /**
     * Статус код: 4
     * Вручен - Успешно доставлен и вручен адресату (конечный статус)
     */
    DELIVERED("DELIVERED", "Вручен"),
    
    /**
     * @deprecated Используйте DELIVERED
     * Вручен (альтернативное название для обратной совместимости)
     */
    @Deprecated
    HANDED_TO("HANDED_TO", "Вручен"),
    
    /**
     * Статус код: 5
     * Не вручен - Покупатель отказался от покупки, возврат в ИМ (конечный статус)
     */
    NOT_DELIVERED("NOT_DELIVERED", "Не вручен"),
    
    /**
     * Статус код: 6
     * Выдан на отправку в г. отправителе - Оформлен расход со склада СДЭК в городе-отправителе. 
     * Груз подготовлен к отправке (консолидирован с другими посылками)
     * Альтернативное название: READY_FOR_SHIPMENT_IN_SENDER_CITY - Готов к отправке в городе отправителе
     */
    READY_TO_SHIP_AT_SENDING_OFFICE("READY_TO_SHIP_AT_SENDING_OFFICE", "Выдан на отправку в г. отправителе"),
    READY_FOR_SHIPMENT_IN_TRANSIT_CITY("READY_FOR_SHIPMENT_IN_TRANSIT_CITY", "Выдан на отправку в г. отправителе"),
    READY_FOR_SHIPMENT_IN_SENDER_CITY("READY_FOR_SHIPMENT_IN_SENDER_CITY", "Готов к отправке в городе отправителе"),
    
    /**
     * Статус код: 7
     * Сдан перевозчику в г. отправителе - Зарегистрирована отправка в городе-отправителе. 
     * Консолидированный груз передан на доставку (в аэропорт/загружен машину)
     * Альтернативное название: TAKEN_BY_TRANSPORTER_FROM_SENDER_CITY - Сдан перевозчику в городе отправителе
     */
    PASSED_TO_CARRIER_AT_SENDING_OFFICE("PASSED_TO_CARRIER_AT_SENDING_OFFICE", "Сдан перевозчику в г. отправителе"),
    TAKEN_BY_TRANSPORTER_FROM_SENDER_CITY("TAKEN_BY_TRANSPORTER_FROM_SENDER_CITY", "Сдан перевозчику в городе отправителе"),
    
    /**
     * Статус код: 8
     * Отправлен в город-получатель - Зарегистрирована отправка в город получателя, заказ в пути
     */
    SENT_TO_RECIPIENT_CITY("SENT_TO_RECIPIENT_CITY", "Отправлен в город-получатель"),
    
    /**
     * Статус код: 9
     * Встречен в г. получателе - Зарегистрирована встреча груза в городе-получателе
     * Альтернативное название: ACCEPTED_IN_RECIPIENT_CITY - Встречен в городе-получателе
     */
    MET_AT_RECIPIENT_OFFICE("MET_AT_RECIPIENT_OFFICE", "Встречен в г. получателе"),
    ACCEPTED_IN_RECIPIENT_CITY("ACCEPTED_IN_RECIPIENT_CITY", "Встречен в городе-получателе"),
    
    /**
     * Статус код: 10
     * Принят на склад доставки - Оформлен приход на склад города-получателя, ожидает доставки до двери
     */
    ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE("ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE", "Принят на склад доставки"),
    
    /**
     * @deprecated Используйте ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE
     * Принят на склад доставки (альтернативный код для обратной совместимости)
     */
    @Deprecated
    RECEIVED_AT_DELIVERY_WAREHOUSE("RECEIVED_AT_DELIVERY_WAREHOUSE", "Принят на склад доставки"),
    
    /**
     * @deprecated Используйте ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE
     * Принят на склад доставки (альтернативный код для обратной совместимости)
     */
    @Deprecated
    ACCEPTED_AT_DELIVERY_WAREHOUSE("ACCEPTED_AT_DELIVERY_WAREHOUSE", "Принят на склад доставки"),
    
    /**
     * @deprecated Используйте ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE
     * Доставлен на склад доставки (устаревший статус для обратной совместимости)
     */
    @Deprecated
    DELIVERED_TO_DELIVERY_WAREHOUSE("DELIVERED_TO_DELIVERY_WAREHOUSE", "Доставлен на склад доставки"),
    
    /**
     * @deprecated Используйте MET_AT_RECIPIENT_OFFICE
     * Прибыл в город получателя (устаревший статус для обратной совместимости)
     */
    @Deprecated
    ARRIVED_IN_RECIPIENT_CITY("ARRIVED_IN_RECIPIENT_CITY", "Прибыл в город получателя"),
    
    /**
     * @deprecated Используйте TAKEN_BY_COURIER
     * Передан на доставку в городе получателя (устаревший статус для обратной совместимости)
     */
    @Deprecated
    TRANSFERRED_TO_DELIVERY_IN_RECIPIENT_CITY("TRANSFERRED_TO_DELIVERY_IN_RECIPIENT_CITY", "Передан на доставку в городе получателя"),
    
    /**
     * Статус код: 11
     * Выдан на доставку - Добавлен в курьерскую карту, выдан курьеру на доставку
     */
    TAKEN_BY_COURIER("TAKEN_BY_COURIER", "Выдан на доставку"),
    
    /**
     * @deprecated Используйте TAKEN_BY_COURIER
     * Выдан на доставку (альтернативное название для обратной совместимости)
     */
    @Deprecated
    ISSUED_FOR_DELIVERY("ISSUED_FOR_DELIVERY", "Выдан на доставку"),
    
    /**
     * Статус код: 12
     * Принят на склад до востребования - Оформлен приход на склад города-получателя. 
     * Доставка до склада, посылка ожидает забора клиентом - покупателем ИМ
     */
    ACCEPTED_AT_PICK_UP_POINT("ACCEPTED_AT_PICK_UP_POINT", "Принят на склад до востребования"),
    
    /**
     * @deprecated Используйте ACCEPTED_AT_PICK_UP_POINT
     * Доставлен в пункт выдачи (устаревший статус для обратной совместимости)
     */
    @Deprecated
    DELIVERED_TO_PICKUP_POINT("DELIVERED_TO_PICKUP_POINT", "Доставлен в пункт выдачи"),
    
    /**
     * Статус код: 13
     * Принят на склад транзита - Оформлен приход в городе-транзите
     */
    ACCEPTED_AT_TRANSIT_WAREHOUSE("ACCEPTED_AT_TRANSIT_WAREHOUSE", "Оформлен приход в городе-транзите"),
    
    /**
     * Статус код: 16
     * Возвращен на склад отправителя - Повторно оформлен приход в городе-отправителе 
     * (не удалось передать перевозчику по какой-либо причине).
     * Примечание: этот статус не означает возврат груза отправителю.
     */
    RETURNED_TO_SENDER_CITY_WAREHOUSE("RETURNED_TO_SENDER_CITY_WAREHOUSE", "Возвращен на склад отправителя"),
    
    /**
     * Статус код: 17
     * Возвращен на склад транзита - Повторно оформлен приход в городе-транзите (груз возвращен на склад).
     * Примечание: этот статус не означает возврат груза отправителю.
     */
    RETURNED_TO_TRANSIT_WAREHOUSE("RETURNED_TO_TRANSIT_WAREHOUSE", "Возвращен на склад транзита"),
    
    /**
     * Статус код: 18
     * Возвращен на склад доставки - Оформлен повторный приход на склад в городе-получателе. 
     * Доставка не удалась по какой-либо причине, ожидается очередная попытка доставки.
     * Примечание: этот статус не означает возврат груза отправителю
     */
    RETURNED_TO_RECIPIENT_CITY_WAREHOUSE("RETURNED_TO_RECIPIENT_CITY_WAREHOUSE", "Возвращен на склад доставки"),
    
    /**
     * Статус код: 19
     * Выдан на отправку в г. транзите - Оформлен расход в городе-транзите
     */
    READY_TO_SHIP_IN_TRANSIT_OFFICE("READY_TO_SHIP_IN_TRANSIT_OFFICE", "Выдан на отправку в г. транзите"),
    
    /**
     * Статус код: 20
     * Сдан перевозчику в г. транзите - Зарегистрирована отправка у перевозчика в городе-транзите
     * Альтернативное название: TAKEN_BY_TRANSPORTER_FROM_TRANSIT_CITY - Сдан перевозчику в городе-транзите
     */
    PASSED_TO_CARRIER_AT_TRANSIT_OFFICE("PASSED_TO_CARRIER_AT_TRANSIT_OFFICE", "Сдан перевозчику в г. транзите"),
    TAKEN_BY_TRANSPORTER_FROM_TRANSIT_CITY("TAKEN_BY_TRANSPORTER_FROM_TRANSIT_CITY", "Сдан перевозчику в городе-транзите"),
    
    /**
     * Статус код: 21
     * Отправлен в г. транзит - Зарегистрирована отправка в город-транзит. Проставлены дата и время отправления у перевозчика
     * Альтернативное название: SENT_TO_TRANSIT_CITY - Отправлен в город-транзит
     */
    SEND_TO_TRANSIT_OFFICE("SEND_TO_TRANSIT_OFFICE", "Отправлен в г. транзит"),
    SENT_TO_TRANSIT_CITY("SENT_TO_TRANSIT_CITY", "Отправлен в город-транзит"),
    
    /**
     * Статус код: 22
     * Встречен в г. транзите - Зарегистрирована встреча в городе-транзите
     * Альтернативное название: ACCEPTED_IN_TRANSIT_CITY - Встречен в городе-транзите
     */
    MET_AT_TRANSIT_OFFICE("MET_AT_TRANSIT_OFFICE", "Встречен в г. транзите"),
    ACCEPTED_IN_TRANSIT_CITY("ACCEPTED_IN_TRANSIT_CITY", "Встречен в городе-транзите"),
    
    /**
     * Статус код: 27
     * Отправлен в г. отправитель - Зарегистрирована отправка в город-отправитель, груз в пути
     */
    SENT_TO_SENDER_CITY("SENT_TO_SENDER_CITY", "Отправлен в г. отправитель"),
    
    /**
     * Статус код: 28
     * Встречен в г. отправителе - Зарегистрирована встреча груза в городе-отправителе
     */
    MET_AT_SENDING_OFFICE("MET_AT_SENDING_OFFICE", "Встречен в г. отправителе"),
    
    /**
     * Статус код: 1000
     * Поступил в г. транзита - Оформлена приемка в городе-транзите
     */
    ENTERED_TO_OFFICE_TRANSIT_WAREHOUSE("ENTERED_TO_OFFICE_TRANSIT_WAREHOUSE", "Поступил в г. транзита"),
    
    /**
     * Статус код: 1000
     * Поступил на склад доставки - Оформлена приемка на складе города получателя по заказу до двери
     */
    ENTERED_TO_DELIVERY_WAREHOUSE("ENTERED_TO_DELIVERY_WAREHOUSE", "Поступил на склад доставки"),
    
    /**
     * Статус код: 1000
     * Поступил на склад до востребования - Оформлена приемка на складе города получателя по заказу до склада
     */
    ENTERED_TO_WAREHOUSE_ON_DEMAND("ENTERED_TO_WAREHOUSE_ON_DEMAND", "Поступил на склад до востребования"),
    
    /**
     * Статус код: 1000
     * Таможенное оформление в стране отправления - В процессе таможенного оформления в стране отправителя (для международных заказов)
     */
    IN_CUSTOMS_INTERNATIONAL("IN_CUSTOMS_INTERNATIONAL", "Таможенное оформление в стране отправления"),
    
    /**
     * Статус код: 1000
     * Отправлено в страну назначения - Отправлен в страну назначения, заказ в пути (для международных заказов)
     */
    SHIPPED_TO_DESTINATION("SHIPPED_TO_DESTINATION", "Отправлено в страну назначения"),
    
    /**
     * Статус код: 1000
     * Передано транзитному перевозчику - Передано транзитному перевозчику
     */
    PASSED_TO_TRANSIT_CARRIER("PASSED_TO_TRANSIT_CARRIER", "Передано транзитному перевозчику"),
    
    /**
     * Статус код: 1000
     * Таможенное оформление в стране назначения - Таможенное оформление в стране назначения
     */
    IN_CUSTOMS_LOCAL("IN_CUSTOMS_LOCAL", "Таможенное оформление в стране назначения"),
    
    /**
     * Статус код: 1000
     * Таможенное оформление завершено - Завершено таможенное оформление заказа (для международных заказов)
     */
    CUSTOMS_COMPLETE("CUSTOMS_COMPLETE", "Таможенное оформление завершено"),
    
    /**
     * Статус код: 1000
     * Заложен в постамат - Заложен в постамат, заказ ожидает забора клиентом - покупателем ИМ
     */
    POSTOMAT_POSTED("POSTOMAT_POSTED", "Заложен в постамат"),
    
    /**
     * Статус код: 1000
     * Изъят из постамата курьером - Истек срок хранения заказа в постамате, возврат в ИМ
     */
    POSTOMAT_SEIZED("POSTOMAT_SEIZED", "Изъят из постамата курьером"),
    
    /**
     * Статус код: 1000
     * Изъят из постамата клиентом - Успешно изъят из постамата клиентом - покупателем ИМ
     */
    POSTOMAT_RECEIVED("POSTOMAT_RECEIVED", "Изъят из постамата клиентом"),
    
    /**
     * Специальный статус для обработки ошибок API
     * Заказ не найден или уже доставлен - используется при ошибках API СДЭК
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
