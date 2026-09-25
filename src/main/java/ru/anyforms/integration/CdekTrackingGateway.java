package ru.anyforms.integration;

import ru.anyforms.dto.cdek.CdekOrderInfo;

/**
 * Интерфейс для работы с CDEK Tracking API
 */
public interface CdekTrackingGateway {
    /**
     * Проверяет статус трекера СДЭК
     */
    String checkTrackingStatus(String trackingNumber);

    /**
     * Получает токен доступа через OAuth 2.0
     */
    String getAccessToken();

    /**
     * Получает код статуса заказа из API СДЭК
     */
    String getOrderStatus(String trackingNumber);

    /**
     * Данные заказа из API СДЭК для оценки срока доставки: плановая дата, тариф, точки и упаковки. Null при ошибке
     */
    CdekOrderInfo getOrderInfo(String trackingNumber);

    /**
     * Проверяет, является ли строка валидным трекером СДЭК
     */
    boolean isValidTrackingNumber(String value);
}
