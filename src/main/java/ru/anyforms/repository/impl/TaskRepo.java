package ru.anyforms.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
interface TaskRepo extends JpaRepository<Task, UUID> {

    @Query("""
            select t from Task t
            where t.type = :type
              and t.status = :status
              and (t.nextAttemptAt is null or t.nextAttemptAt <= :now)
            order by t.createdAt asc
            """)
    List<Task> findDue(@Param("type") TaskType type,
                       @Param("status") TaskStatus status,
                       @Param("now") Instant now,
                       Pageable pageable);

    @Query("""
            select t from Task t
            where t.type = :type
              and t.status = :status
              and t.createdAt < :createdBefore
              and (t.nextAttemptAt is null or t.nextAttemptAt <= :now)
            order by t.createdAt asc
            """)
    List<Task> findDueCreatedBefore(@Param("type") TaskType type,
                                    @Param("status") TaskStatus status,
                                    @Param("createdBefore") Instant createdBefore,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    List<Task> findByTypeOrderByCreatedAtDesc(TaskType type, Pageable pageable);

    List<Task> findByType(TaskType type);

    boolean existsByTypeAndPayloadContaining(TaskType type, String payloadFragment);
}
