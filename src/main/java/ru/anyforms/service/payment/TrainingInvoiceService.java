package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.TrainingInvoiceCreateRequest;
import ru.anyforms.dto.payment.TrainingInvoiceDTO;

import java.util.List;

public interface TrainingInvoiceService {

    TrainingInvoiceDTO create(TrainingInvoiceCreateRequest request);

    List<TrainingInvoiceDTO> recent(int limit);
}
