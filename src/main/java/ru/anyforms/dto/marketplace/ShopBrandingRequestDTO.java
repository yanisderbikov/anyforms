package ru.anyforms.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public abstract class ShopBrandingRequestDTO {

    @Size(max = 255, message = "Заголовок витрины — не длиннее 255 символов")
    @Schema(description = "Заголовок витрины под шапкой; пусто — название магазина", example = "Магазин молдов Дианы di_gips")
    private String tagline;

    @Size(max = 2000, message = "Описание — не длиннее 2000 символов")
    @Schema(description = "Короткое описание магазина рядом с фото/видео витрины")
    private String description;

    @Size(max = 1024, message = "Ссылка на логотип слишком длинная")
    @Schema(description = "Логотип в шапке витрины (URL из S3); пусто — название текстом")
    private String logoUrl;

    @Size(max = 1024, message = "Ссылка на знак слишком длинная")
    @Schema(description = "Компактный знак бренда: на узких экранах заменяет логотип")
    private String logoMarkUrl;

    @Size(max = 1024, message = "Ссылка на фавикон слишком длинная")
    @Schema(description = "Иконка вкладки браузера на страницах витрины; пусто — иконка anyforms")
    private String faviconUrl;

    @Size(max = 1024, message = "Ссылка на фото слишком длинная")
    @Schema(description = "Фото-приветствие над каталогом")
    private String heroImageUrl;

    @Size(max = 255, message = "Подпись к фото — не длиннее 255 символов")
    @Schema(description = "Alt-текст фото-приветствия")
    private String heroImageAlt;

    @Size(max = 1024, message = "Ссылка на видео слишком длинная")
    @Schema(description = "Видео-приветствие над каталогом (webm или mp4); важнее фото")
    private String heroVideoUrl;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Цвет шапки — в формате #rrggbb")
    @Schema(description = "Цвет шапки витрины; пусто — тёмная шапка anyforms", example = "#572631")
    private String headerColor;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Акцентный цвет — в формате #rrggbb")
    @Schema(description = "Акцент: цены, кнопки, ссылки, заголовки", example = "#6e2f3e")
    private String accentColor;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Цвет фона — в формате #rrggbb")
    @Schema(description = "Фон страниц витрины", example = "#f4eee3")
    private String backgroundColor;
}
