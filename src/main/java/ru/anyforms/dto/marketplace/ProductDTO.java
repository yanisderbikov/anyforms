package ru.anyforms.dto.marketplace;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ProductDTO {
    private UUID id;
    private String name;
    private String description;
    private List<String> photos;
    private String price;
    private String crossedPrice;
    private String discountPercent;
    private String tgLink;
}
