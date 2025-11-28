package ru.anyforms.dto;

import lombok.Data;

@Data
public class SetTrackerRequestDTO {
    private Long leadId;
    private String tracker;
}

