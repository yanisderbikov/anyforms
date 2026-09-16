package ru.anyforms.service;

/**
 * Удаление розничного заказа супер-админом (тестовые/ошибочные заказы).
 * Подчищает всё, что завязано на заказ: позиции, отметку о telegram-уведомлении,
 * отвязывает платежи (сами транзакции остаются для бухгалтерии).
 */
public interface RetailOrderDeleter {

    /**
     * @throws org.springframework.web.server.ResponseStatusException 404 — заказа нет,
     *         409 — заказ не розничный или у него есть позиции под заказ
     */
    void delete(Long orderId);
}
