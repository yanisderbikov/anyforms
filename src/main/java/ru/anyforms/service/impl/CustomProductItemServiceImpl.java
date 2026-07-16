package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
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
import ru.anyforms.event.OrderShippedEvent;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.model.CustomProductFile;
import ru.anyforms.model.DeliveryMethod;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.model.Order;
import ru.anyforms.repository.CustomProductFileRepository;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Log4j2
@Service
@RequiredArgsConstructor
class CustomProductItemServiceImpl implements CustomProductItemService {

    private static final String FILE_KEY_PREFIX = "custom-products/";
    private static final List<CustomProductStatus> HIDDEN_STATUSES =
            List.of(CustomProductStatus.DELIVERING, CustomProductStatus.COMPLETED);

    private final CustomProductItemRepository itemRepository;
    private final CustomProductFileRepository fileRepository;
    private final OrderRepository orderRepository;
    private final S3FileStorage s3FileStorage;
    private final ConverterOrder converterOrder;
    private final ApplicationEventPublisher eventPublisher;
    private final DeliveryBotNotifier deliveryBotNotifier;

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductItemDTO> getByOrderId(Long orderId) {
        return itemRepository.findByOrderIdAndStatusNotIn(orderId, HIDDEN_STATUSES, Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomProductItemDTO getById(Long itemId) {
        return toDTO(getOrThrow(itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductItemDTO> getAll() {
        return itemRepository.findByStatusNotIn(List.of(CustomProductStatus.COMPLETED), Sort.by(Sort.Direction.ASC, "createdAt")).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductItemDTO> getAllByStatus(CustomProductStatus status) {
        return itemRepository.findByStatus(status).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getModelers() {
        return itemRepository.findDistinctModelers();
    }

    @Override
    @Transactional
    public CustomProductItemDTO create(Long orderId, CustomProductItemRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + orderId));
        CustomProductItem item = new CustomProductItem();
        item.setOrder(order);
        if (request.getStatus() == CustomProductStatus.DELIVERING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "В статус DELIVERING позиция переводится только через отгрузку с трекером");
        }
        applyRequest(item, request);
        item.setStatus(request.getStatus() != null ? request.getStatus() : CustomProductStatus.MODELING);
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
        if (status == CustomProductStatus.DELIVERING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "В статус DELIVERING позиция переводится только через отгрузку с трекером");
        }
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
        return groupByOrder(itemRepository.findByStatus(CustomProductStatus.READY_TO_SHIP), order -> true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipGroupDTO> getInDeliveryGroups() {
        return groupByOrder(itemRepository.findByStatus(CustomProductStatus.DELIVERING), this::isNotDelivered);
    }

    private boolean isNotDelivered(Order order) {
        return !CdekOrderStatus.DELIVERED.getCode().equals(order.getDeliveryStatus());
    }

    private List<ShipGroupDTO> groupByOrder(List<CustomProductItem> items, Predicate<Order> orderFilter) {
        Map<Long, ShipGroupDTO> groups = new LinkedHashMap<>();
        for (CustomProductItem item : items) {
            Order order = item.getOrder();
            if (order == null || !orderFilter.test(order)) {
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

        boolean pickup = order.getDeliveryMethod() == DeliveryMethod.PICKUP;
        if (!pickup) {
            if (tracker == null || tracker.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для отправки СДЭК обязателен трекер");
            }
            order.setTracker(tracker);
            orderRepository.save(order);
        }

        List<CustomProductItem> items = itemRepository.findByOrderIdAndStatus(orderId, CustomProductStatus.READY_TO_SHIP);
        for (CustomProductItem item : items) {
            item.setStatus(CustomProductStatus.DELIVERING);
            itemRepository.save(item);
        }
        if (pickup) {
            if (order.getLeadId() != null) {
                deliveryBotNotifier.notifyPickupReady(order.getLeadId());
            } else {
                log.warn("Order {} is pickup-ready but has no leadId, pickup bot not triggered", orderId);
            }
        } else {
            eventPublisher.publishEvent(new OrderShippedEvent(tracker));
        }
        log.info("Order {} shipped: pickup={}, tracker={}, items delivering={}", orderId, pickup, tracker, items.size());
    }

    @Override
    @Transactional
    public void completeOrder(Long orderId) {
        List<CustomProductItem> items = itemRepository.findByOrderIdAndStatus(orderId, CustomProductStatus.DELIVERING);
        for (CustomProductItem item : items) {
            item.setStatus(CustomProductStatus.COMPLETED);
            itemRepository.save(item);
        }
        log.info("Order {} completed: items={}", orderId, items.size());
    }

    private CustomProductItem getOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Позиция не найдена: " + itemId));
    }

    private void applyRequest(CustomProductItem item, CustomProductItemRequestDTO request) {
        item.setProductName(request.getProductName());
        item.setDescription(request.getDescription());
        item.setQuantity(request.getQuantity());
        String modeler = request.getModeler();
        item.setModeler(modeler != null && !modeler.isBlank() ? modeler.trim() : null);
    }

    private CustomProductItemDTO toDTO(CustomProductItem item) {
        CustomProductItemDTO dto = new CustomProductItemDTO();
        dto.setId(item.getId());
        dto.setOrderId(item.getOrder() != null ? item.getOrder().getId() : null);
        dto.setClientName(item.getOrder() != null ? item.getOrder().getContactName() : null);
        dto.setLeadId(item.getOrder() != null ? item.getOrder().getLeadId() : null);
        dto.setProductName(item.getProductName());
        dto.setDescription(item.getDescription());
        dto.setQuantity(item.getQuantity());
        dto.setModeler(item.getModeler());
        dto.setStatus(item.getStatus());
        dto.setStatusDescription(item.getStatus() != null ? item.getStatus().getDescription() : null);
        dto.setStatusUpdatedAt(item.getStatusUpdatedAt());
        dto.setFiles(item.getFiles().stream()
                .map(f -> new CustomProductFileDTO(f.getId(), s3FileStorage.presignedUrl(f.getS3Key()), f.getFilename()))
                .toList());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }
}
