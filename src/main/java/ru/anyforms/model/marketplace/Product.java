package ru.anyforms.model.marketplace;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Product {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(nullable = false)
    @NonNull
    private String name;
    @NonNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    private String s3PhotosFolderPath;
    @Column(name = "photo_order", columnDefinition = "TEXT")
    private String photoOrder;
    @Column(nullable = false)
    @NonNull
    private String price;
    private String crossedPrice;
    private String discountPercent;
    private String tgLink;
    private Integer orderNumber;
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;
    @Builder.Default
    @Column(nullable = false)
    private Boolean preorder = Boolean.FALSE;
    /** ID элемента каталога товаров в AmoCRM (для привязки товара к сделке после оплаты). */
    @Column(name = "amo_product_id")
    private Long amoProductId;
    /** Имя товара в каталоге АМО (чтобы позиции заказа назывались как в АМО). */
    @Column(name = "amo_product_name")
    private String amoProductName;
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "product_shop",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "shop_id"))
    private Set<Shop> shops = new HashSet<>();
    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orderNumber ASC")
    private Set<ProductVariant> variants = new LinkedHashSet<>();
}
