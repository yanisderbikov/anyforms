package ru.anyforms.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.util.MoneyUtil;

@Data
@AllArgsConstructor
public class PaymentProductDTO {
    private String code;
    private String title;
    private String price;

    public static PaymentProductDTO from(PaymentProduct product) {
        return new PaymentProductDTO(
                product.getCode(),
                product.getTitle(),
                MoneyUtil.kopecksToString(product.getPriceKopecks())
        );
    }
}
