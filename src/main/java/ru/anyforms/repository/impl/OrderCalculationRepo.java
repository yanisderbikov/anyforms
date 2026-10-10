package ru.anyforms.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.model.calculator.OrderCalculation;

import java.util.List;

@Repository
interface OrderCalculationRepo extends JpaRepository<OrderCalculation, Long> {

    String LIST_ITEM = "select new ru.anyforms.dto.calculator.OrderCalculationListItemDTO("
            + "c.id, c.createdAt, c.createdByName, c.createdByEmail, c.client, c.title, c.totalRub, c.margin, "
            + "c.preliminary, c.hasEstimates, c.hasExceptions, c.belowMinMargin, c.comment, c.ratesId) "
            + "from OrderCalculation c ";

    @Query(LIST_ITEM + "order by c.createdAt desc")
    List<OrderCalculationListItemDTO> findRecent(Pageable pageable);

    @Query(LIST_ITEM
            + "where lower(coalesce(c.client, '')) like :pattern escape '\\' "
            + "or lower(coalesce(c.title, '')) like :pattern escape '\\' "
            + "or lower(coalesce(c.createdByName, '')) like :pattern escape '\\' "
            + "order by c.createdAt desc")
    List<OrderCalculationListItemDTO> search(@Param("pattern") String pattern, Pageable pageable);
}
