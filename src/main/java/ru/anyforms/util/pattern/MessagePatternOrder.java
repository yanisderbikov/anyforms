package ru.anyforms.util.pattern;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public class MessagePatternOrder {

    private static final Pattern HAS_PHONE = Pattern.compile("(\\+?\\d[\\d\\s()\\-]{8,}\\d)");
    private static final Pattern HAS_EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_LINK = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] STRONG_INTENT = new Pattern[] {
            // прямой заказ / действие
            Pattern.compile("\\b(хочу|нужно|надо|можно)\\b.*\\b(заказать|сделать|изготовить|купить)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(оформ(ить|ляем)\\s+заказ|давай(те)?\\s+заказ|беру)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(рассчита(й|йте)|посчита(й|йте)|калькуляц|сколько\\s+стоит|цена|прайс|стоимост)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(срок(и|ов)?|когда\\s+будет\\s+готово|изготовлен(ие|ия)|дедлайн)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(доставк(а|и)|отправк(а|и)|сдэк|почта|курьер|самовывоз)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),

            // предметная область
            Pattern.compile("\\b(молд|mold|форма|формы|матриц(а|ы)|отливк(а|и)|лить(е|ё)|заливк(а|и))\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(силикон|полиуретан|пластик|смола|эпоксид|резин(а|ы))\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(по\\s+тз|тз|чертеж|чертёж|3d|stl|step|iges|модель|рендер)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    private static final Pattern[] WEAK_INTENT = new Pattern[] {
            Pattern.compile("\\b(интересу(ет|ют)|подскаж(и|ите)|уточн(и|ите)|вопрос)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(консультац|посовет(уй|уйте)|как\\s+это\\s+делаете|как\\s+работаете)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(можете\\s+ли|возможн(о|а)\\s+ли)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(примерн(о|ая)\\s+цен(а|ник)|диапазон\\s+цен|от\\s+и\\s+до)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    private static final Pattern[] NEGATIVE = new Pattern[] {
            Pattern.compile("\\b(не\\s+нужно|не\\s+надо|не\\s+хочу|передумал|отмен(а|ить)|не\\s+актуально)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(просто\\s+спросил|просто\\s+интересуюсь|на\\s+будущее|пока\\s+что\\s+нет)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("\\b(спам|реклама)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    private static final Pattern[] GREETINGS_ONLY = new Pattern[] {
            Pattern.compile("^\\s*(здравствуйте|привет|добрый\\s+(день|вечер|утро)|хай|hello|hi)\\s*[!.]*\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    /**
     * Определяет: есть ли смысл "двигать" человека как потенциального заказчика молда.
     * Логика: скоринг по сигналам намерения + отсечка по отрицаниям/пустым приветствиям.
     */
    public static boolean isNeedToMove(String text) {
        if (text == null) return false;

        String s = normalize(text);
        if (s.isBlank()) return false;

        // если это только приветствие — не считаем намерением
        for (Pattern p : GREETINGS_ONLY) {
            if (p.matcher(s).find()) return false;
        }

        int score = 0;

        // явные отрицания режут сильно
        for (Pattern p : NEGATIVE) {
            if (p.matcher(s).find()) score -= 6;
        }

        // сильные сигналы
        for (Pattern p : STRONG_INTENT) {
            if (p.matcher(s).find()) score += 3;
        }

        // слабые сигналы
        for (Pattern p : WEAK_INTENT) {
            if (p.matcher(s).find()) score += 1;
        }

        // контактные данные/ссылки на модель — часто признак реального заказа
        if (HAS_PHONE.matcher(s).find()) score += 3;
        if (HAS_EMAIL.matcher(s).find()) score += 2;
        if (HAS_LINK.matcher(s).find()) score += 2;

        // быстрые доп. признаки: цифры (размеры/кол-во/цена) и единицы измерения
        if (containsOrderNumbers(s)) score += 1;

        // финальное решение
        // 3+ обычно означает: спрашивают цену/срок/доставку + упоминают молд/форму, или есть контакт/файл
        return score >= 3;
    }

    private static String normalize(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);

        // убираем диакритику/странные символы
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // унифицируем ё -> е
        s = s.replace('ё', 'е');

        // схлопываем пробелы
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private static boolean containsOrderNumbers(String s) {
        // числа + типичные единицы: мм/см/м, шт, кг, л
        return Pattern.compile("(\\d{1,5})(\\s*)(мм|cm|см|м\\b|шт\\b|pcs\\b|kg|кг\\b|л\\b)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(s).find()
                || Pattern.compile("\\b(\\d{2,5})\\b").matcher(s).find(); // просто цифры тоже бывают размерами/ценой
    }
}
