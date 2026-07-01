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

    /** Все позиции (плоский список для страницы-трекера). */
    List<CustomProductItemDTO> getAll();

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

    /** Отгрузка: ставит трекер на заказ и переводит его готовые позиции в SENT. */
    void ship(Long orderId, String tracker);
}
