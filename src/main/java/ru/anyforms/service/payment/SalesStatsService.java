package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.ProductSalesDTO;

import java.time.LocalDate;
import java.util.List;

public interface SalesStatsService {

    List<ProductSalesDTO> getTrainingSales(LocalDate from, LocalDate to);
}
