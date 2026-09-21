package ru.anyforms.service;

import ru.anyforms.dto.cdek.CdekDeliveryEta;

public interface DeliveryEtaResolver {

    CdekDeliveryEta resolve(String tracker);
}
