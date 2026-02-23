package ru.anyforms.dto.telegram;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@AllArgsConstructor
@Data
public class TelegramMessageDTO {
    @NonNull
    @NotBlank(message = "Message не должен быть пустым или null")
    private String message;
    List<Button> buttons;
}
