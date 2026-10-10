package ru.anyforms.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.anyforms.repository.TransactionLock;

import java.util.Collection;

@Component
@RequiredArgsConstructor
@Log4j2
class PgAdvisoryTransactionLock implements TransactionLock {

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void lock(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Блокировка " + key + " возможна только внутри транзакции");
        }
        try {
            jdbcTemplate.query(LOCK_SQL, (RowCallbackHandler) rs -> { }, key);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void lockAll(Collection<String> keys) {
        keys.stream().distinct().sorted().forEach(this::lock);
    }
}
