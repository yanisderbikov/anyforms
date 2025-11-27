package ru.anyforms.util;

import ru.anyforms.model.CdekOrderStatus;

/**
 * Утилитный класс для проверки статусов заказов CDEK
 */
public class CdekStatusHelper {
    
    /**
     * Проверяет, является ли статус "принят на доставку" (после Created)
     * @param status статус заказа CDEK
     * @return true если статус означает "принят на доставку"
     */
    public static boolean isAcceptedForDelivery(CdekOrderStatus status) {
        if (status == null || status == CdekOrderStatus.UNKNOWN) {
            return false;
        }
        // Статусы, которые означают "принят на доставку" после Created
        return status == CdekOrderStatus.ACCEPTED ||
               status == CdekOrderStatus.RECEIVED_AT_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.ACCEPTED_AT_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.DELIVERED_TO_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.RECEIVED_IN_SENDER_CITY ||
               status == CdekOrderStatus.TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY;
    }
    
    /**
     * Проверяет, является ли статус "доставлен" (можно забрать)
     * @param status статус заказа CDEK
     * @return true если статус означает "доставлен"
     */
    public static boolean isReadyToPickUp(CdekOrderStatus status) {
        if (status == null || status == CdekOrderStatus.UNKNOWN) {
            return false;
        }
        return status == CdekOrderStatus.DELIVERED_TO_PICKUP_POINT ||
               status == CdekOrderStatus.ACCEPTED_AT_PICK_UP_POINT;
    }
    
    /**
     * Проверяет, является ли статус "вручен" (клиент забрал)
     * @param status статус заказа CDEK
     * @return true если статус означает "вручен"
     */
    public static boolean isDelivered(CdekOrderStatus status) {
        if (status == null || status == CdekOrderStatus.UNKNOWN) {
            return false;
        }
        return status == CdekOrderStatus.HANDED_TO || status == CdekOrderStatus.DELIVERED;
    }
}



