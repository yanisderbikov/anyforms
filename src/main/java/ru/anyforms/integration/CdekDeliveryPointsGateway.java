package ru.anyforms.integration;

import ru.anyforms.dto.cdek.CdekPvzDTO;

import java.util.List;

public interface CdekDeliveryPointsGateway {

    List<CdekPvzDTO> fetchAllPickupPoints();
}
