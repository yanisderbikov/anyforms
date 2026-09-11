package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.integration.CdekDeliveryPointsGateway;
import ru.anyforms.repository.impl.CdekPvzJdbcRepo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CdekPvzServiceTest {

    private final CdekDeliveryPointsGateway gateway = mock(CdekDeliveryPointsGateway.class);
    private final CdekPvzJdbcRepo repo = mock(CdekPvzJdbcRepo.class);
    private final CdekPvzService service = new CdekPvzService(gateway, repo);

    private static CdekPvzDTO point(String code, String country, String city, String address) {
        return CdekPvzDTO.builder()
                .code(code)
                .countryCode(country)
                .city(city)
                .address(address)
                .fullAddress(city + ", " + address)
                .build();
    }

    private static final List<CdekPvzDTO> POINTS = List.of(
            point("ALA1", "KZ", "Алматы", "ул. Абая, 10"),
            point("PRM1", "RU", "Пермь", "ул. Грибоедова, 135"),
            point("MSK1", "RU", "Москва", "ул. Абая, 1"),
            point("MNS1", "BY", "Минск", "пр. Независимости, 5")
    );

    @Test
    void searchesAcrossCountriesAndPutsRussiaFirst() {
        when(repo.findAll()).thenReturn(POINTS);
        service.warmUp();

        List<String> codes = service.search("абая").stream().map(CdekPvzDTO::getCode).toList();

        assertEquals(List.of("MSK1", "ALA1"), codes);
    }

    @Test
    void findsForeignPointByCity() {
        when(repo.findAll()).thenReturn(POINTS);
        service.warmUp();

        List<CdekPvzDTO> found = service.search("минск независимости");

        assertEquals(1, found.size());
        assertEquals("MNS1", found.get(0).getCode());
    }

    @Test
    void shortQueryReturnsNothing() {
        when(repo.findAll()).thenReturn(POINTS);
        service.warmUp();

        assertTrue(service.search("ул").isEmpty());
        assertTrue(service.search(null).isEmpty());
    }

    @Test
    void refreshWritesToDbAndSwapsSnapshot() {
        when(gateway.fetchAllPickupPoints()).thenReturn(POINTS);

        assertTrue(service.refresh());

        verify(repo).replaceAll(POINTS);
        assertEquals(4, service.size());
    }

    @Test
    void refreshKeepsOldDataWhenApiReturnsNothing() {
        when(repo.findAll()).thenReturn(POINTS);
        service.warmUp();
        when(gateway.fetchAllPickupPoints()).thenReturn(List.of());

        assertFalse(service.refresh());

        verify(repo, never()).replaceAll(anyList());
        assertEquals(4, service.size());
    }
}
