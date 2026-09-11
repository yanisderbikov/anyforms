package ru.anyforms.dto.cdek;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdekPvzDTO {
    private String code;
    private String name;
    private String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private String address;
    private String fullAddress;
    private String workTime;
    private Double longitude;
    private Double latitude;
}
