package ru.anyforms.dto.amo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SalesbotRunTaskPayload {
    private Long leadId;
    private Long botId;
}
