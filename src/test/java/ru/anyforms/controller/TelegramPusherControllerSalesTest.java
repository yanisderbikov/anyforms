package ru.anyforms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.anyforms.dto.payment.ProductSalesDTO;
import ru.anyforms.service.payment.SalesStatsService;
import ru.anyforms.service.telegram.TelegramDigestService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Контракт эндпоинта продаж: авторизация, обязательные даты и имена полей в JSON,
 * на которые завязан telegram-pusher.
 */
class TelegramPusherControllerSalesTest {

    private static final String TOKEN = "s3cret";
    private static final String URL = "/api/pusher/telegram/sales";

    private final SalesStatsService salesStatsService = mock(SalesStatsService.class);
    private final TelegramDigestService telegramDigestService = mock(TelegramDigestService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TelegramPusherController controller =
                new TelegramPusherController(telegramDigestService, salesStatsService);
        ReflectionTestUtils.setField(controller, "serviceToken", TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(salesStatsService.getTrainingSales(any(), any())).thenReturn(List.of(
                new ProductSalesDTO("GUIDE", 10, 1_000_000L),
                new ProductSalesDTO("COURSE", 1, 300_000L),
                new ProductSalesDTO("COURSE_PERSONAL", 0, 0L)));
    }

    @Test
    void returnsFieldsThePusherReads() throws Exception {
        mockMvc.perform(get(URL).header("X-Auth-Token", TOKEN)
                        .param("from", "2026-07-28").param("to", "2026-07-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("GUIDE"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].amountKopecks").value(1_000_000L))
                .andExpect(jsonPath("$[2].code").value("COURSE_PERSONAL"))
                .andExpect(jsonPath("$[2].quantity").value(0));
    }

    @Test
    void bindsIsoDates() throws Exception {
        mockMvc.perform(get(URL).header("X-Auth-Token", TOKEN)
                        .param("from", "2026-07-20").param("to", "2026-07-26"))
                .andExpect(status().isOk());

        verify(salesStatsService).getTrainingSales(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
    }

    @Test
    void requiresBothDates() throws Exception {
        mockMvc.perform(get(URL).header("X-Auth-Token", TOKEN).param("from", "2026-07-28"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get(URL).header("X-Auth-Token", TOKEN))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(salesStatsService);
    }

    @Test
    void rejectsWrongAndMissingToken() throws Exception {
        mockMvc.perform(get(URL).header("X-Auth-Token", "nope")
                        .param("from", "2026-07-28").param("to", "2026-07-28"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).param("from", "2026-07-28").param("to", "2026-07-28"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(salesStatsService);
    }
}
