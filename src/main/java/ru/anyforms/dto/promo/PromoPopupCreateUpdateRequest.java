package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.promo.PromoPopupType;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание/обновление попапа с промокодом")
public class PromoPopupCreateUpdateRequest {

    @NotBlank(message = "Название обязательно")
    @Size(max = 128, message = "Название не длиннее 128 символов")
    private String name;

    @NotNull(message = "Выберите тип попапа")
    private PromoPopupType popupType;

    @NotNull(message = "Поле active обязательно")
    private Boolean active;

    @Min(value = -1000, message = "Приоритет от -1000 до 1000")
    @Max(value = 1000, message = "Приоритет от -1000 до 1000")
    private Integer priority;

    @Size(max = 64, message = "Slug магазина не длиннее 64 символов")
    private String shopSlug;

    @NotBlank(message = "Заголовок обязателен")
    @Size(max = 200, message = "Заголовок не длиннее 200 символов")
    private String title;

    @Size(max = 2000, message = "Описание не длиннее 2000 символов")
    private String description;

    @NotBlank(message = "Текст кнопки обязателен")
    @Size(max = 64, message = "Текст кнопки не длиннее 64 символов")
    private String buttonText;

    @Size(max = 200, message = "Заголовок экрана с кодом не длиннее 200 символов")
    private String successTitle;

    @Size(max = 2000, message = "Текст экрана успеха не длиннее 2000 символов")
    private String successText;

    @NotNull(message = "Задержка обязательна")
    @Min(value = 0, message = "Задержка от 0 до 3600 секунд")
    @Max(value = 3600, message = "Задержка от 0 до 3600 секунд")
    private Integer delaySeconds;

    @NotNull(message = "Укажите, через сколько часов показывать снова")
    @Min(value = 0, message = "Повтор показа — от 0 до 8760 часов")
    @Max(value = 8760, message = "Повтор показа — от 0 до 8760 часов")
    private Integer repeatAfterHours;

    @Schema(description = "Для AFTER_PURCHASE — сколько кодов выдать одному клиенту")
    @Min(value = 1, message = "Число показов — от 1 до 1000")
    @Max(value = 1000, message = "Число показов — от 1 до 1000")
    private Integer maxShows;

    private UUID promoCodeId;

    private Boolean hideForKnownContacts;

    private String validFrom;

    private String validUntil;

    @Min(value = 0, message = "Процент не может быть меньше 0")
    @Max(value = 100, message = "Процент не может быть больше 100")
    private Integer discountPercent;

    @Positive(message = "Фиксированная скидка должна быть больше нуля")
    private Long discountAmountKopecks;

    @Positive(message = "Минимальная сумма должна быть больше нуля")
    private Long minOrderKopecks;

    @Pattern(regexp = "^[A-Za-z0-9]{2,10}$", message = "Префикс кода — 2–10 латинских букв или цифр")
    private String codePrefix;

    @Min(value = 1, message = "Срок действия кода от 1 до 365 дней")
    @Max(value = 365, message = "Срок действия кода от 1 до 365 дней")
    private Integer codeTtlDays;

    private Boolean firstOrderOnly;

    private Long amoResponsibleUserId;

    private Long amoTaskTypeId;

    @Min(value = 1, message = "Срок задачи — минимум 1 минута")
    @Max(value = 43200, message = "Срок задачи — максимум 30 дней")
    private Integer amoTaskDeadlineMinutes;
}
