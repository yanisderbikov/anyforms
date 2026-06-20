package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseRequest {

    /** Код продукта из {@link ru.anyforms.model.payment.PaymentProduct}, например {@code GUIDE}. */
    @NotBlank
    @JsonProperty("productCode")
    private String productCode;

    @NotBlank
    @Email
    @JsonProperty("email")
    private String email;

    /** ФИО покупателя (используется в чеке). */
    @JsonProperty("fullName")
    private String fullName;

    /** Телефон покупателя. */
    @JsonProperty("phone")
    private String phone;

    /** Согласие на маркетинговую рассылку (необязательно). */
    @JsonProperty("marketingConsent")
    private Boolean marketingConsent;

    /**
     * Необязательный источник домена для страницы успеха: берётся только его {@code scheme://host}
     * (если хост в белом списке {@code payment.allowed-return-hosts}). Если не передан — домен
     * берётся из заголовка {@code Origin}, иначе из {@code payment.default-domain}. Путь страницы
     * успеха задаётся per-product в {@code payment_product.success_url_path}.
     */
    @JsonProperty("returnUrl")
    private String returnUrl;
}
