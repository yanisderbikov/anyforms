package ru.anyforms.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderFreeDeliveryTest {

    @Test
    void markFreeDeliverySetsFlagAndComment() {
        Order order = new Order();

        order.markFreeDelivery();

        assertTrue(order.isFreeDelivery());
        assertEquals("Бесплатная доставка", order.getComment());
    }

    @Test
    void markFreeDeliveryKeepsExistingComment() {
        Order order = new Order();
        order.setComment("Позвонить перед отправкой");

        order.markFreeDelivery();

        assertEquals("Позвонить перед отправкой | Бесплатная доставка", order.getComment());
    }

    @Test
    void markFreeDeliveryTwiceDoesNotDuplicateComment() {
        Order order = new Order();

        order.markFreeDelivery();
        order.markFreeDelivery();

        assertEquals("Бесплатная доставка", order.getComment());
    }

    @Test
    void regularOrderHasNoComment() {
        assertNull(new Order().getComment());
    }
}
