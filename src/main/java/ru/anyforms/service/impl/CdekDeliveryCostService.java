package ru.anyforms.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.dto.cdek.DeliveryCostResponseDTO;
import ru.anyforms.dto.payment.CartItemDTO;
import ru.anyforms.integration.CdekCalculatorGateway;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;

import java.util.ArrayList;
import java.util.List;

@Service
public class CdekDeliveryCostService {

    private static final Logger logger = LoggerFactory.getLogger(CdekDeliveryCostService.class);

    private final CdekCalculatorGateway calculatorGateway;
    private final CdekPvzService pvzService;
    private final GetterProduct getterProduct;
    private final int tariffCode;
    private final String fromPvzCode;
    private final CdekLocation fromFallback;
    private final int defaultWeightGrams;
    private final int defaultDimensionCm;

    public CdekDeliveryCostService(CdekCalculatorGateway calculatorGateway,
                                   CdekPvzService pvzService,
                                   GetterProduct getterProduct,
                                   @Value("${sdek.tariff.code}") int tariffCode,
                                   @Value("${sdek.from.pvz.code}") String fromPvzCode,
                                   @Value("${sdek.from.postal.code}") String fromPostalCode,
                                   @Value("${sdek.from.city}") String fromCity,
                                   @Value("${sdek.from.address}") String fromAddress,
                                   @Value("${sdek.default.weight.grams}") int defaultWeightGrams,
                                   @Value("${sdek.default.dimension.cm}") int defaultDimensionCm) {
        this.calculatorGateway = calculatorGateway;
        this.pvzService = pvzService;
        this.getterProduct = getterProduct;
        this.tariffCode = tariffCode;
        this.fromPvzCode = fromPvzCode;
        this.fromFallback = new CdekLocation("RU", fromPostalCode, fromCity, fromAddress);
        this.defaultWeightGrams = defaultWeightGrams;
        this.defaultDimensionCm = defaultDimensionCm;
    }

    public DeliveryCostResponseDTO calculate(List<CartItemDTO> items, String pvzCode) {
        CdekLocation to = pvzService.findByCode(pvzCode)
                .map(CdekLocation::fromPvz)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ПВЗ не найден: " + pvzCode));
        List<CdekPackage> packages = buildPackages(items);
        CdekTariffQuote quote = calculatorGateway.calculate(tariffCode, fromLocation(), to, packages);
        return new DeliveryCostResponseDTO(quote.deliverySum(), quote.periodMin(), quote.periodMax(), tariffCode);
    }

    public List<CdekPackage> buildPackages(List<CartItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Корзина пуста");
        }
        List<CdekPackage> packages = new ArrayList<>();
        for (CartItemDTO item : items) {
            Product product = getterProduct.getById(item.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Товар не найден: " + item.getProductId()));
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Некорректное количество для товара " + product.getName());
            }
            CdekPackage unit = packageFor(product);
            for (int i = 0; i < quantity; i++) {
                packages.add(unit);
            }
        }
        return packages;
    }

    private CdekPackage packageFor(Product product) {
        return new CdekPackage(
                positiveOrDefault(product.getWeightGrams(), defaultWeightGrams),
                positiveOrDefault(product.getLengthCm(), defaultDimensionCm),
                positiveOrDefault(product.getWidthCm(), defaultDimensionCm),
                positiveOrDefault(product.getHeightCm(), defaultDimensionCm));
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private CdekLocation fromLocation() {
        return pvzService.findByCode(fromPvzCode)
                .map(CdekLocation::fromPvz)
                .orElseGet(() -> {
                    logger.warn("ПВЗ отправки {} не найден в справочнике, используем адрес из конфига", fromPvzCode);
                    return fromFallback;
                });
    }
}
