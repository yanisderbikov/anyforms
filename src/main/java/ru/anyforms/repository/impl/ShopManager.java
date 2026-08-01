package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class ShopManager implements GetterShop {

    private final ShopRepo shopRepo;

    @Override
    public List<Shop> getAllShops() {
        try {
            return shopRepo.findAllByOrderByNameAsc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Shop> getActiveShops() {
        try {
            return shopRepo.findByActiveIsTrueOrderByNameAsc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Shop> getBySlug(String slug) {
        try {
            return slug == null || slug.isBlank()
                    ? Optional.empty()
                    : shopRepo.findBySlug(slug.trim());
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
