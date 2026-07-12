package ru.anyforms.service;

import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductItemRequestDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.model.CustomProductStatus;

import java.util.List;

public interface CustomProductItemService {

    List<CustomProductItemDTO> getByOrderId(Long orderId);

    /** Одна позиция по id (для шеринга ссылки; отдаётся в любом статусе). */
    CustomProductItemDTO getById(Long itemId);

    /** Все позиции, кроме завершённых (плоский список для «в работе»). */
    List<CustomProductItemDTO> getAll();

    /** Позиции в конкретном статусе (для фильтра, в т.ч. COMPLETED). */
    List<CustomProductItemDTO> getAllByStatus(CustomProductStatus status);

    /** Уникальные значения «кто моделирует» (для select с автодобавлением). */
    List<String> getModelers();

    CustomProductItemDTO create(Long orderId, CustomProductItemRequestDTO request);

    CustomProductItemDTO update(Long itemId, CustomProductItemRequestDTO request);

    CustomProductItemDTO updateStatus(Long itemId, CustomProductStatus status);

    void delete(Long itemId);

    CustomProductItemDTO addFiles(Long itemId, List<MultipartFile> files);

    /** Удаляет один файл по его id, возвращает обновлённую позицию. */
    CustomProductItemDTO removeFile(Long fileId);

    /** Группы позиций «к отправке» (READY_TO_SHIP), сгруппированные по заказу. */
    List<ShipGroupDTO> getReadyToShipGroups();

    /** Группы позиций «доставляются» (DELIVERING), заказ ещё не вручен. */
    List<ShipGroupDTO> getInDeliveryGroups();

    /** Отгрузка: ставит трекер на заказ и переводит его готовые позиции в DELIVERING. */
    void ship(Long orderId, String tracker);

    /** Завершение: переводит доставляющиеся позиции заказа в COMPLETED. */
    void completeOrder(Long orderId);
}
