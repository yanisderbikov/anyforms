package ru.anyforms.model.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Каталог продаваемых продуктов (источник истины по цене и пути страницы успеха).
 * Цена — в копейках. Новые продукты и комбо («что-то с чем-то») добавляются строками таблицы,
 * без правок кода.
 *
 * <p>{@code successUrlPath} — относительный путь страницы успеха (например {@code /guide/success}).
 * Домен подставляется динамически на этапе покупки, итоговый {@code return_url} = домен + путь.</p>
 * <p>{@code vatCode} — код ставки НДС для чека Юкассы (1 — без НДС).</p>
 */
@Entity
@Table(name = "payment_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentProduct {

    /** Известные коды продуктов, для которых в коде есть отдельная логика выдачи. */
    public static final String CODE_GUIDE = "GUIDE";
    public static final String CODE_COURSE = "COURSE";

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_kopecks", nullable = false)
    private Long priceKopecks;

    @Column(name = "vat_code", nullable = false)
    private Integer vatCode;

    /** Относительный путь страницы успеха, обязателен. Домен добавляется динамически. */
    @Column(name = "success_url_path", nullable = false)
    private String successUrlPath;

    @Column(nullable = false)
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
