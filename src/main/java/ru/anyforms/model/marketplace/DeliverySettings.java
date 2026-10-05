package ru.anyforms.model.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "delivery_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DeliverySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(nullable = false, updatable = false)
    private Short id;

    @Column(name = "free_delivery_enabled", nullable = false)
    private Boolean freeDeliveryEnabled;

    @Column(name = "free_delivery_threshold_kopecks", nullable = false)
    private Long freeDeliveryThresholdKopecks;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean qualifiesForFreeDelivery(long payableKopecks) {
        return Boolean.TRUE.equals(freeDeliveryEnabled)
                && freeDeliveryThresholdKopecks != null
                && payableKopecks >= freeDeliveryThresholdKopecks;
    }
}
