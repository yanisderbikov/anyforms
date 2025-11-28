package ru.anyforms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO {
    private Boolean success;
    private String error;
    private Long leadId;
    private String tracker;
    private Integer itemsCount;
}

