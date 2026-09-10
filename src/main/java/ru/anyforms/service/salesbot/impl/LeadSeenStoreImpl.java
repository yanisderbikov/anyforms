package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.salesbot.BotGroupLeadSeen;
import ru.anyforms.repository.BotGroupLeadSeenRepository;
import ru.anyforms.service.salesbot.LeadSeenStore;

import java.time.Instant;

/** Адаптер {@link LeadSeenStore} поверх {@link BotGroupLeadSeenRepository}. */
@Component
@AllArgsConstructor
class LeadSeenStoreImpl implements LeadSeenStore {

    private final BotGroupLeadSeenRepository repository;

    @Override
    @Transactional
    public Instant firstSeenOrRecord(Long groupId, Long leadId, Instant now) {
        return repository.findByGroupIdAndLeadId(groupId, leadId)
                .map(BotGroupLeadSeen::getFirstSeenAt)
                .orElseGet(() -> {
                    repository.insertIfAbsent(groupId, leadId, now);
                    return repository.findByGroupIdAndLeadId(groupId, leadId)
                            .map(BotGroupLeadSeen::getFirstSeenAt)
                            .orElse(now);
                });
    }
}
