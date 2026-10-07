package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculatorOptionsDTO;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.service.auth.UserAccess;

public interface OrderCalculatorService {

    OrderCalculationResult calculate(OrderCalculationRequest request, UserAccess user);

    OrderCalculationResult calculate(OrderCalculationRequest request, RatesSnapshot rates);

    CalculatorOptionsDTO options(UserAccess user);
}
