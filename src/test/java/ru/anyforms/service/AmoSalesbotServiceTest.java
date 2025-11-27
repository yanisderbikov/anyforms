package ru.anyforms.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ru.anyforms.model.SalesbotRunRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для проверки работы с Salesbot API в amoCRM
 * 
 * Для использования тестов:
 * 
 * Вариант 1: Укажите значения в константах ниже (TEST_BOT_ID, TEST_ENTITY_ID, TEST_ENTITY_TYPE)
 * 
 * Вариант 2: Используйте системные свойства при запуске:
 *   -Dtest.bot.id=565
 *   -Dtest.entity.id=76687686
 *   -Dtest.entity.type=1
 * 
 * Вариант 3: Используйте переменные окружения:
 *   TEST_BOT_ID=565
 *   TEST_ENTITY_ID=76687686
 *   TEST_ENTITY_TYPE=1
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
public class AmoSalesbotServiceTest {

    @Autowired
    private AmoSalesbotService amoSalesbotService;

    // ============================================
    // НАСТРОЙКИ ДЛЯ ТЕСТИРОВАНИЯ
    // ============================================
    // Внесите сюда ID тестового бота или используйте системные свойства/переменные окружения
    private static final Long TEST_BOT_ID = getTestBotId();
    
    // Внесите сюда ID тестовой сущности (контакт или сделка) или используйте системные свойства/переменные окружения
    private static final Long TEST_ENTITY_ID = getTestEntityId();
    
    // Тип сущности: 1 - контакт, 2 - сделка
    private static final Integer TEST_ENTITY_TYPE = getTestEntityType();
    
    /**
     * Получает ID тестового бота из системных свойств, переменных окружения или возвращает null
     */
    private static Long getTestBotId() {
        // Сначала проверяем системное свойство
        String botIdStr = System.getProperty("test.bot.id");
        if (botIdStr != null && !botIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(botIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат test.bot.id: " + botIdStr);
            }
        }
        
        // Затем проверяем переменную окружения
        botIdStr = System.getenv("TEST_BOT_ID");
        if (botIdStr != null && !botIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(botIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат TEST_BOT_ID: " + botIdStr);
            }
        }
        
        // Если ничего не найдено, возвращаем null (нужно будет указать в коде)
        return null;
    }
    
    /**
     * Получает ID тестовой сущности из системных свойств, переменных окружения или возвращает null
     */
    private static Long getTestEntityId() {
        // Сначала проверяем системное свойство
        String entityIdStr = System.getProperty("test.entity.id");
        if (entityIdStr != null && !entityIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(entityIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат test.entity.id: " + entityIdStr);
            }
        }
        
        // Затем проверяем переменную окружения
        entityIdStr = System.getenv("TEST_ENTITY_ID");
        if (entityIdStr != null && !entityIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(entityIdStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат TEST_ENTITY_ID: " + entityIdStr);
            }
        }
        
        // Если ничего не найдено, возвращаем null (нужно будет указать в коде)
        return null;
    }
    
    /**
     * Получает тип тестовой сущности из системных свойств, переменных окружения или возвращает 1 (контакт) по умолчанию
     */
    private static Integer getTestEntityType() {
        // Сначала проверяем системное свойство
        String entityTypeStr = System.getProperty("test.entity.type");
        if (entityTypeStr != null && !entityTypeStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(entityTypeStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат test.entity.type: " + entityTypeStr);
            }
        }
        
        // Затем проверяем переменную окружения
        entityTypeStr = System.getenv("TEST_ENTITY_TYPE");
        if (entityTypeStr != null && !entityTypeStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(entityTypeStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат TEST_ENTITY_TYPE: " + entityTypeStr);
            }
        }
        
        // По умолчанию возвращаем 1 (контакт)
        return 1;
    }

