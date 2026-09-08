package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoSalesbot;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SalesbotDirectory} поверх {@link AmoCrmGateway#getSalesbots()} с кэшем на {@link #TTL}
 * (см. {@link TtlSnapshotCache}).
 */
@Slf4j
@Component
class SalesbotDirectoryImpl implements SalesbotDirectory {

    static final Duration TTL = Duration.ofMinutes(5);

    private final TtlSnapshotCache<AmoSalesbot> cache;

    /** Конструктор для Spring: два конструктора в классе, поэтому явно указываем, какой внедрять. */
    @Autowired
    SalesbotDirectoryImpl(AmoCrmGateway amoCrmGateway) {
        this(amoCrmGateway, Clock.systemUTC());
    }

    /** Для тестов: подменяемые часы, чтобы проверять TTL кэша. */
    SalesbotDirectoryImpl(AmoCrmGateway amoCrmGateway, Clock clock) {
        this.cache = new TtlSnapshotCache<>("список ботов", amoCrmGateway::getSalesbots, TTL, clock);
    }

    @Override
    public List<AmoSalesbot> bots(boolean refresh) {
        return cache.get(refresh);
    }

    @Override
    public Map<Long, String> namesById() {
        List<AmoSalesbot> bots;
        try {
            bots = bots(false);
        } catch (IllegalStateException e) {
            log.warn("Salesbot names unavailable: {}", e.getMessage());
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (AmoSalesbot bot : bots) {
            if (bot.id() != null && bot.name() != null) {
                names.put(bot.id(), bot.name());
            }
        }
        return names;
    }
}
