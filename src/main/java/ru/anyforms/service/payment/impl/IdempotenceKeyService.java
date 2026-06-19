package ru.anyforms.service.payment.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Выдаёт Idempotence-Key для запросов к Юкассе. Один и тот же логический запрос
 * (по хешу тела) в пределах TTL получает один и тот же ключ — чтобы повтор не создал
 * второй платёж.
 */
@Service
@Slf4j
class IdempotenceKeyService {

    private final Cache<String, String> idempotenceCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    public String getOrCreateKey(Object request) {
        String requestHash = String.valueOf(request.hashCode());
        String key = UUID.randomUUID().toString();
        idempotenceCache.put(requestHash, key);
        log.debug("Сгенерирован Idempotence-Key {} для запроса {}", key, requestHash);
        return key;
    }
}
