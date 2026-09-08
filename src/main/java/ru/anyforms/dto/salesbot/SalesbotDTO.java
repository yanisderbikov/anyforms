package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.amo.AmoSalesbot;

@Schema(description = "SalesBot аккаунта amoCRM — для выбора бота по имени; в bot_sequence пишется id")
public record SalesbotDTO(
        @Schema(description = "ID бота в amoCRM (bot_id)") Long id,
        @Schema(description = "Название бота в редакторе amoCRM") String name,
        @Schema(description = "Тип: regular / greeting / marketing / nps; может отсутствовать") String type
) {
    public static SalesbotDTO from(AmoSalesbot bot) {
        return new SalesbotDTO(bot.id(), bot.name(), bot.typeFunctionality());
    }
}