    /**
     * Тест для проверки запуска Salesbot для одной задачи
     * 
     * Этот тест:
     * 1. Запускает Salesbot с указанными параметрами
     * 2. Проверяет, что запуск прошел успешно
     */
    @Test
    public void testRunSalesbot() {
        // Проверяем, что указаны необходимые параметры
        assertNotNull(TEST_BOT_ID, 
            "Необходимо указать ID бота одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.bot.id=565\n" +
            "2. Установить переменную окружения: TEST_BOT_ID=565\n" +
            "3. Изменить константу TEST_BOT_ID в коде");
        assertNotNull(TEST_ENTITY_ID, 
            "Необходимо указать ID сущности одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.entity.id=76687686\n" +
            "2. Установить переменную окружения: TEST_ENTITY_ID=76687686\n" +
            "3. Изменить константу TEST_ENTITY_ID в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Запуск Salesbot");
        System.out.println("============================================");
        System.out.println("ID бота: " + TEST_BOT_ID);
        System.out.println("ID сущности: " + TEST_ENTITY_ID);
        System.out.println("Тип сущности: " + TEST_ENTITY_TYPE + " (" + 
            (TEST_ENTITY_TYPE == 1 ? "контакт" : TEST_ENTITY_TYPE == 2 ? "сделка" : "неизвестно") + ")");
        System.out.println();
        
        try {
            // Запускаем Salesbot
            System.out.println("Запускаем Salesbot...");
            boolean result = amoSalesbotService.runSalesbot(TEST_BOT_ID, TEST_ENTITY_ID, TEST_ENTITY_TYPE);
            
            assertTrue(result, "Не удалось запустить Salesbot");
            System.out.println("✓ Salesbot успешно запущен!");
            
            System.out.println();
            System.out.println("============================================");
            System.out.println("ТЕСТ ЗАВЕРШЕН УСПЕШНО");
            System.out.println("============================================");
            System.out.println("Проверьте в AmoCRM, что Salesbot действительно запущен.");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки запуска Salesbot для контакта
     */
    @Test
    public void testRunSalesbotForContact() {
        assertNotNull(TEST_BOT_ID, 
            "Необходимо указать ID бота одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.bot.id=565\n" +
            "2. Установить переменную окружения: TEST_BOT_ID=565\n" +
            "3. Изменить константу TEST_BOT_ID в коде");
        assertNotNull(TEST_ENTITY_ID, 
            "Необходимо указать ID контакта одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.entity.id=76687686\n" +
            "2. Установить переменную окружения: TEST_ENTITY_ID=76687686\n" +
            "3. Изменить константу TEST_ENTITY_ID в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Запуск Salesbot для контакта");
        System.out.println("============================================");
        System.out.println("ID бота: " + TEST_BOT_ID);
        System.out.println("ID контакта: " + TEST_ENTITY_ID);
        System.out.println();
        
        try {
            boolean result = amoSalesbotService.runSalesbotForContact(TEST_BOT_ID, TEST_ENTITY_ID);
            
            assertTrue(result, "Не удалось запустить Salesbot для контакта");
            System.out.println("✓ Salesbot успешно запущен для контакта!");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки запуска Salesbot для сделки
     */
    @Test
    public void testRunSalesbotForLead() {
        assertNotNull(TEST_BOT_ID, 
            "Необходимо указать ID бота одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.bot.id=565\n" +
            "2. Установить переменную окружения: TEST_BOT_ID=565\n" +
            "3. Изменить константу TEST_BOT_ID в коде");
        assertNotNull(TEST_ENTITY_ID, 
            "Необходимо указать ID сделки одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.entity.id=76687686\n" +
            "2. Установить переменную окружения: TEST_ENTITY_ID=76687686\n" +
            "3. Изменить константу TEST_ENTITY_ID в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Запуск Salesbot для сделки");
        System.out.println("============================================");
        System.out.println("ID бота: " + TEST_BOT_ID);
        System.out.println("ID сделки: " + TEST_ENTITY_ID);
        System.out.println();
        
        try {
            boolean result = amoSalesbotService.runSalesbotForLead(TEST_BOT_ID, TEST_ENTITY_ID);
            
            assertTrue(result, "Не удалось запустить Salesbot для сделки");
            System.out.println("✓ Salesbot успешно запущен для сделки!");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки запуска Salesbot для нескольких задач
     */
    @Test
    public void testRunSalesbotMultiple() {
        assertNotNull(TEST_BOT_ID, 
            "Необходимо указать ID бота одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.bot.id=565\n" +
            "2. Установить переменную окружения: TEST_BOT_ID=565\n" +
            "3. Изменить константу TEST_BOT_ID в коде");
        assertNotNull(TEST_ENTITY_ID, 
            "Необходимо указать ID сущности одним из способов:\n" +
            "1. Установить системное свойство: -Dtest.entity.id=76687686\n" +
            "2. Установить переменную окружения: TEST_ENTITY_ID=76687686\n" +
            "3. Изменить константу TEST_ENTITY_ID в коде");
        
        System.out.println("============================================");
        System.out.println("ТЕСТ: Запуск Salesbot для нескольких задач");
        System.out.println("============================================");
        System.out.println("ID бота: " + TEST_BOT_ID);
        System.out.println("ID сущности: " + TEST_ENTITY_ID);
        System.out.println();
        
        try {
            // Создаем список из нескольких задач
            List<SalesbotRunRequest> requests = new ArrayList<>();
            requests.add(new SalesbotRunRequest(TEST_BOT_ID, TEST_ENTITY_ID, TEST_ENTITY_TYPE));
            // Можно добавить еще задачи, если нужно
            // requests.add(new SalesbotRunRequest(TEST_BOT_ID, TEST_ENTITY_ID + 1, TEST_ENTITY_TYPE));
            
            System.out.println("Запускаем Salesbot для " + requests.size() + " задачи(задач)...");
            boolean result = amoSalesbotService.runSalesbot(requests);
            
            assertTrue(result, "Не удалось запустить Salesbot для нескольких задач");
            System.out.println("✓ Salesbot успешно запущен для " + requests.size() + " задачи(задач)!");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки валидации (пустой список)
     */
    @Test
    public void testRunSalesbotEmptyList() {
        System.out.println("============================================");
        System.out.println("ТЕСТ: Валидация пустого списка");
        System.out.println("============================================");
        
        try {
            List<SalesbotRunRequest> emptyList = new ArrayList<>();
            boolean result = amoSalesbotService.runSalesbot(emptyList);
            
            assertFalse(result, "Пустой список должен вернуть false");
            System.out.println("✓ Валидация работает корректно: пустой список отклонен");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }

    /**
     * Тест для проверки валидации (превышение лимита в 100 задач)
     */
    @Test
    public void testRunSalesbotExceedsLimit() {
        System.out.println("============================================");
        System.out.println("ТЕСТ: Валидация превышения лимита");
        System.out.println("============================================");
        
        try {
            // Создаем список из 101 задачи
            List<SalesbotRunRequest> requests = new ArrayList<>();
            for (int i = 0; i < 101; i++) {
                requests.add(new SalesbotRunRequest(1L, (long) i, 1));
            }
            
            boolean result = amoSalesbotService.runSalesbot(requests);
            
            assertFalse(result, "Список с более чем 100 задачами должен вернуть false");
            System.out.println("✓ Валидация работает корректно: превышение лимита отклонено");
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при выполнении теста:");
            e.printStackTrace();
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
}



