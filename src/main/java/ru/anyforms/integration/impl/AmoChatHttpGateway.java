package ru.anyforms.integration.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoChatMessages;
import ru.anyforms.model.amo.AmoLeadChat;
import ru.anyforms.model.amo.AmoTalk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "amocrm.enabled", havingValue = "true", matchIfMissing = true)
class AmoChatHttpGateway implements AmoChatGateway {

    private static final int PAGE = 100;
    private static final int MAX_LIMIT = 1000;
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Value("${amocrm.subdomain}")
    private String subdomain;

    @Value("${amocrm.access.token}")
    private String accessToken;

    @Value("${amocrm.amojo.url}")
    private String amojoUrl;

    @Value("${amocrm.min-request-interval-ms}")
    private long minIntervalMs;

    private WebClient amoClient;
    private WebClient amojoClient;

    private final Object slotLock = new Object();
    private long nextAllowedAtMs = 0L;

    private volatile String amojoId;
    private String sessionToken;
    private long sessionExpiresAt;

    @jakarta.annotation.PostConstruct
    private void init() {
        this.amoClient = WebClient.builder()
                .baseUrl("https://" + subdomain + ".amocrm.ru")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
        this.amojoClient = WebClient.builder()
                .baseUrl(amojoUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Override
    public List<AmoTalk> getLeadTalks(Long leadId, boolean includeContactChats) {
        Map<String, AmoTalk> byChat = new LinkedHashMap<>();
        mergeTalks(byChat, listTalks("filter[entity_type]=lead&filter[entity_id][0]=" + leadId), leadId);
        if (includeContactChats) {
            for (Long contactId : getLeadContactIds(leadId)) {
                mergeTalks(byChat, getContactTalks(contactId), leadId);
            }
        }
        List<AmoTalk> talks = new ArrayList<>(byChat.values());
        talks.sort(Comparator.comparing((AmoTalk t) -> t.updatedAt() != null ? t.updatedAt() : 0L).reversed());
        return talks;
    }

    @Override
    public List<AmoTalk> getContactTalks(Long contactId) {
        return listTalks("filter[contact_id][0]=" + contactId);
    }

    @Override
    public AmoChatMessages getChatMessages(String chatId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), MAX_LIMIT);
        List<AmoChatMessage> out = new ArrayList<>();
        boolean hasMore = false;
        while (out.size() < safeLimit) {
            int want = Math.min(PAGE, safeLimit - out.size());
            List<AmoChatMessage> page = fetchPage(chatId, safeOffset + out.size(), want);
            out.addAll(page);
            if (page.size() < want) {
                hasMore = false;
                break;
            }
            hasMore = true;
        }
        Collections.reverse(out);
        return new AmoChatMessages(chatId, out, hasMore);
    }

    @Override
    public List<AmoLeadChat> getLeadChats(Long leadId, int offset, int limit, boolean includeContactChats) {
        List<AmoLeadChat> result = new ArrayList<>();
        for (AmoTalk talk : getLeadTalks(leadId, includeContactChats)) {
            AmoChatMessages messages = getChatMessages(talk.chatId(), offset, limit);
            result.add(new AmoLeadChat(talk, messages.messages(), messages.hasMore()));
        }
        return result;
    }

    private void mergeTalks(Map<String, AmoTalk> byChat, List<AmoTalk> talks, Long leadId) {
        for (AmoTalk t : talks) {
            if (t.chatId() == null) {
                continue;
            }
            AmoTalk prev = byChat.get(t.chatId());
            boolean tOwn = leadId.equals(t.entityId());
            boolean better = prev == null
                    || (tOwn && !leadId.equals(prev.entityId()))
                    || (tOwn == leadId.equals(prev.entityId()) && orZero(t.updatedAt()) > orZero(prev.updatedAt()));
            if (better) {
                byChat.put(t.chatId(), t);
            }
        }
    }

    private List<AmoTalk> listTalks(String filterQuery) {
        String response = amoGet("/api/v4/talks?" + filterQuery + "&limit=250");
        List<AmoTalk> talks = new ArrayList<>();
        for (JsonElement el : embedded(response, "talks")) {
            talks.add(parseTalk(el.getAsJsonObject()));
        }
        return talks;
    }

