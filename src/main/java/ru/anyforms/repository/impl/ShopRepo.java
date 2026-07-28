package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.marketplace.Shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface ShopRepo extends JpaRepository<Shop, UUID> {

    Optional<Shop> findBySlug(String slug);

    List<Shop> findByActiveIsTrueOrderByNameAsc();

    List<Shop> findAllByOrderByNameAsc();
}
