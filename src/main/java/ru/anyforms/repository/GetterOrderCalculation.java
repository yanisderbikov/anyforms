package ru.anyforms.repository;

import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.model.calculator.OrderCalculation;

import java.util.List;
import java.util.Optional;

public interface GetterOrderCalculation {
    Optional<OrderCalculation> getById(Long id);

    List<OrderCalculationListItemDTO> getRecent(String query, int limit);
}
