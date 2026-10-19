package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.AdminShopCreateRequestDTO;
import ru.anyforms.dto.marketplace.AdminShopDTO;
import ru.anyforms.dto.marketplace.AdminShopUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.repository.SaverShop;
import ru.anyforms.service.product.ShopService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@Log4j2
class ShopServiceImpl implements ShopService {

    private static final Set<String> RESERVED_SLUGS = Set.of("product", "cart", "checkout", "success", "offer", "admin");

    private final GetterShop getterShop;
    private final SaverShop saverShop;

    @Override
    public List<ShopDTO> getActiveShops() {
        return getterShop.getActiveShops().stream()
                .map(shop -> new ShopDTO(shop.getId(), shop.getSlug(), shop.getName(), shop.getActive(),
                        shop.getSupportTelegram()))
                .toList();
    }

    @Override
    public Shop resolveBySlug(String slug) {
        String normalized = slug == null || slug.isBlank() ? Shop.DEFAULT_SLUG : slug.trim();
        Shop shop = getterShop.getBySlug(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден: " + normalized));
        if (Boolean.FALSE.equals(shop.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин отключён: " + normalized);
        }
        return shop;
    }

    @Override
    public List<AdminShopDTO> getAllShopsForAdmin() {
        return getterShop.getAllShops().stream().map(ShopServiceImpl::toAdminDto).toList();
    }

    @Override
    public AdminShopDTO create(AdminShopCreateRequestDTO request) {
        String slug = normalizeSlug(request.getSlug());
        if (getterShop.getBySlug(slug).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Магазин с адресом " + slug + " уже есть");
        }
        Shop shop = Shop.builder()
                .slug(slug)
                .name(request.getName().trim())
                .supportTelegram(normalizeSupportTelegram(request.getSupportTelegram()))
                .amoContactValue(normalizeAmoContactValue(request.getAmoContactValue()))
                .build();
        Shop saved = saverShop.save(shop);
        log.info("Создан магазин витрины {} (id={})", saved.getSlug(), saved.getId());
        return toAdminDto(saved);
    }

    @Override
    public AdminShopDTO update(UUID id, AdminShopUpdateRequestDTO request) {
        Shop shop = getterShop.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден"));
        boolean defaultShop = Shop.DEFAULT_SLUG.equals(shop.getSlug());
        if (defaultShop && Boolean.FALSE.equals(request.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Магазин anyforms выключить нельзя — это общая витрина /shop");
        }
        shop.setName(request.getName().trim());
        shop.setSupportTelegram(normalizeSupportTelegram(request.getSupportTelegram()));
        shop.setAmoContactValue(normalizeAmoContactValue(request.getAmoContactValue()));
        shop.setActive(request.getActive());
        Shop saved = saverShop.save(shop);
        log.info("Изменён магазин витрины {} (id={}), active={}", saved.getSlug(), saved.getId(), saved.getActive());
        return toAdminDto(saved);
    }

    private static String normalizeSlug(String rawSlug) {
        String slug = rawSlug.trim().toLowerCase();
        if (!slug.matches("[a-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug — строчные латинские буквы, цифры, _ и -, без пробелов");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Адрес /shop/" + slug + " занят служебной страницей, выберите другой slug");
        }
        return slug;
    }

    private static String normalizeSupportTelegram(String raw) {
        if (raw == null || raw.isBlank()) {
            return Shop.DEFAULT_SUPPORT_TELEGRAM;
        }
        String handle = raw.trim();
        if (handle.startsWith("@")) {
            handle = handle.substring(1);
        }
        if (!handle.matches("[A-Za-z0-9_]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username бота — латинские буквы, цифры и _, без @ и ссылок");
        }
        return handle;
    }

    private static String normalizeAmoContactValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static AdminShopDTO toAdminDto(Shop shop) {
        return AdminShopDTO.builder()
                .id(shop.getId())
                .slug(shop.getSlug())
                .name(shop.getName())
                .active(shop.getActive())
                .supportTelegram(shop.getSupportTelegram())
                .amoContactValue(shop.getAmoContactValue())
                .createdAt(shop.getCreatedAt())
                .defaultShop(Shop.DEFAULT_SLUG.equals(shop.getSlug()))
                .build();
    }
}
