package ru.anyforms.service.product;

import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    List<ProductDTO> getAllProducts();

    List<ProductDTO> getActiveProducts();

    ProductDTO saveOrUpdate(ProductCreateUpdateRequestDTO request);

    ProductDTO uploadPhotos(UUID id, List<MultipartFile> files);
}
