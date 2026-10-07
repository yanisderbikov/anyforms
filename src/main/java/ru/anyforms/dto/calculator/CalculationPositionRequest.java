package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.calculator.PourMaterial;
import ru.anyforms.model.calculator.SiliconeType;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Позиция заказа — одно изделие с вариантами формы, силикона и тиража")
public class CalculationPositionRequest {

    @Size(max = 255, message = "Слишком длинное название изделия")
    private String productName;

    @PositiveOrZero(message = "Ширина не может быть отрицательной")
    @Max(value = 5000, message = "Ширина больше 5 м — проверьте единицы (мм)")
    private Double widthMm;

    @PositiveOrZero(message = "Глубина не может быть отрицательной")
    @Max(value = 5000, message = "Глубина больше 5 м — проверьте единицы (мм)")
    private Double depthMm;

    @PositiveOrZero(message = "Высота не может быть отрицательной")
    @Max(value = 5000, message = "Высота больше 5 м — проверьте единицы (мм)")
    private Double heightMm;

    private PourMaterial pourMaterial;

    @Schema(description = "Готовая 3D-модель клиента — моделирование 0")
    private Boolean hasClientModel;

    @Schema(description = "Цифровой продукт: только модель и проект, без печати и форм")
    private Boolean digitalOnly;

    @Schema(description = "Модель учтена в другой позиции (одна модель на серию)")
    private Boolean sharedModel;

    @Schema(description = "SLA-печать учтена в другой позиции (одна печать на стол)")
    private Boolean sharedSlaPrint;

    @Schema(description = "Исключение: бонусная позиция с ценой 0, затраты идут в рентабельность")
    private Boolean bonus;

    @Size(max = 6, message = "Не больше шести вариантов тиража")
    private List<@NotNull(message = "Пустой тираж") @Min(value = 1, message = "Тираж — от 1 формы")
            @Max(value = 100_000, message = "Тираж не больше 100 000") Integer> tirages;

    @Size(max = 2, message = "Силикон — платина и/или олово")
    private List<@NotNull(message = "Пустой тип силикона") SiliconeType> silicones;

    @NotEmpty(message = "У позиции должен быть хотя бы один вариант формы")
    @Size(max = 4, message = "Не больше четырёх вариантов формы")
    @Valid
    private List<@NotNull(message = "Пустой вариант формы") CalculationVariantRequest> variants;

    @Min(value = 0, message = "Некорректный выбранный вариант")
    private Integer selectedVariant;

    private SiliconeType selectedSilicone;

    @Min(value = 1, message = "Некорректный выбранный тираж")
    private Integer selectedTirage;

    @Size(max = 20, message = "Не больше 20 референсов на позицию")
    @Valid
    private List<@NotNull(message = "Пустой референс") CalculationReferenceDTO> references;

    @Size(max = 2000, message = "Комментарий к исключению — до 2000 символов")
    @Schema(description = "Обязательный комментарий «исключение проекта», если заданы исключения")
    private String exceptionComment;

    public boolean hasExceptions() {
        return Boolean.TRUE.equals(bonus)
                || (variants != null && variants.stream().anyMatch(v -> v != null && v.hasExceptions()));
    }
}
