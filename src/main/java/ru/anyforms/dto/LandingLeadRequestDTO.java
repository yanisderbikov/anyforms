package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Запрос на создание заявки из лендинга")
public class LandingLeadRequestDTO {
    @Schema(description = "Название сделки", example = "заявка с лендинга")
    private String leadName;

    @Schema(description = "Имя клиента", example = "Иван")
    private String name;

    @NotBlank
    @Schema(description = "Телефон клиента", required = true, example = "+79991234567")
    private String phone;

    @Schema(description = "ID воронки amoCRM; если не задан — воронка лендинга по умолчанию", example = "9012345")
    private Long pipelineId;

    @Schema(description = "ID статуса amoCRM; если не задан — статус лендинга по умолчанию", example = "71234567")
    private Long statusId;

    @Schema(description = "UTM-метка source", example = "yandex")
    private String utmSource;

    @Schema(description = "UTM-метка medium", example = "cpc")
    private String utmMedium;

    @Schema(description = "UTM-метка campaign", example = "print3d_spb")
    private String utmCampaign;

    @Schema(description = "UTM-метка term", example = "3d печать корпусов")
    private String utmTerm;

    @Schema(description = "UTM-метка content", example = "banner_1")
    private String utmContent;

    @Schema(description = "Referrer страницы, с которой пришёл посетитель", example = "https://yandex.ru/search/")
    private String utmReferrer;
}
