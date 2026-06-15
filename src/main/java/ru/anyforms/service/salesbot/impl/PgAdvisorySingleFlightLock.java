package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.SingleFlightLock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Single-flight через advisory-lock PostgreSQL ({@code pg_try_advisory_lock}).
 * <p>
 * Лок «сессионный» — привязан к соединению, поэтому одно соединение из пула
 * удерживается на всё время прогона и освобождается в {@code finally}. Так лок не
 * держит транзакцию открытой (сетевые вызовы amo идут вне БД-транзакции), а сам лок —
 * на уровне БД, т.е. работает и при нескольких инстансах приложения (кластерный мьютекс).
 */
@Slf4j
@Component
class PgAdvisorySingleFlightLock implements SingleFlightLock {

    private final DataSource dataSource;
    private final long lockKey;

    PgAdvisorySingleFlightLock(DataSource dataSource,
                               @Value("${salesbot.lock.key}") long lockKey) {
        this.dataSource = dataSource;
        this.lockKey = lockKey;
    }

    @Override
    public boolean runExclusively(Runnable action) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                log.info("Salesbot run skipped: another run already holds the advisory lock (key={})", lockKey);
                return false;
            }
            try {
                action.run();
                return true;
            } finally {
                unlock(connection);
            }
        } catch (Exception e) {
            log.error("Single-flight lock execution failed (key={})", lockKey, e);
            return false;
        }
    }

    private boolean tryLock(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, lockKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, lockKey);
            ps.execute();
        } catch (Exception e) {
            // Соединение всё равно вернётся в пул/закроется; для сессионного лока это не критично.
            log.warn("Failed to release advisory lock (key={})", lockKey, e);
        }
    }
}
