package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class AmoProduct {
    private Long id;
    private String name;
    private Long price;
    private Integer quantity;
    
    @SerializedName("catalog_id")
    private Long catalogId;
    
    @SerializedName("request_id")
    private Long requestId;
}


