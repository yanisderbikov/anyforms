package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.AdminShopCreateRequestDTO;
import ru.anyforms.dto.marketplace.AdminShopDTO;
import ru.anyforms.dto.marketplace.AdminShopUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ShopAssetUploadDTO;
import ru.anyforms.dto.marketplace.ShopBrandingRequestDTO;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.config.s3.S3Static;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.repository.SaverProduct;
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
    private final GetterProduct getterProduct;
    private final SaverProduct saverProduct;
    private final S3FileStorage s3FileStorage;
    private final S3Static s3Static;

    @Override
    public List<ShopDTO> getActiveShops() {
        return getterShop.getActiveShops().stream()
                .map(shop -> ShopDTO.builder()
                        .id(shop.getId())
                        .slug(shop.getSlug())
                        .name(shop.getName())
                        .active(shop.getActive())
                        .supportTelegram(shop.getSupportTelegram())
                        .tagline(shop.getTagline())
                        .description(shop.getDescription())
                        .logoUrl(shop.getLogoUrl())
                        .logoMarkUrl(shop.getLogoMarkUrl())
                        .faviconUrl(shop.getFaviconUrl())
                        .heroImageUrl(shop.getHeroImageUrl())
                        .heroImageAlt(shop.getHeroImageAlt())
                        .heroVideoUrl(shop.getHeroVideoUrl())
                        .headerColor(shop.getHeaderColor())
                        .accentColor(shop.getAccentColor())
                        .backgroundColor(shop.getBackgroundColor())
                        .build())
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
    @Transactional
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
        applyBranding(shop, request);
        Shop saved = saverShop.save(shop);
        int linked = addRetailProducts(saved);
        log.info("Создан магазин витрины {} (id={}), добавлено товаров: {}", saved.getSlug(), saved.getId(), linked);
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
        applyBranding(shop, request);
        Shop saved = saverShop.save(shop);
        log.info("Изменён магазин витрины {} (id={}), active={}", saved.getSlug(), saved.getId(), saved.getActive());
        return toAdminDto(saved);
    }

    @Override
    public ShopAssetUploadDTO presignAssetUpload(UUID id, String filename, String contentType) {
        Shop shop = getterShop.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден"));
        return presignAsset(shop.getSlug(), filename, contentType);
    }

    @Override
    public ShopAssetUploadDTO presignNewShopAssetUpload(String slug, String filename, String contentType) {
        String normalized = normalizeSlug(slug);
        if (getterShop.getBySlug(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Магазин с адресом " + normalized + " уже есть");
        }
        return presignAsset(normalized, filename, contentType);
    }

    private ShopAssetUploadDTO presignAsset(String slug, String filename, String contentType) {
        if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Можно загрузить только картинку или видео");
        }
        var presigned = s3FileStorage.presignUpload(filename, contentType, "shop/" + slug + "/branding");
        return new ShopAssetUploadDTO(presigned.uploadUrl(), presigned.key(), s3Static.publicUrl(presigned.key()));
    }

    private int addRetailProducts(Shop shop) {
        List<Product> retail = getterProduct.getAllProducts().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getPreorder()))
                .toList();
        for (Product product : retail) {
            product.getShops().add(shop);
            saverProduct.save(product);
        }
        return retail.size();
    }

    private static void applyBranding(Shop shop, ShopBrandingRequestDTO request) {
        shop.setTagline(normalizeText(request.getTagline()));
        shop.setDescription(normalizeText(request.getDescription()));
        shop.setLogoUrl(normalizeUrl(request.getLogoUrl()));
        shop.setLogoMarkUrl(normalizeUrl(request.getLogoMarkUrl()));
        shop.setFaviconUrl(normalizeUrl(request.getFaviconUrl()));
        shop.setHeroImageUrl(normalizeUrl(request.getHeroImageUrl()));
        shop.setHeroImageAlt(normalizeText(request.getHeroImageAlt()));
        shop.setHeroVideoUrl(normalizeUrl(request.getHeroVideoUrl()));
        shop.setHeaderColor(normalizeColor(request.getHeaderColor()));
        shop.setAccentColor(normalizeColor(request.getAccentColor()));
        shop.setBackgroundColor(normalizeColor(request.getBackgroundColor()));
    }

    private static String normalizeColor(String raw) {
        String color = normalizeText(raw);
        return color == null ? null : color.toLowerCase();
    }

    private static String normalizeText(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String normalizeUrl(String raw) {
        String url = normalizeText(raw);
        if (url == null) {
            return null;
        }
        if (!(url.startsWith("https://") || url.startsWith("/")) || url.startsWith("//") || url.matches(".*\\s.*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка на файл должна начинаться с https:// или / — лучше загрузите файл кнопкой");
        }
        return url;
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
                .tagline(shop.getTagline())
                .description(shop.getDescription())
                .logoUrl(shop.getLogoUrl())
                .logoMarkUrl(shop.getLogoMarkUrl())
                .faviconUrl(shop.getFaviconUrl())
                .heroImageUrl(shop.getHeroImageUrl())
                .heroImageAlt(shop.getHeroImageAlt())
                .heroVideoUrl(shop.getHeroVideoUrl())
                .headerColor(shop.getHeaderColor())
                .accentColor(shop.getAccentColor())
                .backgroundColor(shop.getBackgroundColor())
                .createdAt(shop.getCreatedAt())
                .defaultShop(Shop.DEFAULT_SLUG.equals(shop.getSlug()))
                .build();
    }
}
