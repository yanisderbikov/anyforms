package ru.anyforms.model.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "telegram_subscriber")
@Getter
@Setter
public class TelegramSubscriber {

    @Id
    @Column(name = "chat_id", nullable = false, unique = true)
    private String chatId;

    @Column(name = "username")
    private String username;
}
