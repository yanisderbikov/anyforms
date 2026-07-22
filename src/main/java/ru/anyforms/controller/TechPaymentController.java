package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.RefundOrderResult;
import ru.anyforms.dto.payment.RefundOrdersRequest;
import ru.anyforms.service.payment.PaymentRefundService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tech/payment")
@RequiredArgsConstructor
@Tag(name = "TechPayment", description = "Технические операции с платежами (авторизация по X-Auth-Token)")
public class TechPaymentController {

    private final PaymentRefundService paymentRefundService;

    @Value("${service.auth.token}")
    private String serviceToken;

    @Operation(summary = "Массовый возврат по заказам",
            description = "Делает полный возврат через Т-Кассу по каждому заказу из списка")
    @PostMapping("/refund")
    public List<RefundOrderResult> refund(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                          @Valid @RequestBody RefundOrdersRequest request) {
        checkToken(token);
        return paymentRefundService.refundOrders(request.getOrderIds());
    }

    private void checkToken(String token) {
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("service.auth.token не настроен — запрос возврата отклонён");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "service token is not configured");
        }
        if (!serviceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid auth token");
        }
    }
}
