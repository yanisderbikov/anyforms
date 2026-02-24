package ru.anyforms.util;

import ru.anyforms.dto.amo.AmoNewMessageAccount;
import ru.anyforms.dto.amo.AmoNewMessageAuthor;
import ru.anyforms.dto.amo.AmoNewMessageEntity;
import ru.anyforms.dto.amo.AmoNewMessageItem;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;

import java.util.List;
import java.util.Map;

/**
 * Парсит результат FormDataParser (Map с вложенной структурой message[add][0][...])
 * в типизированный AmoNewMessageWebhookPayload.
 */
public final class AmoNewMessagePayloadParser {

    private AmoNewMessagePayloadParser() {
    }

    @SuppressWarnings("unchecked")
    public static AmoNewMessageWebhookPayload parse(Map<String, Object> parsed) {
        if (parsed == null) {
            return null;
        }
        AmoNewMessageWebhookPayload payload = new AmoNewMessageWebhookPayload();
        payload.setAccount(parseAccount((Map<String, Object>) parsed.get("account")));
        payload.setMessage(parseFirstMessage(parsed.get("message")));
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static AmoNewMessageAccount parseAccount(Map<String, Object> accountMap) {
        if (accountMap == null) {
            return null;
        }
        AmoNewMessageAccount account = new AmoNewMessageAccount();
        account.setSubdomain(getString(unwrapDeep(accountMap.get("subdomain"), "subdomain")));
        account.setId(getLong(unwrapDeep(accountMap.get("id"), "id")));
        Object links = accountMap.get("_links");
        if (links instanceof Map) {
            Object self = ((Map<String, Object>) links).get("self");
            account.setSelfLink(getString(unwrapDeep(self, "self")));
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    private static AmoNewMessageItem parseFirstMessage(Object messageObj) {
        if (messageObj == null || !(messageObj instanceof Map)) {
            return null;
        }
        Map<String, Object> messageMap = (Map<String, Object>) messageObj;
        Object addObj = messageMap.get("add");
        if (addObj == null || !(addObj instanceof List)) {
            return null;
        }
        List<?> addList = (List<?>) addObj;
        if (addList.isEmpty()) return null;
        Object first = addList.get(0);
        if (!(first instanceof Map)) return null;
        return parseMessageItem((Map<String, Object>) first);
    }

    @SuppressWarnings("unchecked")
    private static AmoNewMessageItem parseMessageItem(Map<String, Object> map) {
        AmoNewMessageItem item = new AmoNewMessageItem();
        item.setId(getString(unwrapDeep(map.get("id"), "id")));
        item.setType(getString(unwrapDeep(map.get("type"), "type")));
        item.setText(getString(unwrapDeep(map.get("text"), "text")));
        item.setCreatedAt(getLong(unwrapDeep(map.get("created_at"), "created_at")));
        item.setOrigin(getString(unwrapDeep(map.get("origin"), "origin")));
        item.setChatId(getString(unwrapDeep(map.get("chat_id"), "chat_id")));
        item.setTalkId(getString(unwrapDeep(map.get("talk_id"), "talk_id")));
        item.setContactId(getLong(unwrapDeep(map.get("contact_id"), "contact_id")));
        item.setEntity(parseEntity(map));
        Object authorObj = map.get("author");
        if (authorObj instanceof Map) {
            item.setAuthor(parseAuthor((Map<String, Object>) authorObj));
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private static AmoNewMessageEntity parseEntity(Map<String, Object> messageMap) {
        Object entityObj = messageMap.get("entity");
        if (entityObj instanceof Map) {
            Map<String, Object> entityMap = (Map<String, Object>) entityObj;
            AmoNewMessageEntity entity = new AmoNewMessageEntity();
            entity.setType(getString(unwrapDeep(entityMap.get("type"), "type")));
            entity.setId(getLong(unwrapDeep(entityMap.get("id"), "id")));
            return entity;
        }
        // Форма присылает entity_type, entity_id на уровне сообщения
        String entityType = getString(unwrapDeep(messageMap.get("entity_type"), "entity_type"));
        Long entityId = getLong(unwrapDeep(messageMap.get("entity_id"), "entity_id"));
        if (entityId == null) {
            entityId = getLong(unwrapDeep(messageMap.get("element_id"), "element_id"));
        }
        if (entityType != null || entityId != null) {
            AmoNewMessageEntity entity = new AmoNewMessageEntity();
            entity.setType(entityType);
            entity.setId(entityId);
            return entity;
        }
        return null;
    }

    private static AmoNewMessageAuthor parseAuthor(Map<String, Object> map) {
        AmoNewMessageAuthor author = new AmoNewMessageAuthor();
        author.setId(getString(unwrapDeep(map.get("id"), "id")));
        author.setType(getString(unwrapDeep(map.get("type"), "type")));
        author.setName(getString(unwrapDeep(map.get("name"), "name")));
        author.setAvatarUrl(getString(unwrapDeep(map.get("avatar_url"), "avatar_url")));
        return author;
    }

    /**
     * Если значение пришло как вложенная Map (например {id=uuid}), рекурсивно достаёт значение по ключу.
     */
    @SuppressWarnings("unchecked")
    private static Object unwrapDeep(Object o, String key) {
        if (o == null) return null;
        if (o instanceof Map) {
            Object next = ((Map<String, Object>) o).get(key);
            return unwrapDeep(next, key);
        }
        return o;
    }

    private static String getString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long getLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer getInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
