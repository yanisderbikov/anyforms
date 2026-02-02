package ru.anyforms.model.marketplace;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

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
    @Column(nullable = false)
    @NonNull
    private String s3PhotosFolderPath;
    @Column(nullable = false)
    @NonNull
    private String price;
    private String crossedPrice;
    private String discountPercent;
    @Column(nullable = false)
    @NonNull
    private String tgLink;
    private Integer orderNumber;
}
