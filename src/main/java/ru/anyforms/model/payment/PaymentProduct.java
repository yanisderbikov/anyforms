package ru.anyforms.model.payment;

/**
 * Каталог продаваемых продуктов. Цена хранится в копейках, чтобы не плодить ошибки округления.
 * Новые продукты и комбо («что-то с чем-то») добавляются новыми значениями enum.
 *
 * <p>{@code vatCode} — код ставки НДС для чека Юкассы (1 — без НДС).</p>
 */
public enum PaymentProduct {

    GUIDE(
            "GUIDE",
            "Гайд",
            "Гайд anyforms",
            99_000L,
            1
    ),
    COURSE(
            "COURSE",
            "Курс",
            "Курс anyforms",
            990_000L,
            1
    );

    private final String code;
    private final String title;
    private final String description;
    private final long priceKopecks;
    private final int vatCode;

    PaymentProduct(String code, String title, String description, long priceKopecks, int vatCode) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.priceKopecks = priceKopecks;
        this.vatCode = vatCode;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceKopecks() {
        return priceKopecks;
    }

    public int getVatCode() {
        return vatCode;
    }

    public static PaymentProduct fromCode(String code) {
        for (PaymentProduct product : values()) {
            if (product.code.equalsIgnoreCase(code)) {
                return product;
            }
        }
        throw new IllegalArgumentException("Неизвестный продукт: " + code);
    }
}
