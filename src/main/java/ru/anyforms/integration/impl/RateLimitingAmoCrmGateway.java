package ru.anyforms.integration.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoProduct;

import java.util.List;
import java.util.Map;

/**
 * Декоратор {@link AmoCrmGateway}, который глобально ограничивает частоту обращений к amoCRM:
 * между любыми двумя запросами (из ЛЮБОГО потока) выдерживается минимум
 * {@code amocrm.min-request-interval-ms}. Защита от 429 без усложнения бизнес-логики —
 * вся она просто зовёт {@link AmoCrmGateway}, ничего не зная про троттлинг.
 * <p>
 * Помечен {@link Primary}, поэтому внедряется везде вместо «сырого» {@link AmoCrmHttpGateway}
 * (который остаётся нетронутым и используется как делегат). Очереди/асинхронности нет:
 * вызывающий поток просто ждёт свой «слот».
 * <p>
 * Реализация: общий счётчик {@code nextAllowedAtMs} под {@code synchronized} атомарно
 * резервирует следующий слот, а сам {@link Thread#sleep} выполняется ВНЕ лока — потоки не
 * толпятся на мониторе и естественно расходятся по времени. На виртуальных потоках
 * {@code sleep} размонтирует носитель.
 * <p>
 * Замечание: пара методов делегата внутри делает несколько HTTP-запросов
 * (напр. {@code getContactFromLead}); такой составной вызов резервирует один слот.
 * Это лишь делает лимит чуть мягче фактического и приемлемо при запасе к лимиту amo (~7 req/s).
 */
@Primary
@Component
class RateLimitingAmoCrmGateway implements AmoCrmGateway {

    private final AmoCrmHttpGateway delegate;
    private final long minIntervalMs;

    private final Object slotLock = new Object();
    private long nextAllowedAtMs = 0L;

    RateLimitingAmoCrmGateway(AmoCrmHttpGateway delegate,
                              @Value("${amocrm.min-request-interval-ms}") long minIntervalMs) {
        this.delegate = delegate;
        this.minIntervalMs = minIntervalMs;
    }

    /** Резервирует следующий слот и ждёт до него. Потокобезопасно, без очереди. */
    private void acquireSlot() {
        if (minIntervalMs <= 0) {
            return;
        }
        long waitMs;
        synchronized (slotLock) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextAllowedAtMs);
            nextAllowedAtMs = slot + minIntervalMs;
            waitMs = slot - now;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void setNewTask(Long responsibleUser, Long taskType, String taskMessage, Long leadId, int minutesToComplete) {
        acquireSlot();
        delegate.setNewTask(responsibleUser, taskType, taskMessage, leadId, minutesToComplete);
    }

    @Override
    public AmoLead getLead(Long leadId) {
        acquireSlot();
        return delegate.getLead(leadId);
    }

    @Override
    public AmoContact getContact(Long contactId) {
        acquireSlot();
        return delegate.getContact(contactId);
    }

    @Override
    public Long getContactIdFromLead(Long leadId) {
        acquireSlot();
        return delegate.getContactIdFromLead(leadId);
    }

    @Override
    public AmoContact getContactFromLead(Long leadId) {
        acquireSlot();
        return delegate.getContactFromLead(leadId);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status) {
        acquireSlot();
        return delegate.updateLeadStatus(leadId, status);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId) {
        acquireSlot();
        return delegate.updateLeadStatus(leadId, status, pipelineId);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId) {
        acquireSlot();
        return delegate.updateLeadStatus(leadId, statusId, pipelineId);
    }

    @Override
    public boolean updateLeadStatus(List<Long> leadIds, Long statusId, Long pipelineId) {
        acquireSlot();
        return delegate.updateLeadStatus(leadIds, statusId, pipelineId);
    }

    @Override
    public boolean updateLeadCustomField(Long leadId, Long fieldId, String value) {
        acquireSlot();
        return delegate.updateLeadCustomField(leadId, fieldId, value);
    }

    @Override
    public boolean updateContactCustomField(Long contactId, Map<Long, String> customFields) {
        acquireSlot();
        return delegate.updateContactCustomField(contactId, customFields);
    }

    @Override
    public boolean sendMessageToContact(Long leadId, String message) {
        acquireSlot();
        return delegate.sendMessageToContact(leadId, message);
    }

    @Override
    public boolean updateLeadFields(Long leadId, Long price, Map<Long, String> customFields) {
        acquireSlot();
        return delegate.updateLeadFields(leadId, price, customFields);
    }

    @Override
    public boolean addNoteToLead(Long leadId, String noteText) {
        acquireSlot();
        return delegate.addNoteToLead(leadId, noteText);
    }

    @Override
    public List<AmoProduct> getLeadProducts(Long leadId) {
        acquireSlot();
        return delegate.getLeadProducts(leadId);
    }

    @Override
    public List<Long> getLeadIdsOlderThanTwoWeeks(Long pipelineId, Long statusId, Long closedTo) {
        acquireSlot();
        return delegate.getLeadIdsOlderThanTwoWeeks(pipelineId, statusId, closedTo);
    }

    @Override
    public List<Long> getLeadIdsByStatus(Long pipelineId, Long statusId) {
        acquireSlot();
        return delegate.getLeadIdsByStatus(pipelineId, statusId);
    }

    @Override
    public boolean runSalesbot(Long leadId, Long botId) {
        acquireSlot();
        return delegate.runSalesbot(leadId, botId);
    }

    @Override
    public Long createLandingLead(String leadName, String contactName, String phone) {
        acquireSlot();
        return delegate.createLandingLead(leadName, contactName, phone);
    }

    @Override
    public Long createLead(String leadName, String contactName, String phone, Long pipelineId, Long statusId) {
        acquireSlot();
        return delegate.createLead(leadName, contactName, phone, pipelineId, statusId);
    }

    @Override
    public boolean linkCatalogElementsToLead(Long leadId, Long catalogId, Map<Long, Integer> elementIdToQuantity) {
        acquireSlot();
        return delegate.linkCatalogElementsToLead(leadId, catalogId, elementIdToQuantity);
    }
}
