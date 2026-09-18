package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.dto.cdek.DeliveryCostResponseDTO;
import ru.anyforms.dto.payment.CartItemDTO;
import ru.anyforms.integration.CdekCalculatorGateway;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CdekDeliveryCostServiceTest {

    private static final int TARIFF = 136;
    private static final String FROM_PVZ = "SPB75";

    private final CdekCalculatorGateway gateway = mock(CdekCalculatorGateway.class);
    private final CdekPvzService pvzService = mock(CdekPvzService.class);
    private final GetterProduct getterProduct = mock(GetterProduct.class);
    private final CdekDeliveryCostService service = new CdekDeliveryCostService(
            gateway, pvzService, getterProduct, TARIFF, FROM_PVZ,
            "198095", "Санкт-Петербург", "ул. Трефолева, 9, корп. 2", 300, 15);

    private static CdekPvzDTO pvz(String code, String city, String postal, String address) {
        return CdekPvzDTO.builder().code(code).countryCode("RU").city(city).postalCode(postal).address(address).build();
    }

    private static CartItemDTO item(UUID productId, int quantity) {
        CartItemDTO dto = new CartItemDTO();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        return dto;
    }

    @Test
    void usesDefaultsWhenProductHasNoDimensionsAndOnePackagePerUnit() {
        UUID noDims = UUID.randomUUID();
        UUID withDims = UUID.randomUUID();
        when(getterProduct.getById(noDims)).thenReturn(Optional.of(
                Product.builder().name("Без габаритов").description("d").price("100").build()));
        when(getterProduct.getById(withDims)).thenReturn(Optional.of(
                Product.builder().name("С габаритами").description("d").price("100")
                        .weightGrams(1200).lengthCm(28).widthCm(26).heightCm(10).build()));
        when(pvzService.findByCode("MSK1")).thenReturn(Optional.of(pvz("MSK1", "Москва", "101000", "ул. Абая, 1")));
        when(pvzService.findByCode(FROM_PVZ)).thenReturn(Optional.of(pvz(FROM_PVZ, "Санкт-Петербург", "198095", "ул. Трефолева, 9, корп. 2")));
        when(gateway.calculate(anyInt(), any(), any(), anyList()))
                .thenReturn(new CdekTariffQuote(new BigDecimal("450.00"), 2, 4));

        DeliveryCostResponseDTO response = service.calculate(List.of(item(noDims, 2), item(withDims, 1)), "MSK1");

        assertEquals(new BigDecimal("450.00"), response.cost());
        assertEquals(2, response.periodMin());
        assertEquals(4, response.periodMax());
        assertEquals(TARIFF, response.tariffCode());

        ArgumentCaptor<CdekLocation> from = ArgumentCaptor.forClass(CdekLocation.class);
        ArgumentCaptor<CdekLocation> to = ArgumentCaptor.forClass(CdekLocation.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CdekPackage>> packages = ArgumentCaptor.forClass(List.class);
        verify(gateway).calculate(org.mockito.ArgumentMatchers.eq(TARIFF), from.capture(), to.capture(), packages.capture());
        assertEquals("198095", from.getValue().postalCode());
        assertEquals("101000", to.getValue().postalCode());
        assertEquals(List.of(
                new CdekPackage(300, 15, 15, 15),
                new CdekPackage(300, 15, 15, 15),
                new CdekPackage(1200, 28, 26, 10)), packages.getValue());
    }

    @Test
    void fallsBackToConfiguredFromAddressWhenFromPvzUnknown() {
        UUID id = UUID.randomUUID();
        when(getterProduct.getById(id)).thenReturn(Optional.of(
                Product.builder().name("x").description("d").price("1").build()));
        when(pvzService.findByCode("MSK1")).thenReturn(Optional.of(pvz("MSK1", "Москва", "101000", "ул. Абая, 1")));
        when(pvzService.findByCode(FROM_PVZ)).thenReturn(Optional.empty());
        when(gateway.calculate(anyInt(), any(), any(), anyList()))
                .thenReturn(new CdekTariffQuote(new BigDecimal("300"), 1, 2));

        service.calculate(List.of(item(id, 1)), "MSK1");

        ArgumentCaptor<CdekLocation> from = ArgumentCaptor.forClass(CdekLocation.class);
        verify(gateway).calculate(anyInt(), from.capture(), any(), anyList());
        assertEquals("ул. Трефолева, 9, корп. 2", from.getValue().address());
        assertEquals("Санкт-Петербург", from.getValue().city());
    }

    @Test
    void rejectsUnknownPvzAndEmptyCart() {
        when(pvzService.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.calculate(List.of(item(UUID.randomUUID(), 1)), "NOPE"));

        when(pvzService.findByCode("MSK1")).thenReturn(Optional.of(pvz("MSK1", "Москва", "101000", "ул. Абая, 1")));
        assertThrows(ResponseStatusException.class, () -> service.calculate(List.of(), "MSK1"));
    }
}
