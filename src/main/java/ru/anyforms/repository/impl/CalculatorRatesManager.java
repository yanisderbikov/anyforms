package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.model.calculator.CalculatorRatesVersion;
import ru.anyforms.repository.GetterCalculatorRates;
import ru.anyforms.repository.SaverCalculatorRates;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class CalculatorRatesManager implements GetterCalculatorRates, SaverCalculatorRates {

    private final CalculatorRatesRepo calculatorRatesRepo;

    @Override
    public Optional<CalculatorRatesVersion> getLatest() {
        try {
            return calculatorRatesRepo.findFirstByOrderByIdDesc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<CalculatorRatesVersion> getHistory(int limit) {
        try {
            return calculatorRatesRepo.findAllByOrderByIdDesc(PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public CalculatorRatesVersion save(CalculatorRatesVersion version) {
        try {
            return calculatorRatesRepo.save(version);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
