package ru.anyforms.service.salesbot.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.BotGroup;
import ru.anyforms.repository.BotGroupRepository;
import ru.anyforms.service.salesbot.ActiveGroup;
import ru.anyforms.service.salesbot.BotGroupDirectory;
import ru.anyforms.service.salesbot.FunnelTarget;
import ru.anyforms.service.salesbot.TimeWindow;

import java.util.List;

/**
 * Адаптер {@link BotGroupDirectory} поверх {@link BotGroupRepository}. Группе без своего окна
 * отправки подставляется окно по умолчанию ({@code salesbot.run.window-msk}).
 */
@Component
class BotGroupDirectoryImpl implements BotGroupDirectory {

    private final BotGroupRepository repository;
    private final TimeWindow defaultWindow;

    BotGroupDirectoryImpl(BotGroupRepository repository,
                          @Value("${salesbot.run.window-msk}") String defaultWindowMsk) {
        this.repository = repository;
        this.defaultWindow = TimeWindow.parse(defaultWindowMsk);
    }

    @Override
    public List<ActiveGroup> activeGroups() {
        return repository.findByEnabledTrueAndPipelineIdNotNullAndStatusIdNotNullOrderByIdAsc().stream()
                .map(g -> new ActiveGroup(g.getId(), g.getName(),
                        new FunnelTarget(g.getPipelineId(), g.getStatusId()), windowOf(g)))
                .toList();
    }

    private TimeWindow windowOf(BotGroup group) {
        return group.hasSendWindow()
                ? new TimeWindow(group.sendFromTime(), group.sendToTime())
                : defaultWindow;
    }
}
