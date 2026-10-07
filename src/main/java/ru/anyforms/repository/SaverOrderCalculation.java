package ru.anyforms.repository;

import ru.anyforms.model.calculator.OrderCalculation;

public interface SaverOrderCalculation {
    OrderCalculation save(OrderCalculation calculation);
}
