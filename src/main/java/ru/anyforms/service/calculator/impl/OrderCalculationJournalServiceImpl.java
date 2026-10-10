package ru.anyforms.service.calculator.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.CalculationDiscountRequest;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.OrderCalculationDTO;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.model.calculator.OrderCalculation;
import ru.anyforms.repository.GetterOrderCalculation;
import ru.anyforms.repository.OrderCalculationDeleter;
import ru.anyforms.repository.SaverOrderCalculation;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.calculator.CalculatorReferenceService;
import ru.anyforms.service.calculator.OrderCalculationJournalService;
import ru.anyforms.service.calculator.OrderCalculatorService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
class OrderCalculationJournalServiceImpl implements OrderCalculationJournalService {

    private static final int MAX_LIST = 200;
    private static final int TITLE_LENGTH = 500;

    private final OrderCalculatorService orderCalculatorService;
    private final GetterOrderCalculation getterOrderCalculation;
    private final SaverOrderCalculation saverOrderCalculation;
    private final OrderCalculationDeleter orderCalculationDeleter;
    private final CalculatorReferenceService calculatorReferenceService;
    private final ObjectMapper objectMapper;

    @Override
    public OrderCalculationListItemDTO save(OrderCalculationRequest request, UserAccess user) {
        for (int i = 0; i < request.getPositions().size(); i++) {
            CalculationPositionRequest position = request.getPositions().get(i);
            if (position.hasExceptions() && isBlank(position.getExceptionComment())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Позиция " + (i + 1) + ": укажите комментарий «исключение проекта»");
            }
        }
        OrderCalculationResult result = orderCalculatorService.calculate(request, user);
        CalculationDiscountRequest discount = request.getDiscount();
        boolean founderDiscount = discount != null
                && ((discount.getDevelopmentPercent() != null && discount.getDevelopmentPercent() > 0)
                || (Boolean.TRUE.equals(discount.getAllowBelowMinMargin()) && result.summary().belowMinMargin()));
        if (founderDiscount && isBlank(discount.getComment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите комментарий к скидке по решению основателя");
        }

        OrderCalculation saved = saverOrderCalculation.save(OrderCalculation.builder()
                .client(trim(request.getClient()))
                .title(title(request))
                .totalRub(result.summary().totalOffer())
                .margin(result.summary().margin())
                .preliminary(result.preliminary())
                .hasEstimates(result.hasEstimates())
                .hasExceptions(result.hasExceptions())
                .belowMinMargin(result.summary().belowMinMargin())
                .comment(trim(request.getComment()))
                .request(write(request))
                .result(write(result))
                .ratesId(result.ratesVersionId())
                .createdByEmail(user.email())
                .createdByName(user.name())
                .createdAt(Instant.now())
                .build());
        log.info("Калькулятор: расчёт {} сохранён ({}, {} ₽)", saved.getId(), user.name(), saved.getTotalRub());
        return OrderCalculationListItemDTO.from(saved);
    }

    @Override
    public List<OrderCalculationListItemDTO> list(String query, int limit) {
        return getterOrderCalculation.getRecent(query, Math.min(Math.max(limit, 1), MAX_LIST));
    }

    @Override
    public OrderCalculationDTO get(Long id) {
        OrderCalculation calculation = find(id);
        JsonNode request = read(calculation.getRequest());
        List<String> keys = new ArrayList<>();
        request.path("positions").forEach(position -> position.path("references")
                .forEach(reference -> keys.add(reference.path("key").asText(null))));
        return new OrderCalculationDTO(
                OrderCalculationListItemDTO.from(calculation),
                request,
                read(calculation.getResult()),
                calculatorReferenceService.viewUrls(keys.stream().filter(Objects::nonNull).toList()));
    }

    @Override
    public void delete(Long id) {
        find(id);
        orderCalculationDeleter.delete(id);
        log.info("Калькулятор: расчёт {} удалён", id);
    }

    private OrderCalculation find(Long id) {
        return getterOrderCalculation.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Расчёт не найден"));
    }

    private static String title(OrderCalculationRequest request) {
        String title = String.join(", ", request.getPositions().stream()
                .map(CalculationPositionRequest::getProductName)
                .map(OrderCalculationJournalServiceImpl::trim)
                .filter(Objects::nonNull)
                .toList());
        if (title.isEmpty()) {
            return null;
        }
        return title.length() > TITLE_LENGTH ? title.substring(0, TITLE_LENGTH - 1) + "…" : title;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить расчёт", e);
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Сохранённый расчёт повреждён");
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
