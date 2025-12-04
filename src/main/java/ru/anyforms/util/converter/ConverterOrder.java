package ru.anyforms.util.converter;

import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.model.Order;

public interface ConverterOrder {
    OrderSummaryDTO convert(Order order);
}
