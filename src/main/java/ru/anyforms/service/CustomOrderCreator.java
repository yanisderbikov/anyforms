package ru.anyforms.service;

import ru.anyforms.dto.CustomOrderCreateRequestDTO;
import ru.anyforms.model.Order;

public interface CustomOrderCreator {

    Order create(CustomOrderCreateRequestDTO request);
}
