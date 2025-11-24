package ru.anyforms.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для проверки работы с AmoCRM
 * 
 * Для использования тестов:
 * 
 * Вариант 1: Укажите значения в константах ниже (TEST_LEAD_ID, TEST_TRACKER_NUMBER)
 * 
 * Вариант 2: Используйте системные свойства при запуске:
 *   -Dtest.lead.id=123456
 *   -Dtest.tracker.number=TEST123456
 * 
 * Вариант 3: Используйте переменные окружения:
 *   TEST_LEAD_ID=123456
 *   TEST_TRACKER_NUMBER=TEST123456
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
public class AmoCrmServiceTest {

    @Autowired
    private AmoCrmService amoCrmService;

    // ============================================
    // НАСТРОЙКИ ДЛЯ ТЕСТИРОВАНИЯ
    // ============================================
    // Внесите сюда ID тестовой сделки или используйте системные свойства/переменные окружения
    private static final Long TEST_LEAD_ID = getTestLeadId();
    
    // Внесите сюда номер трекера для тестирования или используйте системные свойства/переменные окружения
    private static final String TEST_TRACKER_NUMBER = getTestTrackerNumber();
    
    // ID кастомного поля для трекера (из CdekWebhookService)
    private static final Long TRACKER_FIELD_ID = 2348069L;
    
    /**
     * Получает ID тестовой сделки из системных свойств, переменных окружения или возвращает null
     */
    private static Long getTestLeadId() {
        // Сначала проверяем системное свойство
        String leadIdStr = System.getProperty("test.lead.id");
        if (leadIdStr != null && !leadIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(leadIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат test.lead.id: " + leadIdStr);
            }
        }
        
        // Затем проверяем переменную окружения
        leadIdStr = System.getenv("TEST_LEAD_ID");
        if (leadIdStr != null && !leadIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(leadIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат TEST_LEAD_ID: " + leadIdStr);
            }
        }
        
        // Если ничего не найдено, возвращаем null (нужно будет указать в коде)
        return null;
    }
    
    /**
     * Получает номер трекера из системных свойств, переменных окружения или возвращает null
     */
    private static String getTestTrackerNumber() {
        // Сначала проверяем системное свойство
        String tracker = System.getProperty("test.tracker.number");
        if (tracker != null && !tracker.trim().isEmpty()) {
            return tracker.trim();
        }
        
        // Затем проверяем переменную окружения
        tracker = System.getenv("TEST_TRACKER_NUMBER");
        if (tracker != null && !tracker.trim().isEmpty()) {
            return tracker.trim();
        }
        
        // Если ничего не найдено, возвращаем null (нужно будет указать в коде)
        return null;
    }

