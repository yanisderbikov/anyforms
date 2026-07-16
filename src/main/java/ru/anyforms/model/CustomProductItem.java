package ru.anyforms.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Кастомная позиция под-заказа внутри сделки ({@link Order}).
 * В отличие от {@link OrderItem} (зеркало товаров amoCRM, перетирается при синке),
 * это самостоятельная производственная позиция: amoCRM её не трогает.
 * Связь с {@link Order} по нашему id (lead_id в amo может меняться).
 */
@Entity
@Table(name = "custom_product_items")
@Data
@EqualsAndHashCode(exclude = {"order", "files"})
public class CustomProductItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "modeler")
    private String modeler;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CustomProductStatus status = CustomProductStatus.MODELING;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<CustomProductFile> files = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void setStatus(CustomProductStatus status) {
        if (this.status != status) {
            this.statusUpdatedAt = Instant.now();
        }
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (statusUpdatedAt == null) {
            statusUpdatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addFile(CustomProductFile file) {
        files.add(file);
        file.setItem(this);
    }

    public void removeFile(CustomProductFile file) {
        files.remove(file);
        file.setItem(null);
    }
}
