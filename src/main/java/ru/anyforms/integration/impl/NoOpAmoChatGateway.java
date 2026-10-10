package ru.anyforms.integration.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.model.amo.AmoChatMessages;
import ru.anyforms.model.amo.AmoLeadChat;
import ru.anyforms.model.amo.AmoTalk;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "amocrm.enabled", havingValue = "false")
class NoOpAmoChatGateway implements AmoChatGateway {

    private void skip(String method) {
        log.debug("amoCRM disabled (amocrm.enabled=false), skipping {}", method);
    }

    @Override
    public List<AmoTalk> getLeadTalks(Long leadId, boolean includeContactChats) {
        skip("getLeadTalks");
        return List.of();
    }

    @Override
    public List<AmoTalk> getContactTalks(Long contactId) {
        skip("getContactTalks");
        return List.of();
    }

    @Override
    public AmoChatMessages getChatMessages(String chatId, int offset, int limit) {
        skip("getChatMessages");
        return new AmoChatMessages(chatId, List.of(), false);
    }

    @Override
    public List<AmoLeadChat> getLeadChats(Long leadId, int offset, int limit, boolean includeContactChats) {
        skip("getLeadChats");
        return List.of();
    }
}
