package ru.anyforms.service.email;

/**
 * Рендерит письмо о покупке под конкретный продукт (своя тема и свой HTML-шаблон).
 * Вызывается в момент исполнения таски, а не при её постановке.
 */
public interface PurchaseEmailRenderer {

    /** Готовое письмо: тема + HTML. */
    record RenderedEmail(String subject, String html) {
    }

    /** Собирает письмо по коду продукта. Бросает исключение, если для продукта нет шаблона. */
    RenderedEmail render(String productCode);
}
