package ru.anyforms.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.calculator.CalculatorRatesVersion;

import java.util.List;
import java.util.Optional;

@Repository
interface CalculatorRatesRepo extends JpaRepository<CalculatorRatesVersion, Long> {

    Optional<CalculatorRatesVersion> findFirstByOrderByIdDesc();

    List<CalculatorRatesVersion> findAllByOrderByIdDesc(Pageable pageable);
}
