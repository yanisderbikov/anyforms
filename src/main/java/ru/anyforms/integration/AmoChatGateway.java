package ru.anyforms.integration;

import ru.anyforms.model.amo.AmoChatMessages;
import ru.anyforms.model.amo.AmoLeadChat;
import ru.anyforms.model.amo.AmoTalk;

import java.util.List;

public interface AmoChatGateway {

    int DEFAULT_LIMIT = 100;

    List<AmoTalk> getLeadTalks(Long leadId, boolean includeContactChats);

    List<AmoTalk> getContactTalks(Long contactId);

    AmoChatMessages getChatMessages(String chatId, int offset, int limit);

    List<AmoLeadChat> getLeadChats(Long leadId, int offset, int limit, boolean includeContactChats);

    default List<AmoLeadChat> getLeadChats(Long leadId) {
        return getLeadChats(leadId, 0, DEFAULT_LIMIT, true);
    }

    default AmoChatMessages getChatMessages(String chatId) {
        return getChatMessages(chatId, 0, DEFAULT_LIMIT);
    }
}
