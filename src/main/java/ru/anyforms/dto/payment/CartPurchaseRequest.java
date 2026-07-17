package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Оформление заказа маркетплейса: корзина + данные клиента + выбранный ПВЗ СДЭК. */
@Data
@Schema(description = "Оформление заказа маркетплейса")
public class CartPurchaseRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Позиции корзины")
    private List<CartItemDTO> items;

    @Schema(description = "ФИО клиента")
    private String fullName;

    @Schema(description = "Телефон в формате +7XXXXXXXXXX")
    private String phone;

    @NotNull
    @Email
    @Schema(description = "Email для чека и уведомлений")
    private String email;

    @Schema(description = "Код ПВЗ СДЭК")
    private String pvzCode;

    @Schema(description = "Город ПВЗ СДЭК")
    private String pvzCity;

    @Schema(description = "Адрес ПВЗ СДЭК (улица, дом)")
    private String pvzStreet;

    @Schema(description = "Согласие на рассылку")
    private Boolean marketingConsent;

    @Schema(description = "Промокод (опционально)")
    private String promoCode;

    @Schema(description = "URL страницы успеха (для возврата с Юкассы)")
    private String returnUrl;
}
