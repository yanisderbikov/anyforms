package ru.anyforms.model.amo;

/**
 * SalesBot из amoCRM ({@code GET /api/v4/bots}): нужен админке, чтобы выбирать бота
 * по имени, а в {@code bot_sequence} писать его {@code id}.
 *
 * @param id                ID бота (он же {@code bot_id} при запуске)
 * @param name              название бота в редакторе amoCRM
 * @param typeFunctionality тип: regular / greeting / marketing / nps; может быть {@code null}
 */
public record AmoSalesbot(Long id, String name, String typeFunctionality) {
}
