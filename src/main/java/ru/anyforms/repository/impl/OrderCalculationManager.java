package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.model.calculator.OrderCalculation;
import ru.anyforms.repository.GetterOrderCalculation;
import ru.anyforms.repository.OrderCalculationDeleter;
import ru.anyforms.repository.SaverOrderCalculation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class OrderCalculationManager implements GetterOrderCalculation, SaverOrderCalculation, OrderCalculationDeleter {

    private final OrderCalculationRepo orderCalculationRepo;

    @Override
    public Optional<OrderCalculation> getById(Long id) {
        try {
            return orderCalculationRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<OrderCalculationListItemDTO> getRecent(String query, int limit) {
        try {
            PageRequest page = PageRequest.of(0, limit);
            if (query == null || query.isBlank()) {
                return orderCalculationRepo.findRecent(page);
            }
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return orderCalculationRepo.search(pattern, page);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public OrderCalculation save(OrderCalculation calculation) {
        try {
            return orderCalculationRepo.save(calculation);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            orderCalculationRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
