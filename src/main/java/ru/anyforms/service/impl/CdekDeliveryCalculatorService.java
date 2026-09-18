package ru.anyforms.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.exception.CdekCalculationException;
import ru.anyforms.integration.CdekCalculatorGateway;

import java.util.List;

@Service
public class CdekDeliveryCalculatorService {

    private final CdekCalculatorGateway calculatorGateway;
    private final CdekPvzService pvzService;
    private final int tariffCode;
    private final String fromPvzCode;
    private final CdekLocation fromFallback;

    public CdekDeliveryCalculatorService(CdekCalculatorGateway calculatorGateway,
                                         CdekPvzService pvzService,
                                         @Value("${sdek.tariff.code}") int tariffCode,
                                         @Value("${sdek.from.pvz.code}") String fromPvzCode,
                                         @Value("${sdek.from.postal.code}") String fromPostalCode,
                                         @Value("${sdek.from.city}") String fromCity,
                                         @Value("${sdek.from.address}") String fromAddress) {
        this.calculatorGateway = calculatorGateway;
        this.pvzService = pvzService;
        this.tariffCode = tariffCode;
        this.fromPvzCode = fromPvzCode;
        this.fromFallback = new CdekLocation("RU", fromPostalCode, fromCity, fromAddress);
    }

    public static class DeliveryCalculationResult {
        private final Double cost;
        private final Integer deliveryPeriodMin;
        private final Integer deliveryPeriodMax;
        private final String error;

        public DeliveryCalculationResult(Double cost, Integer deliveryPeriodMin, Integer deliveryPeriodMax) {
            this.cost = cost;
            this.deliveryPeriodMin = deliveryPeriodMin;
            this.deliveryPeriodMax = deliveryPeriodMax;
            this.error = null;
        }

        public DeliveryCalculationResult(String error) {
            this.cost = null;
            this.deliveryPeriodMin = null;
            this.deliveryPeriodMax = null;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }

        public Double getCost() {
            return cost;
        }

        public Integer getDeliveryPeriodMin() {
            return deliveryPeriodMin;
        }

        public Integer getDeliveryPeriodMax() {
            return deliveryPeriodMax;
        }

        public String getError() {
            return error;
        }

        public String getFormattedResult() {
            if (!isSuccess()) {
                return "Ошибка расчета: " + error;
            }
            StringBuilder result = new StringBuilder();
            result.append("Стоимость доставки: ").append(cost != null ? String.format("%.2f", cost) : "не указана").append(" руб.");
            if (deliveryPeriodMin != null && deliveryPeriodMax != null) {
                if (deliveryPeriodMin.equals(deliveryPeriodMax)) {
                    result.append("\nСрок доставки: ").append(deliveryPeriodMin).append(" дн.");
                } else {
                    result.append("\nСрок доставки: ").append(deliveryPeriodMin).append("-").append(deliveryPeriodMax).append(" дн.");
                }
            } else if (deliveryPeriodMin != null) {
                result.append("\nСрок доставки: от ").append(deliveryPeriodMin).append(" дн.");
            } else if (deliveryPeriodMax != null) {
                result.append("\nСрок доставки: до ").append(deliveryPeriodMax).append(" дн.");
            }
            return result.toString();
        }
    }

    public DeliveryCalculationResult calculateDelivery(String toCity, Integer toPostalCode,
                                                       int weight, int length, int width, int height) {
        if (toPostalCode == null && (toCity == null || toCity.isEmpty())) {
            return new DeliveryCalculationResult("Не указан город или почтовый индекс получателя");
        }
        CdekLocation to = new CdekLocation("RU", toPostalCode == null ? null : String.valueOf(toPostalCode), toCity, null);
        CdekLocation from = pvzService.findByCode(fromPvzCode).map(CdekLocation::fromPvz).orElse(fromFallback);
        try {
            CdekTariffQuote quote = calculatorGateway.calculate(tariffCode, from, to,
                    List.of(new CdekPackage(weight, length, width, height)));
            return new DeliveryCalculationResult(
                    quote.deliverySum() == null ? null : quote.deliverySum().doubleValue(),
                    quote.periodMin(), quote.periodMax());
        } catch (CdekCalculationException e) {
            return new DeliveryCalculationResult(e.getMessage());
        }
    }
}
