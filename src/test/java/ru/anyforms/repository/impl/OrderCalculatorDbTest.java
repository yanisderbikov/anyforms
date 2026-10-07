package ru.anyforms.repository.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.model.calculator.CalculatorRatesVersion;
import ru.anyforms.model.calculator.OrderCalculation;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@EnabledIfSystemProperty(named = "calculatorDb", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${calculator.db.url:jdbc:postgresql://localhost:5474/anyforms_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
})
class OrderCalculatorDbTest {

    @Autowired
    private CalculatorRatesRepo calculatorRatesRepo;

    @Autowired
    private OrderCalculationRepo orderCalculationRepo;

    @Test
    void migrationSeedsCompleteRates() throws Exception {
        CalculatorRatesVersion seed = calculatorRatesRepo.findFirstByOrderByIdDesc().orElseThrow();

        CalculatorRates rates = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readValue(seed.getRates(), CalculatorRates.class);

        assertEquals(2.2, rates.getModelMarkup());
        assertEquals(5, rates.getMinFormPriceByTirage().size());
        assertEquals("Регламент ценообразования v3.5", seed.getCreatedByName());

        CalculatorRatesVersion next = calculatorRatesRepo.save(CalculatorRatesVersion.builder()
                .rates(seed.getRates())
                .createdAt(Instant.now())
                .createdByEmail("founder@anyforms.ru")
                .build());
        assertEquals(next.getId(), calculatorRatesRepo.findFirstByOrderByIdDesc().orElseThrow().getId());
        assertEquals(2, calculatorRatesRepo.findAllByOrderByIdDesc(PageRequest.of(0, 10)).size());
    }

    @Test
    void journalListUsesProjectionAndSearch() {
        Long ratesId = calculatorRatesRepo.findFirstByOrderByIdDesc().orElseThrow().getId();
        OrderCalculation saved = orderCalculationRepo.save(OrderCalculation.builder()
                .client("Анкор ЖБИ")
                .title("Свеча Онигири, 100%_скидка")
                .totalRub(28120.0)
                .margin(0.3878)
                .preliminary(false)
                .hasEstimates(true)
                .hasExceptions(false)
                .belowMinMargin(false)
                .request("{\"positions\":[]}")
                .result("{}")
                .ratesId(ratesId)
                .createdByEmail("manager@anyforms.ru")
                .createdByName("Менеджер")
                .createdAt(Instant.now())
                .build());

        List<OrderCalculationListItemDTO> recent = orderCalculationRepo.findRecent(PageRequest.of(0, 10));
        assertEquals(1, recent.size());
        assertEquals(saved.getId(), recent.get(0).id());
        assertEquals(28120.0, recent.get(0).totalRub());
        assertTrue(recent.get(0).hasEstimates());

        assertEquals(1, orderCalculationRepo.search("%анкор%", PageRequest.of(0, 10)).size());
        assertEquals(1, orderCalculationRepo.search("%100\\%\\_%", PageRequest.of(0, 10)).size());
        assertEquals(0, orderCalculationRepo.search("%10\\%0%", PageRequest.of(0, 10)).size());
        assertEquals(1, orderCalculationRepo.search("%менеджер%", PageRequest.of(0, 10)).size());
    }
}
