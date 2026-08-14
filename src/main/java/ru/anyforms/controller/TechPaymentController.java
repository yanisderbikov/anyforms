package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.RefundOrderResult;
import ru.anyforms.dto.payment.RefundOrdersRequest;
import ru.anyforms.service.payment.PaymentRefundService;

import java.util.List;

/** Доступ закрыт ролью SERVICE (см. WebSecurityConfig). */
@RestController
@RequestMapping("/api/tech/payment")
@RequiredArgsConstructor
@Tag(name = "TechPayment", description = "Технические операции с платежами (межсервисный токен)")
public class TechPaymentController {

    private final PaymentRefundService paymentRefundService;

    @Operation(summary = "Массовый возврат по заказам",
            description = "Делает полный возврат через Т-Кассу по каждому заказу из списка")
    @PostMapping("/refund")
    public List<RefundOrderResult> refund(@Valid @RequestBody RefundOrdersRequest request) {
        return paymentRefundService.refundOrders(request.getOrderIds());
    }
}
