package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Amount {

    @JsonProperty("value")
    private String value;

    @JsonProperty("currency")
    private String currency;
}
