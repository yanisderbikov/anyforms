package ru.anyforms.service;

import ru.anyforms.model.AmoWebhook;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeadIdExtractionService {

    /**
     * Извлекает все ID лидов из AmoWebhook объекта
     */
    public List<Long> extractLeadIdsFromWebhook(AmoWebhook webhook) {
        if (webhook.getLeads() == null) {
            return new ArrayList<>();
        }

        List<Long> leadIds = new ArrayList<>();
        
        if (webhook.getLeads().getCallIn() != null) {
            leadIds.addAll(webhook.getLeads().getCallIn().stream()
                    .map(e -> e.getId()).collect(Collectors.toList()));
        }
        if (webhook.getLeads().getChat() != null) {
            leadIds.addAll(webhook.getLeads().getChat().stream()
                    .map(e -> e.getId()).collect(Collectors.toList()));
        }
        if (webhook.getLeads().getSiteVisit() != null) {
            leadIds.addAll(webhook.getLeads().getSiteVisit().stream()
                    .map(e -> e.getId()).collect(Collectors.toList()));
        }
        if (webhook.getLeads().getMailIn() != null) {
            leadIds.addAll(webhook.getLeads().getMailIn().stream()
                    .map(e -> e.getId()).collect(Collectors.toList()));
        }
        if (webhook.getLeads().getStatus() != null) {
            leadIds.addAll(webhook.getLeads().getStatus().stream()
                    .map(e -> e.getId()).collect(Collectors.toList()));
        }

        return leadIds;
    }

    /**
     * Извлекает ID лидов из form-data структуры для события "add"
     */
    @SuppressWarnings("unchecked")
    public List<Long> extractLeadIdsFromFormDataAdd(Map<String, Object> leads) {
        List<Long> leadIds = new ArrayList<>();
        
        if (leads == null) {
            return leadIds;
        }
        
        List<Object> addList = (List<Object>) leads.get("add");
        if (addList != null) {
            for (Object item : addList) {
                Map<String, Object> leadData = (Map<String, Object>) item;
                Long leadId = extractIdFromMap(leadData);
                if (leadId != null) {
                    leadIds.add(leadId);
                }
            }
        }
        
        return leadIds;
    }

    /**
     * Извлекает ID лидов из form-data структуры для других типов событий
     */
    @SuppressWarnings("unchecked")
    public List<Long> extractLeadIdsFromFormDataEvents(Map<String, Object> leads) {
        List<Long> leadIds = new ArrayList<>();
        
        if (leads == null) {
            return leadIds;
        }
        
        String[] eventTypes = {"status", "mail_in", "call_in", "chat", "site_visit"};
        
        for (String eventType : eventTypes) {
            Object eventData = leads.get(eventType);
            if (eventData instanceof List) {
                List<Object> events = (List<Object>) eventData;
                for (Object event : events) {
                    if (event instanceof Map) {
                        Map<String, Object> eventMap = (Map<String, Object>) event;
                        Long leadId = extractIdFromMap(eventMap);
                        if (leadId != null) {
                            leadIds.add(leadId);
                        }
                    }
                }
            }
        }
        
        return leadIds;
    }

    /**
     * Извлекает ID из Map структуры, обрабатывая вложенные Map
     */
    @SuppressWarnings("unchecked")
    private Long extractIdFromMap(Map<String, Object> map) {
        Object idObj = map.get("id");
        if (idObj == null) {
            return null;
        }
        
        // Если idObj это Map, извлекаем значение по ключу "id"
        if (idObj instanceof Map) {
            Map<String, Object> idMap = (Map<String, Object>) idObj;
            idObj = idMap.get("id");
        }
        
        return parseLong(idObj);
    }

    /**
     * Парсит объект в Long
     */
    private Long parseLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

