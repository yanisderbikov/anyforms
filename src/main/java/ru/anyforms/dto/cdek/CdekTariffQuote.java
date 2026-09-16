package ru.anyforms.dto.cdek;

import java.math.BigDecimal;

public record CdekTariffQuote(BigDecimal deliverySum, Integer periodMin, Integer periodMax) {
}
