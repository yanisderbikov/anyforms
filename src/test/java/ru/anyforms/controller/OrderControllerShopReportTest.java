package ru.anyforms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.anyforms.dto.marketplace.ShopSalesReportDTO;
import ru.anyforms.model.Role;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomOrderCreator;
import ru.anyforms.service.GetterOrderDTOByType;
import ru.anyforms.service.OrderService;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;
import ru.anyforms.service.product.ShopSalesReportService;
import ru.anyforms.util.converter.ConverterOrder;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerShopReportTest {

    private static final String URL = "/api/orders/shop-report";
    private static final Principal OWNER = () -> "owner@af.ru";
    private static final Principal ADMIN = () -> "admin@anyforms.ru";

    private final ShopSalesReportService reportService = mock(ShopSalesReportService.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
                mock(OrderService.class), mock(GetterOrderDTOByType.class), mock(OrderRepository.class),
                mock(ConverterOrder.class), mock(CustomProductItemRepository.class), mock(CustomOrderCreator.class),
                reportService, userAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(reportService.getReport(any(), any(), any())).thenReturn(mock(ShopSalesReportDTO.class));
        when(userAccessService.resolve("owner@af.ru")).thenReturn(Optional.of(
                new UserAccess("owner@af.ru", "Аня", Role.SHOP_OWNER, false, "af_pastry", "AF Pastry")));
        when(userAccessService.resolve("admin@anyforms.ru")).thenReturn(Optional.of(
                new UserAccess("admin@anyforms.ru", "Юра", Role.ADMIN, false, null, null)));
    }

    @Test
    void ownerWithoutSlugGetsOwnShop() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-09-01").param("to", "2026-09-14").principal(OWNER))
                .andExpect(status().isOk());

        verify(reportService).getReport("af_pastry", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14));
    }

    @Test
    void ownerAskingForAnotherShopIsForbidden() throws Exception {
        mockMvc.perform(get(URL).param("shopSlug", "di_gips").param("from", "2026-09-01").param("to", "2026-09-14")
                        .principal(OWNER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }

    @Test
    void adminPicksAnyShopButMustNameIt() throws Exception {
        mockMvc.perform(get(URL).param("shopSlug", "di_gips").param("from", "2026-09-01").param("to", "2026-09-14")
                        .principal(ADMIN))
                .andExpect(status().isOk());
        verify(reportService).getReport(eq("di_gips"), any(), any());

        mockMvc.perform(get(URL).param("from", "2026-09-01").param("to", "2026-09-14").principal(ADMIN))
                .andExpect(status().isBadRequest());
    }
}
