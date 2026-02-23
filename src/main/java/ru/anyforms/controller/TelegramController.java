package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.anyforms.service.telegram.BotService;
import ru.anyforms.service.telegram.UpdateProcessor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/telegram")
@Tag(name = "Telegram")
public class TelegramController {

    private final BotService botService;
    private final UpdateProcessor updateProcessor;

    @Operation(summary = "Вебхук для телеграма бота.")
    @RequestMapping(value = "/callback/update", method = RequestMethod.POST) // main
    public ResponseEntity<?> onUpdateReceived(@RequestBody Update update) {
        if (update.hasMessage() && update.getMessage().getChat().getType().equals("private")
                || update.hasCallbackQuery()
                && update.getCallbackQuery().getMessage().isUserMessage()) {
            updateProcessor.processUpdate(update);
            return ResponseEntity.ok().build();
        }
        return new ResponseEntity<>("cannot handle non private messages", HttpStatus.BAD_REQUEST);
    }
}
