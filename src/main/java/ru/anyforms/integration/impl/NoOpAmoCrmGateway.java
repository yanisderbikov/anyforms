package ru.anyforms.integration.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoProduct;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "amocrm.enabled", havingValue = "false")
class NoOpAmoCrmGateway implements AmoCrmGateway {

    private void skip(String method) {
        log.debug("amoCRM disabled (amocrm.enabled=false), skipping {}", method);
    }

    @Override
    public void setNewTask(Long responsibleUser, Long taskType, String taskMessage, Long leadId, int minutesToComplete) {
        skip("setNewTask");
    }

    @Override
    public AmoLead getLead(Long leadId) {
        skip("getLead");
        return null;
    }

    @Override
    public AmoContact getContact(Long contactId) {
        skip("getContact");
        return null;
    }

    @Override
    public Long getContactIdFromLead(Long leadId) {
        skip("getContactIdFromLead");
        return null;
    }

    @Override
    public AmoContact getContactFromLead(Long leadId) {
        skip("getContactFromLead");
        return null;
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status) {
        skip("updateLeadStatus");
        return true;
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId) {
        skip("updateLeadStatus");
        return true;
    }

    @Override
    public boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId) {
        skip("updateLeadStatus");
        return true;
    }

    @Override
    public boolean updateLeadStatus(List<Long> leadIds, Long statusId, Long pipelineId) {
        skip("updateLeadStatus");
        return true;
    }

    @Override
    public boolean updateLeadCustomField(Long leadId, Long fieldId, String value) {
        skip("updateLeadCustomField");
        return true;
    }

    @Override
    public boolean updateContactCustomField(Long contactId, Map<Long, String> customFields) {
        skip("updateContactCustomField");
        return true;
    }

    @Override
    public boolean sendMessageToContact(Long leadId, String message) {
        skip("sendMessageToContact");
        return true;
    }

    @Override
    public boolean updateLeadFields(Long leadId, Long price, Map<Long, ?> customFields) {
        skip("updateLeadFields");
        return true;
    }

    @Override
    public boolean addNoteToLead(Long leadId, String noteText) {
        skip("addNoteToLead");
        return true;
    }

    @Override
    public List<AmoProduct> getLeadProducts(Long leadId) {
        skip("getLeadProducts");
        return List.of();
    }

    @Override
    public List<Long> getLeadIdsOlderThanTwoWeeks(Long pipelineId, Long statusId, Long closedTo) {
        skip("getLeadIdsOlderThanTwoWeeks");
        return List.of();
    }

    @Override
    public List<Long> getLeadIdsByStatus(Long pipelineId, Long statusId) {
        skip("getLeadIdsByStatus");
        return List.of();
    }

    @Override
    public boolean runSalesbot(Long leadId, Long botId) {
        skip("runSalesbot");
        return true;
    }

    @Override
    public Long createLandingLead(String leadName, String contactName, String phone,
                                  Long pipelineId, Long statusId, Map<String, String> utmByFieldCode) {
        skip("createLandingLead");
        return null;
    }

    @Override
    public Long createLead(String leadName, String contactName, String phone, Long pipelineId, Long statusId) {
        skip("createLead");
        return null;
    }

    @Override
    public boolean linkCatalogElementsToLead(Long leadId, Long catalogId, Map<Long, Integer> elementIdToQuantity) {
        skip("linkCatalogElementsToLead");
        return true;
    }

    @Override
    public List<AmoProduct> getCatalogElements(Long catalogId) {
        skip("getCatalogElements");
        return List.of();
    }
}
