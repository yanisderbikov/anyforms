package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Справочники формы калькулятора и права текущего пользователя")
public record CalculatorOptionsDTO(
        List<FormTypeOption> formTypes,
        List<Option> pourMaterials,
        List<Option> silicones,
        List<Option> masterTypes,
        List<Option> modifiers,
        @Schema(description = "Подключён ли AI-провайдер для подсказок по референсам") boolean aiAvailable,
        String aiProvider,
        @Schema(description = "Текущий пользователь — основатель: правит ставки и задаёт исключения") boolean founder) {

    @Schema(description = "Элемент справочника")
    public record Option(String code, String label, String hint) {
    }

    @Schema(description = "Тип формы и правила, которые он задаёт по умолчанию")
    public record FormTypeOption(
            String code,
            String label,
            String hint,
            @Schema(description = "Разрез по умолчанию") boolean cut,
            @Schema(description = "Заливок на форму") int pours,
            @Schema(description = "Когда нужен кожух") String shellRule) {
    }
}