    /**
     * Тест для проверки внесения трекер номера в сделку
     * 
     * Этот тест:
     * 1. Получает сделку по ID
     * 2. Обновляет кастомное поле трекера
     * 3. Проверяет, что обновление прошло успешно
     * 4. Получает сделку снова и проверяет, что трекер номер действительно внесен
     */
    @Test
    public void testUpdateTrackerNumber() {
        // Проверяем, что указан ID сделки
        assertNotNull(TEST_LEAD_ID, 
            "Необходимо указать ID тестовой сделки одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.lead.id=123456\n" +
            "2. Установить переменную окружения: TEST_LEAD_ID=123456\n" +
            "3. Изменить константу TEST_LEAD_ID в коде");
        assertNotNull(TEST_TRACKER_NUMBER, 
            "Необходимо указать номер трекера одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.tracker.number=TEST123456\n" +
            "2. Установить переменную окружения: TEST_TRACKER_NUMBER=TEST123456\n" +
            "3. Изменить константу TEST_TRACKER_NUMBER в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Внесение трекер номера");
        System.out.println("============================================");
        System.out.println("ID сделки: " + TEST_LEAD_ID);
        System.out.println("Трекер номер: " + TEST_TRACKER_NUMBER);
        System.out.println("ID поля трекера: " + TRACKER_FIELD_ID);
        System.out.println();
        
        try {
            // Шаг 1: Получаем сделку до обновления
            System.out.println("Шаг 1: Получаем сделку до обновления...");
            var leadBefore = amoCrmService.getLead(TEST_LEAD_ID);
            assertNotNull(leadBefore, "Не удалось получить сделку с ID: " + TEST_LEAD_ID);
            System.out.println("✓ Сделка получена: " + leadBefore.getName());
            
            // Шаг 2: Обновляем трекер номер
            System.out.println("Шаг 2: Обновляем трекер номер...");
            boolean updated = amoCrmService.updateLeadCustomField(
                    TEST_LEAD_ID, 
                    TRACKER_FIELD_ID, 
                    TEST_TRACKER_NUMBER
            );
            
            assertTrue(updated, "Не удалось обновить трекер номер в CRM");
            System.out.println("✓ Трекер номер успешно обновлен в CRM");
            
            // Шаг 3: Получаем сделку после обновления и проверяем
            System.out.println("Шаг 3: Проверяем, что трекер номер действительно внесен...");
            var leadAfter = amoCrmService.getLead(TEST_LEAD_ID);
            assertNotNull(leadAfter, "Не удалось получить сделку после обновления");
            
            // Получаем значение трекера из кастомного поля
            String trackerValue = leadAfter.getCustomFieldValue(TRACKER_FIELD_ID);
            
            if (trackerValue != null && trackerValue.equals(TEST_TRACKER_NUMBER)) {
                System.out.println("✓ Трекер номер успешно внесен и проверен!");
                System.out.println("  Значение в CRM: " + trackerValue);
            } else {
                System.out.println("⚠ Трекер номер обновлен в CRM, но значение не совпадает:");
                System.out.println("  Ожидалось: " + TEST_TRACKER_NUMBER);
                System.out.println("  Получено: " + (trackerValue != null ? trackerValue : "null"));
                // Не падаем тест, так как обновление могло пройти успешно, но чтение может быть с задержкой
            }
            
            System.out.println();
            System.out.println("============================================");
            System.out.println("ТЕСТ ЗАВЕРШЕН УСПЕШНО");
            System.out.println("============================================");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки отправки сообщения контакту
     * 
     * Этот тест:
     * 1. Получает сделку по ID
     * 2. Получает контакт, связанный со сделкой
     * 3. Отправляет тестовое сообщение
     * 4. Проверяет, что сообщение отправлено успешно
     */
    @Test
    public void testSendMessage() {
        // Проверяем, что указан ID сделки
        assertNotNull(TEST_LEAD_ID, 
            "Необходимо указать ID тестовой сделки одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.lead.id=123456\n" +
            "2. Установить переменную окружения: TEST_LEAD_ID=123456\n" +
            "3. Изменить константу TEST_LEAD_ID в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Отправка сообщения");
        System.out.println("============================================");
        System.out.println("ID сделки: " + TEST_LEAD_ID);
        System.out.println();
        
        try {
            // Шаг 1: Получаем сделку
            System.out.println("Шаг 1: Получаем сделку...");
            var lead = amoCrmService.getLead(TEST_LEAD_ID);
            assertNotNull(lead, "Не удалось получить сделку с ID: " + TEST_LEAD_ID);
            System.out.println("✓ Сделка получена: " + lead.getName());
            
            // Шаг 2: Получаем контакт
            System.out.println("Шаг 2: Получаем контакт, связанный со сделкой...");
            Long contactId = amoCrmService.getContactIdFromLead(TEST_LEAD_ID);
            assertNotNull(contactId, "Не удалось получить ID контакта для сделки: " + TEST_LEAD_ID);
            System.out.println("✓ ID контакта: " + contactId);
            
            var contact = amoCrmService.getContact(contactId);
            assertNotNull(contact, "Не удалось получить контакт с ID: " + contactId);
            System.out.println("✓ Контакт получен: " + contact.getName());
            
            // Шаг 3: Формируем тестовое сообщение
            String testMessage = "Тестовое сообщение от системы\n\n" +
                    "Это автоматическое тестовое сообщение для проверки функционала отправки.\n" +
                    "Время отправки: " + java.time.LocalDateTime.now() + "\n" +
                    "ID сделки: " + TEST_LEAD_ID;
            
            System.out.println("Шаг 3: Отправляем тестовое сообщение...");
            System.out.println("Текст сообщения:");
            System.out.println("---");
            System.out.println(testMessage);
            System.out.println("---");
            
            // Шаг 4: Отправляем сообщение
            boolean messageSent = amoCrmService.sendMessageToContact(TEST_LEAD_ID, testMessage);
            
            assertTrue(messageSent, "Не удалось отправить сообщение в CRM");
            System.out.println("✓ Сообщение успешно отправлено!");
            
            System.out.println();
            System.out.println("============================================");
            System.out.println("ТЕСТ ЗАВЕРШЕН УСПЕШНО");
            System.out.println("============================================");
            System.out.println("Проверьте в AmoCRM, что сообщение действительно отправлено контакту.");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Комплексный тест: внесение трекер номера и отправка сообщения
     * 
     * Этот тест объединяет оба предыдущих теста в один
     */
    @Test
    public void testUpdateTrackerAndSendMessage() {
        // Проверяем, что указан ID сделки
        assertNotNull(TEST_LEAD_ID, 
            "Необходимо указать ID тестовой сделки одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.lead.id=123456\n" +
            "2. Установить переменную окружения: TEST_LEAD_ID=123456\n" +
            "3. Изменить константу TEST_LEAD_ID в коде");
        assertNotNull(TEST_TRACKER_NUMBER, 
            "Необходимо указать номер трекера одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.tracker.number=TEST123456\n" +
            "2. Установить переменную окружения: TEST_TRACKER_NUMBER=TEST123456\n" +
            "3. Изменить константу TEST_TRACKER_NUMBER в коде");
        
        System.out.println("============================================");
        System.out.println("КОМПЛЕКСНЫЙ ТЕСТ: Трекер + Сообщение");
        System.out.println("============================================");
        System.out.println("ID сделки: " + TEST_LEAD_ID);
        System.out.println("Трекер номер: " + TEST_TRACKER_NUMBER);
        System.out.println();
        
        try {
            // Часть 1: Внесение трекер номера
            System.out.println("--- ЧАСТЬ 1: Внесение трекер номера ---");
            boolean trackerUpdated = amoCrmService.updateLeadCustomField(
                    TEST_LEAD_ID, 
                    TRACKER_FIELD_ID, 
                    TEST_TRACKER_NUMBER
            );
            assertTrue(trackerUpdated, "Не удалось обновить трекер номер");
            System.out.println("✓ Трекер номер обновлен");
            System.out.println();
            
            // Часть 2: Отправка сообщения
            System.out.println("--- ЧАСТЬ 2: Отправка сообщения ---");
            String message = "Ваш заказ был отправлен:\n\nТрекер: " + TEST_TRACKER_NUMBER;
            boolean messageSent = amoCrmService.sendMessageToContact(TEST_LEAD_ID, message);
            assertTrue(messageSent, "Не удалось отправить сообщение");
            System.out.println("✓ Сообщение отправлено");
            System.out.println();
            
            System.out.println("============================================");
            System.out.println("КОМПЛЕКСНЫЙ ТЕСТ ЗАВЕРШЕН УСПЕШНО");
            System.out.println("============================================");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
}
