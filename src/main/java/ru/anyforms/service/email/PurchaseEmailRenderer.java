package ru.anyforms.service.email;

public interface PurchaseEmailRenderer {

    record RenderedEmail(String subject, String html) {
    }

    RenderedEmail render(String productCode);
}
