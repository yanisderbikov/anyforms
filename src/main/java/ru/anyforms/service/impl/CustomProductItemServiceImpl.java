package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.CustomProductFileDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductItemRequestDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.model.CustomProductFile;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.model.Order;
import ru.anyforms.repository.CustomProductFileRepository;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
class CustomProductItemServiceImpl implements CustomProductItemService {

    private static final String FILE_KEY_PREFIX = "custom-products/";

    private final CustomProductItemRepository itemRepository;
    private final CustomProductFileRepository fileRepository;
    private final OrderRepository orderRepository;
    private final S3FileStorage s3FileStorage;
    private final ConverterOrder converterOrder;

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductItemDTO> getByOrderId(Long orderId) {
        return itemRepository.findByOrderIdAndStatusNot(orderId, CustomProductStatus.SENT, Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductItemDTO> getAll() {
        return itemRepository.findByStatusNot(CustomProductStatus.SENT, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public CustomProductItemDTO create(Long orderId, CustomProductItemRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + orderId));
        CustomProductItem item = new CustomProductItem();
        item.setOrder(order);
        applyRequest(item, request);
        item.setStatus(CustomProductStatus.MODELING);
        CustomProductItem saved = itemRepository.save(item);
        log.info("CustomProductItem created: id={}, orderId={}", saved.getId(), orderId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public CustomProductItemDTO update(Long itemId, CustomProductItemRequestDTO request) {
        CustomProductItem item = getOrThrow(itemId);
        applyRequest(item, request);
        return toDTO(itemRepository.save(item));
    }

    @Override
    @Transactional
    public CustomProductItemDTO updateStatus(Long itemId, CustomProductStatus status) {
        CustomProductItem item = getOrThrow(itemId);
        item.setStatus(status);
        return toDTO(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void delete(Long itemId) {
        CustomProductItem item = getOrThrow(itemId);
        item.getFiles().forEach(f -> s3FileStorage.delete(f.getS3Key()));
        itemRepository.delete(item);
        log.info("CustomProductItem deleted: id={}", itemId);
    }

    @Override
    @Transactional
    public CustomProductItemDTO addFiles(Long itemId, List<MultipartFile> files) {
        CustomProductItem item = getOrThrow(itemId);
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String key = s3FileStorage.upload(file, FILE_KEY_PREFIX + item.getId());
                CustomProductFile cf = new CustomProductFile();
                cf.setS3Key(key);
                cf.setFilename(file.getOriginalFilename());
                item.addFile(cf);
            }
        }
        return toDTO(itemRepository.save(item));
    }

    @Override
    @Transactional
    public CustomProductItemDTO removeFile(Long fileId) {
        CustomProductFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден: " + fileId));
        Long itemId = file.getItem().getId();
        CustomProductItem item = getOrThrow(itemId);
        item.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .ifPresent(target -> {
                    s3FileStorage.delete(target.getS3Key());
                    item.removeFile(target);
                    itemRepository.save(item);
                });
        return toDTO(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipGroupDTO> getReadyToShipGroups() {
        Map<Long, ShipGroupDTO> groups = new LinkedHashMap<>();
        for (CustomProductItem item : itemRepository.findByStatus(CustomProductStatus.READY_TO_SHIP)) {
            Order order = item.getOrder();
            if (order == null) {
                continue;
            }
            ShipGroupDTO group = groups.computeIfAbsent(order.getId(), k -> {
                ShipGroupDTO g = new ShipGroupDTO();
                g.setOrder(converterOrder.convert(order));
                g.setItems(new ArrayList<>());
                return g;
            });
            group.getItems().add(toDTO(item));
        }
        return new ArrayList<>(groups.values());
    }

    @Override
    @Transactional
    public void ship(Long orderId, String tracker) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + orderId));
        order.setTracker(tracker);
        orderRepository.save(order);

        List<CustomProductItem> items = itemRepository.findByOrderIdAndStatus(orderId, CustomProductStatus.READY_TO_SHIP);
        for (CustomProductItem item : items) {
            item.setStatus(CustomProductStatus.SENT);
            itemRepository.save(item);
        }
        log.info("Order {} shipped: tracker={}, items sent={}", orderId, tracker, items.size());
    }

    private CustomProductItem getOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Позиция не найдена: " + itemId));
    }

    private void applyRequest(CustomProductItem item, CustomProductItemRequestDTO request) {
        item.setProductName(request.getProductName());
        item.setDescription(request.getDescription());
        item.setQuantity(request.getQuantity());
    }

    private CustomProductItemDTO toDTO(CustomProductItem item) {
        CustomProductItemDTO dto = new CustomProductItemDTO();
        dto.setId(item.getId());
        dto.setOrderId(item.getOrder() != null ? item.getOrder().getId() : null);
        dto.setClientName(item.getOrder() != null ? item.getOrder().getContactName() : null);
        dto.setProductName(item.getProductName());
        dto.setDescription(item.getDescription());
        dto.setQuantity(item.getQuantity());
        dto.setStatus(item.getStatus());
        dto.setStatusDescription(item.getStatus() != null ? item.getStatus().getDescription() : null);
        dto.setFiles(item.getFiles().stream()
                .map(f -> new CustomProductFileDTO(f.getId(), s3FileStorage.presignedUrl(f.getS3Key()), f.getFilename()))
                .toList());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }
}
