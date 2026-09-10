package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotGroup;

import java.util.List;

@Repository
public interface BotGroupRepository extends JpaRepository<BotGroup, Long> {

    List<BotGroup> findAllByOrderByIdAsc();

    /** Группы, которые участвуют в прогоне: включены и с заданной воронкой/статусом. */
    List<BotGroup> findByEnabledTrueAndPipelineIdNotNullAndStatusIdNotNullOrderByIdAsc();
}
