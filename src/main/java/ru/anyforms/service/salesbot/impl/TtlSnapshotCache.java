package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Снимок списка из amoCRM с TTL. Пока снимок свежий — отдаём его; иначе перечитываем.
 * Если перечитать не удалось, а старый снимок есть — отдаём старый (имена лучше старые,
 * чем никакие); если снимка нет — бросаем {@link IllegalStateException}.
 */
@Slf4j
final class TtlSnapshotCache<T> {

    private final String what;
    private final Supplier<List<T>> loader;
    private final Duration ttl;
    private final Clock clock;

    private final Object refreshLock = new Object();
    private volatile Snapshot<T> snapshot;

    TtlSnapshotCache(String what, Supplier<List<T>> loader, Duration ttl, Clock clock) {
        this.what = what;
        this.loader = loader;
        this.ttl = ttl;
        this.clock = clock;
    }

    List<T> get(boolean refresh) {
        Snapshot<T> current = snapshot;
        if (!refresh && current != null && !current.isStale(clock.instant(), ttl)) {
            return current.items();
        }
        synchronized (refreshLock) {
            current = snapshot;
            if (!refresh && current != null && !current.isStale(clock.instant(), ttl)) {
                return current.items();
            }
            try {
                List<T> loaded = List.copyOf(loader.get());
                snapshot = new Snapshot<>(loaded, clock.instant());
                return loaded;
            } catch (RuntimeException e) {
                if (current != null) {
                    log.warn("{}: refresh failed, serving cached snapshot from {}: {}", what, current.loadedAt(), e.toString());
                    return current.items();
                }
                throw new IllegalStateException("Не удалось получить " + what + " из amoCRM: " + e.getMessage(), e);
            }
        }
    }

    private record Snapshot<T>(List<T> items, Instant loadedAt) {
        boolean isStale(Instant now, Duration ttl) {
            return loadedAt.plus(ttl).isBefore(now);
        }
    }
}
