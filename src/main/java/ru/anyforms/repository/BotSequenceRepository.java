package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.model.salesbot.OrderType;

import java.util.List;

@Repository
public interface BotSequenceRepository extends JpaRepository<BotSequence, Long> {

    /** Цепочка ботов типа, отсортированная по возрастанию позиции. */
    List<BotSequence> findByTypeOrderByPositionAsc(OrderType type);
}
