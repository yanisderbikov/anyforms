package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.InvoiceCreateRequest;
import ru.anyforms.dto.payment.InvoiceDTO;

import java.util.List;

public interface InvoiceService {

    InvoiceDTO create(InvoiceCreateRequest request);

    List<InvoiceDTO> recent(int limit);
}
