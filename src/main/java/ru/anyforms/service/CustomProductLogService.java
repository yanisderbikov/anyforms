package ru.anyforms.service;

import ru.anyforms.dto.CustomProductLogDTO;

import java.util.List;

public interface CustomProductLogService {

    /** Лог производства позиции, от старых записей к новым. */
    List<CustomProductLogDTO> getByItemId(Long itemId);

    /** Добавляет запись в лог позиции, время ставится сервером. */
    CustomProductLogDTO add(Long itemId, String text);
}
