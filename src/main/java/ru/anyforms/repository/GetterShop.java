package ru.anyforms.repository;

import ru.anyforms.model.marketplace.Shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterShop {

    List<Shop> getAllShops();

    List<Shop> getActiveShops();

    Optional<Shop> getBySlug(String slug);

    Optional<Shop> getById(UUID id);
}
