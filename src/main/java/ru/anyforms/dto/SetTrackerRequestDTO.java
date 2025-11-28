package ru.anyforms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetTrackerRequestDTO {
    private Long leadId;
    private String tracker;
}

