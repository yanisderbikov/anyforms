package ru.anyforms.repository.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.telegram.TelegramSubscriber;

import java.util.List;

@Repository
interface TelegramSubscriberRepository extends JpaRepository<TelegramSubscriber, String> {

    @Query("SELECT s.chatId FROM TelegramSubscriber s")
    List<String> findAllChatIds();

}
