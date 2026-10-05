package ru.anyforms.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetterPromoPopupView {

    long countForDevice(UUID popupId, String deviceId);

    Optional<Instant> lastViewAt(UUID popupId, String deviceId);

    Map<UUID, PopupViewStats> statsByPopup();
}
