package ru.anyforms.dto.cdek;

import java.time.LocalDate;
import java.util.List;

public record CdekOrderInfo(LocalDate plannedDeliveryDate,
                            Integer tariffCode,
                            CdekLocation from,
                            CdekLocation to,
                            List<CdekPackage> packages) {

    public boolean canCalculate() {
        return tariffCode != null && from != null && to != null && packages != null && !packages.isEmpty();
    }
}
