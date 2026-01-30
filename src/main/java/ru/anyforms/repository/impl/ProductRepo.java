package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.marketplace.Product;

import java.util.List;
import java.util.UUID;

@Repository
interface ProductRepo extends JpaRepository<Product, UUID> {

    /**
     * Все продукты, отсортированные по orderNumber: сначала 1, 2, 3..., затем все с null.
     */
    @Query("SELECT p FROM Product p ORDER BY CASE WHEN p.orderNumber IS NULL THEN 1 ELSE 0 END, p.orderNumber ASC")
    List<Product> findAllOrderByOrderNumberAscNullsLast();
}
