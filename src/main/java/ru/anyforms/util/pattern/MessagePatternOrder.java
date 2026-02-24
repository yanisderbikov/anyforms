package ru.anyforms.util.pattern;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class MessagePatternOrder {

    private static final int UF = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    // --- Блоки смыслов ---
    private static final Pattern DOMAIN_OBJECT = Pattern.compile(
            "\\b(молд|mold|форма|формы|матриц(а|ы)|пресс-форма|отливк(а|и)|лить(е|ё)|заливк(а|и))\\b", UF
    );

    private static final Pattern MATERIALS = Pattern.compile(
            "\\b(силикон|полиуретан|пластик|смола|эпоксид|резин(а|ы))\\b", UF
    );

    private static final Pattern FILES = Pattern.compile(
            "\\b(тз|по\\s+тз|чертеж|чертёж|3d|stl|step|iges|модель|рендер)\\b", UF
    );

    // Опечатки/формы "заказать"
    private static final Pattern ORDER_VERB = Pattern.compile(
            "\\b(заказать|закажу|закажите|закажем|заказат|заказть|заказа(ть|ти)|купить|беру|оформить|оформим|оформите|оформляем|сделать|сделаете|сделай(те)?|изготовить|изготовите|сможете\\s+изготовить)\\b",
            UF
    );

    // "нужен/надо" как явная потребность
    private static final Pattern NEED_WORDS = Pattern.compile(
            "\\b(нужен|нужна|нужны|нужно|надо|хочу)\\b", UF
    );

    private static final Pattern QUOTE_WORDS = Pattern.compile(
            "\\b(рассчита(й|йте)|посчита(й|йте)|калькуляц|сколько\\s+стоит|цена|прайс|стоимост|ценник)\\b", UF
    );

    private static final Pattern TIME_WORDS = Pattern.compile(
            "\\b(срок(и|ов)?|когда\\s+будет\\s+готово|дедлайн|изготовлен(ие|ия))\\b", UF
    );

    private static final Pattern LOGISTICS_WORDS = Pattern.compile(
            "\\b(доставк(а|и)|отправк(а|и)|сдэк|почта|курьер|самовывоз)\\b", UF
    );

    private static final Pattern ORDER_NOUN = Pattern.compile("\\bзаказ\\b", UF);

    // --- Отсечки ---
    private static final Pattern GREETINGS_ONLY = Pattern.compile(
            "^\\s*(здравствуйте|привет|добрый\\s+(день|вечер|утро)|хай|hello|hi)\\s*[!.]*\\s*$", UF
    );

    private static final Pattern[] NEGATIVE = new Pattern[] {
            Pattern.compile("\\b(не\\s+нужно|не\\s+надо|не\\s+хочу|передумал|отмен(а|ить)|не\\s+актуально)\\b", UF),
            Pattern.compile("\\b(просто\\s+спросил|просто\\s+интересуюсь|на\\s+будущее|пока\\s+что\\s+нет)\\b", UF),
            Pattern.compile("\\b(спам|реклама)\\b", UF),

            // ключевое для твоего кейса
            Pattern.compile("\\bбез\\s+заказа\\b", UF)
    };

    // “что такое/объясните/для чего” — справочный вопрос, не заказ
    private static final Pattern EDU_QUESTION = Pattern.compile(
            "\\b(что\\s+такое|объяснит(е|ь)|расскаж(и|ите)|для\\s+чего|зачем|какие\\s+бывают|в\\s+чем\\s+разниц)\\b", UF
    );

    // Контакты/ссылки (как было)
    private static final Pattern HAS_PHONE = Pattern.compile("(\\+?\\d[\\d\\s()\\-]{8,}\\d)");
    private static final Pattern HAS_EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_LINK  = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);

    public static boolean isNeedToMove(String text) {
        if (text == null) return false;

        String s = normalize(text);
        if (s.isBlank()) return false;

        if (GREETINGS_ONLY.matcher(s).find()) return false;

        for (Pattern n : NEGATIVE) {
            if (n.matcher(s).find()) return false; // жесткая отсечка
        }

        boolean hasDomain = DOMAIN_OBJECT.matcher(s).find() || MATERIALS.matcher(s).find() || FILES.matcher(s).find();
        boolean hasOrderVerb = ORDER_VERB.matcher(s).find();
        boolean hasNeed = NEED_WORDS.matcher(s).find();
        boolean hasQuote = QUOTE_WORDS.matcher(s).find();
        boolean hasTime = TIME_WORDS.matcher(s).find();
        boolean hasLogistics = LOGISTICS_WORDS.matcher(s).find();
        boolean hasOrderNoun = ORDER_NOUN.matcher(s).find();

        boolean hasContacts = HAS_PHONE.matcher(s).find() || HAS_EMAIL.matcher(s).find() || HAS_LINK.matcher(s).find();
        boolean hasNumbers = containsOrderNumbers(s);

        // Справочные вопросы не считаем лидом,
        // если нет явного заказа/потребности/расчета.
        if (EDU_QUESTION.matcher(s).find() && !(hasOrderVerb || hasNeed || hasQuote || hasOrderNoun)) {
            return false;
        }

        // 1) “Давайте оформим заказ” (может быть без слова молд)
        if ((hasOrderVerb && hasOrderNoun) || s.contains("давайте оформим заказ")) return true;

        // 2) Заказ + предметка/данные
        if (hasOrderVerb && (hasDomain || hasContacts || hasNumbers)) return true;

        // 3) Нужен/хочу + предметка
        if (hasNeed && hasDomain) return true;

        // 4) Предметка + (цена/сроки/доставка) — это уже лид
        if (hasDomain && (hasQuote || hasTime || hasLogistics)) return true;

        // 5) “Сколько стоит доставка” без предметки/заказа — не лид
        return false;
    }

    private static String normalize(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = s.replace('ё', 'е');
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private static boolean containsOrderNumbers(String s) {
        return Pattern.compile("(\\d{1,5})(\\s*)(мм|cm|см|м\\b|шт\\b|pcs\\b|kg|кг\\b|л\\b)", UF).matcher(s).find()
                || Pattern.compile("\\b(\\d{2,5})\\b", UF).matcher(s).find();
    }
}