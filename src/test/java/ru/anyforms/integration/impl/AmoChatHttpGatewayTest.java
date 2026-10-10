package ru.anyforms.integration.impl;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoTalk;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmoChatHttpGatewayTest {

    private final AmoChatHttpGateway gateway = new AmoChatHttpGateway();

    @Test
    void parsesMessagesArrayWithDirections() {
        String json = "["
                + "{\"id\":\"m3\",\"created_at\":1759600000,\"msec_created_at\":1759600000123,"
                + "\"author\":{\"id\":\"u1\",\"full_name\":\"Ян\",\"origin\":\"amocrm\"},"
                + "\"message\":{\"type\":\"text\",\"text\":\"Добрый день\"}},"
                + "{\"id\":\"m2\",\"created_at\":1759599000,"
                + "\"author\":{\"name\":\"Salesbot\",\"bot\":true},\"message\":{\"type\":\"text\",\"text\":\"Трек: 123\"}},"
                + "{\"id\":\"m1\",\"created_at\":1759598000,"
                + "\"author\":{\"full_name\":\"\",\"name\":\"Анна\",\"origin\":\"telegram\"},"
                + "\"message\":{\"type\":\"picture\",\"text\":\"\",\"media\":\"https://x/y.jpg\",\"media_file_name\":\"y.jpg\"},"
                + "\"error\":{\"code\":0}}"
                + "]";

        List<AmoChatMessage> messages = gateway.parseMessages(json);

        assertEquals(3, messages.size());
        AmoChatMessage manager = messages.get(0);
        assertEquals(AmoChatMessage.Direction.OUT, manager.direction());
        assertEquals(AmoChatMessage.AuthorType.MANAGER, manager.authorType());
        assertEquals(Instant.ofEpochMilli(1759600000123L), manager.createdAt());
        assertEquals("Добрый день", manager.text());

        AmoChatMessage bot = messages.get(1);
        assertEquals(AmoChatMessage.Direction.OUT, bot.direction());
        assertEquals(AmoChatMessage.AuthorType.BOT, bot.authorType());

        AmoChatMessage client = messages.get(2);
        assertEquals(AmoChatMessage.Direction.IN, client.direction());
        assertEquals(AmoChatMessage.AuthorType.CLIENT, client.authorType());
        assertEquals("Анна", client.author());
        assertEquals("picture", client.type());
        assertEquals("y.jpg", client.fileName());
        assertNull(client.error());
    }

    @Test
    void parsesMessageListWrapperAndEmptyBody() {
        List<AmoChatMessage> wrapped = gateway.parseMessages("{\"message_list\":[{\"id\":\"a\",\"created_at\":1,\"text\":\"hi\"}]}");

        assertEquals(1, wrapped.size());
        assertEquals("hi", wrapped.get(0).text());
        assertTrue(gateway.parseMessages("").isEmpty());
    }

    @Test
    void parsesSessionAndTalk() {
        var session = gateway.parseSession("{\"response\":{\"chats\":{\"session\":{\"access_token\":\"tok\",\"expired_at\":1760000000}}}}");
        AmoTalk talk = gateway.parseTalk(JsonParser.parseString(
                "{\"talk_id\":42,\"chat_id\":\"c-1\",\"contact_id\":7,\"entity_id\":100,\"entity_type\":\"lead\","
                        + "\"origin\":\"telegram\",\"is_read\":true,\"updated_at\":1759600000}").getAsJsonObject());

        assertEquals("tok", session.get("access_token").getAsString());
        assertNull(gateway.parseSession("{\"response\":{}}"));
        assertEquals(42L, talk.talkId());
        assertEquals("c-1", talk.chatId());
        assertEquals(100L, talk.entityId());
        assertEquals(Boolean.TRUE, talk.isRead());
    }
}
