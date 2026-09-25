package ru.anyforms.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.SalesbotTrigger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryBotNotifierImplTest {

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final SalesbotTrigger salesbotTrigger = mock(SalesbotTrigger.class);
    private final BotExecutionReader botExecutionReader = mock(BotExecutionReader.class);
    private final BotExecutionRecorder botExecutionRecorder = mock(BotExecutionRecorder.class);
    private final DeliveryBotNotifierImpl notifier =
            new DeliveryBotNotifierImpl(amoCrmGateway, salesbotTrigger, botExecutionReader, botExecutionRecorder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notifier, "trackerSentBotId", 12981L);
        ReflectionTestUtils.setField(notifier, "cdekReadyToPickupBotId", 23939L);
        ReflectionTestUtils.setField(notifier, "pickupReadyBotId", 23937L);
        when(salesbotTrigger.run(anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    void writesDaysToAmoFieldBeforeRunningBot() {
        notifier.notifyShipped(777L, "1234567890", CdekDeliveryEta.ofPeriod(2, 3));

        verify(amoCrmGateway).updateLeadCustomField(777L, AmoCrmFieldId.DELIVERY_ETA.getId(), "2-3 дня");
        verify(salesbotTrigger).run(777L, 12981L);
    }

    @Test
    void runsBotWithoutEtaWhenCdekGaveNothing() {
        notifier.notifyShipped(777L, "1234567890", null);

        verify(amoCrmGateway, never()).updateLeadCustomField(anyLong(), eq(AmoCrmFieldId.DELIVERY_ETA.getId()), anyString());
        verify(salesbotTrigger).run(777L, 12981L);
    }

    @Test
    void amoFailureDoesNotBlockBot() {
        when(amoCrmGateway.updateLeadCustomField(anyLong(), eq(AmoCrmFieldId.DELIVERY_ETA.getId()), anyString()))
                .thenThrow(new RuntimeException("amo down"));

        notifier.notifyShipped(777L, "1234567890", CdekDeliveryEta.ofPeriod(1, 1));

        verify(salesbotTrigger).run(777L, 12981L);
    }
}
