package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.BotSequence;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotSequenceRepository extends JpaRepository<BotSequence, Long> {

    /** Цепочка группы, отсортированная по возрастанию позиции. */
    List<BotSequence> findByGroupIdOrderByPositionAsc(Long groupId);

    /** Все шаги всех групп — для админки (группа, затем позиция по возрастанию). */
    List<BotSequence> findAllByOrderByGroupIdAscPositionAsc();

    /** Последний шаг цепочки группы (максимальная позиция) — новый бот добавляется за ним. */
    Optional<BotSequence> findFirstByGroupIdOrderByPositionDesc(Long groupId);

    /** Ближайший шаг выше (позиция меньше) — для сдвига «вверх»; позиции могут идти с пропусками. */
    Optional<BotSequence> findFirstByGroupIdAndPositionLessThanOrderByPositionDesc(Long groupId, Integer position);

    /** Ближайший шаг ниже (позиция больше) — для сдвига «вниз». */
    Optional<BotSequence> findFirstByGroupIdAndPositionGreaterThanOrderByPositionAsc(Long groupId, Integer position);

    /** Есть ли бот уже в цепочке группы (один бот — не более одного раза на группу). */
    Optional<BotSequence> findByGroupIdAndBotId(Long groupId, Long botId);
}
