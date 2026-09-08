package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.model.salesbot.OrderType;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotSequenceRepository extends JpaRepository<BotSequence, Long> {

    /** Цепочка ботов типа, отсортированная по возрастанию позиции. */
    List<BotSequence> findByTypeOrderByPositionAsc(OrderType type);

    /** Все шаги всех типов — для админки (тип, затем позиция по возрастанию). */
    List<BotSequence> findAllByOrderByTypeAscPositionAsc();

    /** Шаг на позиции в рамках типа: позиции уникальны ({@code UNIQUE(type, position)}). */
    Optional<BotSequence> findByTypeAndPosition(OrderType type, Integer position);

    /** Последний шаг цепочки типа (максимальная позиция) — новый бот добавляется за ним. */
    Optional<BotSequence> findFirstByTypeOrderByPositionDesc(OrderType type);

    /** Ближайший шаг выше (позиция меньше) — для сдвига «вверх»; позиции могут идти с пропусками. */
    Optional<BotSequence> findFirstByTypeAndPositionLessThanOrderByPositionDesc(OrderType type, Integer position);

    /** Ближайший шаг ниже (позиция больше) — для сдвига «вниз». */
    Optional<BotSequence> findFirstByTypeAndPositionGreaterThanOrderByPositionAsc(OrderType type, Integer position);

    /** Есть ли бот уже в цепочке типа (один бот — не более одного раза на тип). */
    Optional<BotSequence> findByTypeAndBotId(OrderType type, Long botId);
}
