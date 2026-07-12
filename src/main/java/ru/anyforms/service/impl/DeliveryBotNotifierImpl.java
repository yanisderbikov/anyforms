package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.BotStep;
import ru.anyforms.service.salesbot.SalesbotTrigger;

@Log4j2
@Service
@RequiredArgsConstructor
class DeliveryBotNotifierImpl implements DeliveryBotNotifier {

    private final AmoCrmGateway amoCrmGateway;
    private final SalesbotTrigger salesbotTrigger;
    private final BotExecutionReader botExecutionReader;
    private final BotExecutionRecorder botExecutionRecorder;

    @Value("${amocrm.bot.tracker.sent.id}")
    private Long trackerSentBotId;

    @Value("${amocrm.bot.cdek.ready.to.pickup.id}")
    private Long cdekReadyToPickupBotId;

    @Value("${amocrm.bot.pickup.ready.id}")
    private Long pickupReadyBotId;

    @Override
    public void notifyShipped(Long leadId, String tracker) {
        if (leadId == null) {
            return;
        }
        ensureTrackerInAmo(leadId, tracker);
        runOnce(leadId, trackerSentBotId);
    }

    @Override
    public void notifyCdekReadyToPickup(Long leadId) {
        if (leadId == null) {
            return;
        }
        runOnce(leadId, cdekReadyToPickupBotId);
    }

    @Override
    public void notifyPickupReady(Long leadId) {
        if (leadId == null) {
            return;
        }
        runOnce(leadId, pickupReadyBotId);
    }

    private void ensureTrackerInAmo(Long leadId, String tracker) {
        if (tracker == null || tracker.isBlank()) {
            return;
        }
        try {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            String current = lead != null ? lead.getCustomFieldValue(AmoCrmFieldId.TRACKER.getId()) : null;
            if (current == null || current.isBlank() || current.equals("...")) {
                amoCrmGateway.updateLeadCustomField(leadId, AmoCrmFieldId.TRACKER.getId(), tracker);
                log.info("Tracker {} set in AmoCRM for lead {}", tracker, leadId);
            }
        } catch (Exception e) {
            log.error("Failed to ensure tracker {} in AmoCRM for lead {}: {}", tracker, leadId, e.getMessage(), e);
        }
    }

    private void runOnce(Long leadId, Long botId) {
        try {
            if (botExecutionReader.alreadyExecuted(leadId, botId)) {
                log.info("Bot {} already executed for lead {}, skipping", botId, leadId);
                return;
            }
            boolean success = salesbotTrigger.run(leadId, botId);
            BotStep step = new BotStep(botId, 1);
            if (success) {
                botExecutionRecorder.recordSuccess(leadId, OrderType.DELIVERY, step);
                log.info("Delivery bot {} executed for lead {}", botId, leadId);
            } else {
                botExecutionRecorder.recordFailed(leadId, OrderType.DELIVERY, step);
                log.warn("Delivery bot {} failed for lead {}", botId, leadId);
            }
        } catch (Exception e) {
            log.error("Failed to run delivery bot {} for lead {}: {}", botId, leadId, e.getMessage(), e);
        }
    }
}
