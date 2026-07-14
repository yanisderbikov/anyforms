package ru.anyforms.service.amo.impl;

import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.amo.AmoCrmWebhookService;
import ru.anyforms.service.amo.LeadAmoCrmStatusUpdater;
import ru.anyforms.service.OrderService;
import ru.anyforms.service.impl.CacheService;
import ru.anyforms.service.telegram.TelegramNotificationQueue;
import ru.anyforms.util.WebhookParserService;
import ru.anyforms.util.amo.JsonLeadIdExtractionService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
class AmoCrmWebhookServiceImpl implements AmoCrmWebhookService {

    private final WebhookParserService webhookParserService;
    private final JsonLeadIdExtractionService jsonLeadIdExtraction;
    private final LeadAmoCrmStatusUpdater leadAmoCrmStatusUpdater;
    private final OrderService orderService;
    private final AmoCrmGateway amoCrmGateway;
    private final CacheService cacheService;
    private final OrderRepository orderRepository;
    private final TelegramNotificationQueue telegramNotificationQueue;
    @Value("${amocrm.subdomain}")
    private String subdomain;

    @Override
    public void processFormDataWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads != null) {
                // Extract lead IDs from "add" events
                List<Long> addLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    log.info("extract from add event for lead {}", leadId);
                    handlePerchance(leadId);
                }
                
                // Extract lead IDs from other event types (status, mail_in, etc.)
                List<Long> eventLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataEvents(leads);
                for (Long leadId : eventLeadIds) {
                    log.info("IDs from other event types {}", leadId);
                    handlePerchance(leadId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing form-data webhook: " + e);
        }
    }

    @Override
    public void processFormDataSyncOrderWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);

            if (leads != null) {
                List<Long> addLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    if (cacheService.containsSyncOrderLead(leadId)) {
                        log.info("Skip duplicate sync-order webhook for lead {}", leadId);
                        continue;
                    }
                    cacheService.addSyncOrderLead(leadId);
                    orderService.syncOrder(leadId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing form-data webhook: " + e);
        }
    }

    private void handlePerchance(Long leadId) {
        var result = orderService.syncOrder(leadId);
        var contact = amoCrmGateway.getContactFromLead(leadId);

        String newLeadUrl = String.format("https://%s.amocrm.ru/leads/detail/%s", subdomain, leadId);
        String listReleasedLeads = contact.getCustomFieldValue(AmoCrmFieldId.RELEASED_LEADS_LIST_CONTACT);

        // Идемпотентность: если сделка уже учтена в контакте (ссылка есть в списке),
        // не накручиваем повторно кол-во покупок, бюджет и список при повторном вебхуке.
        boolean alreadyCounted = listReleasedLeads != null
                && Arrays.asList(listReleasedLeads.split("\\R")).contains(newLeadUrl);

        if (alreadyCounted) {
            log.info("Lead {} already counted for contact {}, skip count/budget/list update", leadId, contact.getId());
        } else {
            var countReleasedOrders = parseLong(contact.getCustomFieldValue(AmoCrmFieldId.COUNT_RELEASED_ORDERS_CONTACT)) + 1;
            String updatedListReleasedLeads = listReleasedLeads == null
                    ? newLeadUrl
                    : listReleasedLeads + "\n" + newLeadUrl;

            var lead = amoCrmGateway.getLead(leadId);
            var price = lead.getPrice();
            var priceForAllTime = parseLong(contact.getCustomFieldValue(AmoCrmFieldId.BUDGET_FOR_ALL_TIME_CONTACT));
            var wholeBudget = price + priceForAllTime;

            var customFields = Map.of(
                    AmoCrmFieldId.COUNT_RELEASED_ORDERS_CONTACT.getId(), String.valueOf(countReleasedOrders),
                    AmoCrmFieldId.RELEASED_LEADS_LIST_CONTACT.getId(), updatedListReleasedLeads,
                    AmoCrmFieldId.BUDGET_FOR_ALL_TIME_CONTACT.getId(), String.valueOf(wholeBudget)
            );
            amoCrmGateway.updateContactCustomField(contact.getId(), customFields);
        }

        if (!result.getSuccess()) {
            log.warn("Lead not updated because unsuccess sync");
            return;
        }

        // Двигаем статус только для розницы (когда в сделке есть товары).
        boolean isRetail = result.getItemsCount() != null && result.getItemsCount() > 0;
        if (isRetail) {
            leadAmoCrmStatusUpdater.moveToReadyToDeliver(leadId);
            if (!alreadyCounted) {
                enqueueRetailTelegramNotification(leadId);
            }
        } else {
            log.info("Lead {} has no products (not retail), skip moveToReadyToDeliver", leadId);
        }
    }

    private void enqueueRetailTelegramNotification(Long leadId) {
        Order order = orderRepository.findByLeadId(leadId).orElse(null);
        if (order == null) {
            log.warn("Lead {}: заказ не найден, телеграм-уведомление о рознице не поставлено", leadId);
            return;
        }
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            log.info("Lead {}: заказ #{} из маркетплейса, телеграм-уведомление уже отправлено при оплате", leadId, order.getId());
            return;
        }
        telegramNotificationQueue.enqueue(order.getId());
        log.info("Lead {}: заказ #{} поставлен в очередь телеграм-уведомлений (заберёт telegram-pusher)", leadId, order.getId());
    }

    private Long parseLong(String str) {
        if (str == null) return 0L;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            log.warn("не получилось спарсить стрингу", e);
        }
        return 0L;
    }
}
