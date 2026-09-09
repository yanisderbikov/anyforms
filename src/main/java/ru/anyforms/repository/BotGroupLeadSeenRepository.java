package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotGroupLeadSeen;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface BotGroupLeadSeenRepository extends JpaRepository<BotGroupLeadSeen, Long> {

    Optional<BotGroupLeadSeen> findByGroupIdAndLeadId(Long groupId, Long leadId);

    /** Запоминает первое появление; повторный вызов ничего не меняет (якорь не сдвигается). */
    @Modifying
    @Query(value = """
            INSERT INTO bot_group_lead_seen (group_id, lead_id, first_seen_at)
            VALUES (:groupId, :leadId, :seenAt)
            ON CONFLICT (group_id, lead_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("groupId") Long groupId, @Param("leadId") Long leadId, @Param("seenAt") Instant seenAt);
}