    private List<Long> getLeadContactIds(Long leadId) {
        String response = amoGet("/api/v4/leads/" + leadId + "/links?filter[to_entity_type]=contacts");
        List<Long> ids = new ArrayList<>();
        for (JsonElement el : embedded(response, "links")) {
            Long id = longOrNull(el.getAsJsonObject(), "to_entity_id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String getAmojoId() {
        if (amojoId == null) {
            String response = amoGet("/api/v4/account?with=amojo_id");
            String id = response != null ? stringOrNull(JsonParser.parseString(response).getAsJsonObject(), "amojo_id") : null;
            if (id == null) {
                throw new IllegalStateException("amoCRM не вернул amojo_id: в аккаунте не подключены чаты");
            }
            amojoId = id;
        }
        return amojoId;
    }

    private synchronized String getSessionToken(boolean force) {
        long now = System.currentTimeMillis() / 1000;
        if (!force && sessionToken != null && sessionExpiresAt - 60 > now) {
            return sessionToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("request[chats][session][action]", "create");
        acquireSlot();
        String response = amoClient.post()
                .uri("/ajax/v1/chats/session")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new RuntimeException("amoCRM chats session " + r.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .block(TIMEOUT);
        JsonObject session = parseSession(response);
        String token = session != null ? stringOrNull(session, "access_token") : null;
        if (token == null) {
            String preview = response == null ? "" : response.substring(0, Math.min(500, response.length()));
            throw new IllegalStateException("Не удалось получить сессию чатов amojo: " + preview);
        }
        Long expiredAt = longOrNull(session, "expired_at");
        sessionToken = token;
        sessionExpiresAt = expiredAt != null ? expiredAt : now + 3600;
        return token;
    }

    private List<AmoChatMessage> fetchPage(String chatId, int offset, int limit) {
        String uri = "/messages/" + getAmojoId() + "/merge?stand=v16&offset=" + offset + "&limit=" + limit + "&chat_id[]=" + chatId;
        for (int attempt = 0; ; attempt++) {
            String token = getSessionToken(attempt > 0);
            AmojoResponse res = amojoClient.get()
                    .uri(uri)
                    .header("X-Auth-Token", token)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(r -> r.bodyToMono(String.class).defaultIfEmpty("")
                            .map(body -> new AmojoResponse(r.statusCode().value(), body)))
                    .block(TIMEOUT);
            if (res == null || res.status() == 204) {
                return List.of();
            }
            if ((res.status() == 401 || res.status() == 403) && attempt == 0) {
                continue;
            }
            if (res.status() >= 400) {
                throw new RuntimeException("amojo GET /messages/merge " + res.status() + ": " + res.body());
            }
            return parseMessages(res.body());
        }
    }

    JsonObject parseSession(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        JsonElement root = JsonParser.parseString(response);
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject();
        for (String key : new String[]{"response", "chats", "session"}) {
            if (!obj.has(key) || !obj.get(key).isJsonObject()) {
                return null;
            }
            obj = obj.getAsJsonObject(key);
        }
        return obj;
    }

    List<AmoChatMessage> parseMessages(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonElement root = JsonParser.parseString(body);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("message_list")
                && root.getAsJsonObject().get("message_list").isJsonArray()) {
            array = root.getAsJsonObject().getAsJsonArray("message_list");
        } else {
            return List.of();
        }
        List<AmoChatMessage> messages = new ArrayList<>();
        for (JsonElement el : array) {
            if (el.isJsonObject()) {
                messages.add(parseMessage(el.getAsJsonObject()));
            }
        }
        return messages;
    }

    AmoChatMessage parseMessage(JsonObject m) {
        JsonObject author = objOrNull(m, "author");
        JsonObject message = objOrNull(m, "message");
        JsonObject error = objOrNull(m, "error");

        String origin = author != null ? stringOrNull(author, "origin") : null;
        boolean bot = author != null && author.has("bot") && !author.get("bot").isJsonNull() && author.get("bot").getAsBoolean();
        boolean manager = "amocrm".equals(origin);

        String authorName = null;
        if (author != null) {
            authorName = stringOrNull(author, "full_name");
            if (authorName == null || authorName.isEmpty()) {
                authorName = stringOrNull(author, "name");
            }
            if (authorName != null && authorName.isEmpty()) {
                authorName = null;
            }
        }

        String text = message != null ? stringOrNull(message, "text") : null;
        if (text == null) {
            text = stringOrNull(m, "text");
        }

        Long createdAt = longOrNull(m, "created_at");
        Long msec = longOrNull(m, "msec_created_at");
        Instant created = msec != null ? Instant.ofEpochMilli(msec)
                : createdAt != null ? Instant.ofEpochSecond(createdAt) : null;

        String type = message != null ? stringOrNull(message, "type") : null;
        Long duration = message != null ? longOrNull(message, "media_duration") : null;

        String errorText = null;
        Long errorCode = error != null ? longOrNull(error, "code") : null;
        if (errorCode != null && errorCode != 0) {
            String description = stringOrNull(error, "description");
            errorText = description != null ? errorCode + ": " + description : String.valueOf(errorCode);
        }

        return new AmoChatMessage(
                stringOrNull(m, "id"),
                created,
                bot || manager ? AmoChatMessage.Direction.OUT : AmoChatMessage.Direction.IN,
                authorName,
                manager ? AmoChatMessage.AuthorType.MANAGER : bot ? AmoChatMessage.AuthorType.BOT : AmoChatMessage.AuthorType.CLIENT,
                type != null ? type : "text",
                text != null ? text : "",
                emptyToNull(message != null ? stringOrNull(message, "media") : null),
                emptyToNull(message != null ? stringOrNull(message, "media_file_name") : null),
                duration != null && duration != 0 ? duration.intValue() : null,
                errorText
        );
    }

    AmoTalk parseTalk(JsonObject t) {
        return new AmoTalk(
                longOrNull(t, "talk_id"),
                stringOrNull(t, "chat_id"),
                stringOrNull(t, "origin"),
                longOrNull(t, "contact_id"),
                stringOrNull(t, "entity_type"),
                longOrNull(t, "entity_id"),
                stringOrNull(t, "status"),
                t.has("is_read") && !t.get("is_read").isJsonNull() ? t.get("is_read").getAsBoolean() : null,
                longOrNull(t, "created_at"),
                longOrNull(t, "updated_at")
        );
    }

    private String amoGet(String uri) {
        acquireSlot();
        return amoClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new RuntimeException("amoCRM GET " + uri + " " + r.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .block(TIMEOUT);
    }

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

    private static JsonArray embedded(String response, String key) {
        if (response == null || response.isBlank()) {
            return new JsonArray();
        }
        JsonObject root = JsonParser.parseString(response).getAsJsonObject();
        JsonObject embedded = objOrNull(root, "_embedded");
        if (embedded == null || !embedded.has(key) || !embedded.get(key).isJsonArray()) {
            return new JsonArray();
        }
        return embedded.getAsJsonArray(key);
    }

    private static JsonObject objOrNull(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
    }

    private static String stringOrNull(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static Long longOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return obj.get(key).getAsLong();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static long orZero(Long v) {
        return v != null ? v : 0L;
    }

    private record AmojoResponse(int status, String body) {
    }
}
