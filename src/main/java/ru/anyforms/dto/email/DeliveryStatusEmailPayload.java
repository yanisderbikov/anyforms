package ru.anyforms.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.DeliveryNotification;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryStatusEmailPayload {
    private String to;
    private DeliveryNotification notification;
    private String orderPublicId;
    private String customerName;
    private String tracker;
    private String deliveryEta;
    private String pvzCity;
    private String pvzStreet;
    private String supportTelegram;
    private String shopSlug;
    private String shopName;
}
