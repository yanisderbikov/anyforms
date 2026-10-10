package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CalculatorRatesDTO;
import ru.anyforms.dto.calculator.CalculatorRatesVersionDTO;
import ru.anyforms.service.auth.UserAccess;

import java.util.List;

public interface CalculatorRatesService {

    RatesSnapshot current();

    CalculatorRatesDTO get();

    CalculatorRatesDTO update(CalculatorRates rates, UserAccess user);

    List<CalculatorRatesVersionDTO> history(int limit);
}
