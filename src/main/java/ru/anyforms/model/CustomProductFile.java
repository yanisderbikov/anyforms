package ru.anyforms.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Файл кастомной позиции (фото/чертёж/архив — тип не важен).
 * Своя строка со своим id, чтобы добавлять/удалять по одному.
 */
@Entity
@Table(name = "custom_product_files")
@Data
@EqualsAndHashCode(exclude = "item")
public class CustomProductFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private CustomProductItem item;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    /** Оригинальное имя файла (для скачивания/показа). */
    @Column(name = "filename")
    private String filename;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
