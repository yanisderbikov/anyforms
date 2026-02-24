package ru.anyforms.util.pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты для {@link MessagePatternOrder#isNeedToMove(String)}.
 * Заполните списки MESSAGES_SHOULD_MATCH и MESSAGES_SHOULD_NOT_MATCH своими кейсами.
 */
class MessagePatternOrderTest {

    /**
     * Сообщения, на которые паттерн ДОЛЖЕН среагировать (isNeedToMove == true).
     * Заполните своими кейсами.
     */
    private static final List<String> MESSAGES_SHOULD_MATCH = List.of(
            "Хочу заказать молд",
            "Нужен молд",
            "Нужна форма для заливки",
            "Нужны формы под изделие",
            "Заказать форму можно?",
            "Сделаете молд по ТЗ?",
            "Надо изготовить молд под свечи",
            "Можно сделать силиконовую форму?",
            "Сколько стоит сделать молд?",
            "Посчитайте стоимость молда 2 шт",
            "Рассчитайте цену на форму по моему размеру",
            "Какие сроки изготовления молда?",
            "Когда будет готова форма?",
            "Оформим заказ на молд",
            "Давайте оформим заказ",
            "Закажу у вас молд, что нужно от меня?",
            "заказть молд",                 // опечатка
            "заказат форму",                // опечатка
            "Нужно изготовление формы, есть 3D модель stl",
            "Есть модель в STL, хочу сделать молд",
            "У меня STEP файл, нужно изготовить форму",
            "Можете сделать матрицу под литье полиуретана?",
            "Нужна форма под эпоксидку",
            "Есть чертеж, нужно отлить форму из силикона",
            "Нужен молд, доставка СДЭК возможна?",
            "Сколько стоит и какие сроки на изготовление формы?",
            "Куда отправить размеры, чтобы вы посчитали молд?",
            "Нужно 10 шт форм, сможете изготовить?",
            "Хочу купить форму, как оплатить?",
            "Сможете изготовить молд по фото и размерам?"
            // добавьте свои кейсы...
    );

    /**
     * Сообщения, на которые паттерн НЕ должен среагировать (isNeedToMove == false).
     * Заполните своими кейсами.
     */
    private static final List<String> MESSAGES_SHOULD_NOT_MATCH = List.of(
            "Здравствуйте",
            "Привет!",
            "Добрый день",
            "Доброе утро, как дела?",
            "Спасибо",
            "Ок",
            "Понял, благодарю",
            "Супер",
            "Классно",
            "А вы где находитесь?",
            "Скиньте адрес",
            "Какой у вас график работы?",
            "Есть ли у вас сайт?",
            "Пришлите каталог",
            "Какие у вас соцсети?",
            "Я просто интересуюсь",
            "Пока не актуально",
            "Не нужно, спасибо",
            "Я передумал",
            "Отмена",
            "Это реклама?",
            "Спам",
            "Сколько стоит доставка вообще? (без заказа)",
            "Подскажите, что такое молд?",     // справочный вопрос
            "Какие бывают силиконы?",          // справочный вопрос
            "У вас есть вакансия литейщика?",
            "Мне нужен чек по прошлому заказу",
            "Где мой трек-номер? Я уже заказывал",
            "Можно вернуть деньги за доставку?",
            "Вы работаете с юрлицами?"
            // добавьте свои кейсы...
    );

    static Stream<String> messagesShouldMatch() {
        return MESSAGES_SHOULD_MATCH.stream();
    }

    static Stream<String> messagesShouldNotMatch() {
        return MESSAGES_SHOULD_NOT_MATCH.stream();
    }

    @ParameterizedTest(name = "должен среагировать: \"{0}\"")
    @MethodSource("messagesShouldMatch")
    void shouldMatch(String message) {
        assertTrue(MessagePatternOrder.isNeedToMove(message), "Ожидалось isNeedToMove=true для: " + message);
    }

    @ParameterizedTest(name = "не должен среагировать: \"{0}\"")
    @MethodSource("messagesShouldNotMatch")
    void shouldNotMatch(String message) {
        assertFalse(MessagePatternOrder.isNeedToMove(message), "Ожидалось isNeedToMove=false для: " + message);
    }
}
