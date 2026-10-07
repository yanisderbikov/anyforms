package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.anyforms.model.calculator.SiliconeType;

@Builder(toBuilder = true)
@Schema(description = "Параметры варианта после подстановки оценок и значений по умолчанию — вход формулы цены")
public record PriceInput(
        SiliconeType silicone,
        int tirage,
        boolean hasClientModel,
        boolean digitalOnly,
        boolean sharedModel,
        boolean sharedSlaPrint,
        double modelContractorPrice,
        Double modelPriceOverride,
        double slaAreaCm2,
        double slaVolumeCm3,
        double slaMlManual,
        double slaHours,
        double textureFactor,
        double fdmProjectHours,
        double kitGrams,
        double kitHours,
        double processingHours,
        double cncHours,
        double prepRub,
        double siliconeGrams,
        double shellGrams,
        boolean needsIntermediate,
        double tinGrams,
        int tinFormsCount,
        double copyGrams,
        boolean hasCut,
        double extraPerFormRub,
        double weightReserve,
        Integer kitsOverride,
        Double formPriceOverride) {
}
