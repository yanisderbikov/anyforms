package ru.anyforms.dto.calculator.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.MasterType;
import ru.anyforms.model.calculator.PourMaterial;
import ru.anyforms.model.calculator.SiliconeType;

import java.util.List;

@Builder
@Schema(description = "Что знает калькулятор об изделии — контекст для AI-оценки технических параметров")
public record CalculationAiRequest(
        String productName,
        String description,
        Double widthMm,
        Double depthMm,
        Double heightMm,
        PourMaterial pourMaterial,
        FormType formType,
        MasterType masterType,
        SiliconeType silicone,
        Integer tirage,
        List<CalculationAiReference> references) {
}
