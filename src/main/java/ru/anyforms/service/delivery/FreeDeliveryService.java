package ru.anyforms.service.delivery;

import ru.anyforms.dto.delivery.FreeDeliveryDTO;
import ru.anyforms.dto.delivery.FreeDeliveryUpdateRequest;

public interface FreeDeliveryService {

    FreeDeliveryDTO get();

    FreeDeliveryDTO update(FreeDeliveryUpdateRequest request);

    boolean qualifies(long payableKopecks);
}
