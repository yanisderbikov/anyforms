package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotExecutionLog;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.OrderType;

import java.time.Instant;
import java.util.List;

@Repository
public interface BotExecutionLogRepository extends JpaRepository<BotExecutionLog, Long> {

    /**
     * Позиции, успешно отработавшие для лида в рамках типа. По ним считается прогресс цепочки.
     */
    @Query("""
            SELECT l.position FROM BotExecutionLog l
            WHERE l.leadId = :leadId
              AND l.type = :type
              AND l.status = ru.anyforms.model.salesbot.BotExecutionStatus.SUCCESS
            """)
    List<Integer> findSuccessPositions(@Param("leadId") Long leadId, @Param("type") OrderType type);

    /**
     * Был ли у лида хоть один успешно запущенный бот начиная с {@code dayStart}
     * (UTC-полночь текущих суток). Будущих записей не бывает, поэтому это эквивалентно
     * «успешно отправляли сегодня». Используется как дневной guard от повторной отправки.
     */
    boolean existsByLeadIdAndStatusAndDateExecutedGreaterThanEqual(
            Long leadId, BotExecutionStatus status, Instant dayStart);

    /**
     * Идемпотентная запись результата (жёсткий бэкстоп против двойной отправки).
     * <p>
     * {@code ON CONFLICT (lead_id, bot_id) DO UPDATE} позволяет «повысить» прежний FAILED
     * (лид временно вышел из статуса) до SUCCESS, когда бот реально уедет. Двойной отправки
     * это не вызывает: бот запускается только когда для него ещё нет success-записи, а
     * параллельные прогоны исключены single-flight локом.
     * <p>
     * {@code type}/{@code status} передаются строками (enum хранится как varchar, {@code EnumType.STRING}).
     */
    @Modifying
    @Query(value = """
            INSERT INTO bot_execution_log (lead_id, bot_id, position, type, status, date_executed)
            VALUES (:leadId, :botId, :position, :type, :status, :dateExecuted)
            ON CONFLICT (lead_id, bot_id)
            DO UPDATE SET position = EXCLUDED.position,
                          type = EXCLUDED.type,
                          status = EXCLUDED.status,
                          date_executed = EXCLUDED.date_executed
            """, nativeQuery = true)
    void upsert(@Param("leadId") Long leadId,
                @Param("botId") Long botId,
                @Param("position") Integer position,
                @Param("type") String type,
                @Param("status") String status,
                @Param("dateExecuted") Instant dateExecuted);

    /**
     * Проставляет {@code status} на последнюю по {@code date_executed} запись лида —
     * бот, который только что пытался отправить сообщение. Используется при обработке
     * вебхука {@code fail-send-message}.
     *
     * @return число обновлённых строк (0, если у лида ещё нет ни одной записи)
     */
    @Modifying
    @Query(value = """
            UPDATE bot_execution_log
            SET status = :status
            WHERE id = (
                SELECT id FROM bot_execution_log
                WHERE lead_id = :leadId
                ORDER BY date_executed DESC
                LIMIT 1
            )
            """, nativeQuery = true)
    int markLatestStatus(@Param("leadId") Long leadId, @Param("status") String status);
}
