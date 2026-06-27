package ru.anyforms.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
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

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CustomProductStatus status = CustomProductStatus.MODELING;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<CustomProductFile> files = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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
