package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.calculator.FormModifier;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.MasterType;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Вариант исполнения формы: тип формы и технические параметры. Пустое поле — калькулятор подставит оценку")
public class CalculationVariantRequest {

    @NotNull(message = "Выберите тип формы")
    private FormType formType;

    @Schema(description = "Из чего мастер-модель; по умолчанию SLA")
    private MasterType masterType;

    @Min(value = 2, message = "В комплекте минимум 2 формы")
    @Max(value = 20, message = "В комплекте не больше 20 форм")
    @Schema(description = "Сколько форм в комплекте (тип SET)")
    private Integer setFormsCount;

    @Min(value = 1, message = "Ячеек не меньше одной")
    @Max(value = 500, message = "Ячеек не больше 500")
    @Schema(description = "Число ячеек плоской матрицы")
    private Integer cellsCount;

    @Size(max = 2, message = "Не больше двух модификаторов")
    private List<FormModifier> modifiers;

    @Schema(description = "Нужен ли кожух; пусто — по правилу типа формы")
    private Boolean shellRequired;

    @Schema(description = "Узкое/круглое сечение или форма стоит на вершине — кожуху нужно основание")
    private Boolean shellUnstable;

    @PositiveOrZero(message = "Цена художника за модель не может быть отрицательной")
    private Double modelContractorPrice;

    @PositiveOrZero(message = "Площадь мастер-модели не может быть отрицательной")
    private Double slaAreaCm2;

    @PositiveOrZero(message = "Объём мастер-модели не может быть отрицательным")
    private Double slaVolumeCm3;

    @PositiveOrZero(message = "Расход смолы не может быть отрицательным")
    private Double slaMlManual;

    @PositiveOrZero(message = "Время печати SLA не может быть отрицательным")
    private Double slaHours;

    @DecimalMin(value = "1", message = "Коэффициент фактуры — от 1")
    @DecimalMax(value = "3", message = "Коэффициент фактуры — не больше 3")
    @Schema(description = "Крупная фактура (рис, вязка, мех): площадь × 1,5–2")
    private Double textureFactor;

    @PositiveOrZero(message = "Часы проектирования не могут быть отрицательными")
    private Double fdmProjectHours;

    @PositiveOrZero(message = "Вес литьевого комплекта не может быть отрицательным")
    private Double kitGrams;

    @PositiveOrZero(message = "Время печати комплекта не может быть отрицательным")
    private Double kitHours;

    @PositiveOrZero(message = "Часы обработки не могут быть отрицательными")
    private Double processingHours;

    @PositiveOrZero(message = "Часы ЧПУ не могут быть отрицательными")
    private Double cncHours;

    @PositiveOrZero(message = "Подготовка не может быть отрицательной")
    private Double prepRub;

    @PositiveOrZero(message = "Вес силикона не может быть отрицательным")
    private Double siliconeGrams;

    @PositiveOrZero(message = "Вес рабочей оснастки не может быть отрицательным")
    private Double shellGrams;

    @Schema(description = "Нужна ли промежуточная оловянная форма; пусто — да для платины с SLA-мастера")
    private Boolean needsIntermediate;

    @PositiveOrZero(message = "Вес промежуточной формы не может быть отрицательным")
    private Double tinGrams;

    @Min(value = 1, message = "Промежуточных форм не меньше одной")
    @Max(value = 20, message = "Промежуточных форм не больше 20")
    private Integer tinFormsCount;

    @PositiveOrZero(message = "Вес копии не может быть отрицательным")
    private Double copyGrams;

    private Boolean hasCut;

    @PositiveOrZero(message = "Доплата к форме не может быть отрицательной")
    private Double extraPerFormRub;

    @DecimalMin(value = "0", message = "Запас к весу — от 0")
    @DecimalMax(value = "1", message = "Запас к весу — не больше 1 (100%)")
    private Double weightReserve;

    @Schema(description = "Поля, заполненные AI-подсказкой и ещё не подтверждённые человеком")
    @Size(max = 40, message = "Слишком много AI-полей")
    private List<String> aiFields;

    @PositiveOrZero(message = "Цена модели не может быть отрицательной")
    @Schema(description = "Исключение: итоговая цена модели, задаёт основатель")
    private Double modelPriceOverride;

    @Min(value = 1, message = "Комплектов не меньше одного")
    @Max(value = 50, message = "Комплектов не больше 50")
    @Schema(description = "Исключение: число производственных комплектов при любом тираже")
    private Integer kitsOverride;

    @PositiveOrZero(message = "Цена формы не может быть отрицательной")
    @Schema(description = "Исключение: цена одной формы вручную")
    private Double formPriceOverride;

    public boolean hasExceptions() {
        return modelPriceOverride != null || kitsOverride != null || formPriceOverride != null;
    }
}
