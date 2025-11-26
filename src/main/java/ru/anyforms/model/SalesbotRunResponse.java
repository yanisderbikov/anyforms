package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Модель ответа от API запуска Salesbot в amoCRM
 */
@Data
public class SalesbotRunResponse {
    private Boolean success;
    
    @SerializedName("_links")
    private Links links;
    
    @Data
    public static class Links {
        private Self self;
    }
    
    @Data
    public static class Self {
        private String href;
        private String method;
    }
}

