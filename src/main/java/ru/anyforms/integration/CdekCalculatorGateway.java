package ru.anyforms.integration;

import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekTariffQuote;

import java.util.List;

public interface CdekCalculatorGateway {

    CdekTariffQuote calculate(int tariffCode, CdekLocation from, CdekLocation to, List<CdekPackage> packages);
}
