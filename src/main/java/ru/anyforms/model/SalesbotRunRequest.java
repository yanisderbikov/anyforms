package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель запроса для запуска Salesbot в amoCRM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesbotRunRequest {
    @SerializedName("bot_id")
    private Long botId;
    
    @SerializedName("entity_id")
    private Long entityId;
    
    @SerializedName("entity_type")
    private Integer entityType;
}


