package ru.anyforms.model;

import java.util.Arrays;
import java.util.List;

public enum DeliveryNotification {
    SHIPPED,
    ARRIVED_AT_PVZ,
    READY_FOR_PICKUP;

    public List<String> earlierNames() {
        return Arrays.stream(values())
                .filter(v -> v.ordinal() < ordinal())
                .map(Enum::name)
                .toList();
    }
}
