package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.OrderCalculationDTO;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.service.auth.UserAccess;

import java.util.List;

public interface OrderCalculationJournalService {

    OrderCalculationListItemDTO save(OrderCalculationRequest request, UserAccess user);

    List<OrderCalculationListItemDTO> list(String query, int limit);

    OrderCalculationDTO get(Long id);

    void delete(Long id);
}
