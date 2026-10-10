package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.Order;
import ru.anyforms.repository.CustomProductFileRepository;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.DeliveryNotifier;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomProductItemServiceImplTest {

    private final CustomProductItemRepository itemRepository = mock(CustomProductItemRepository.class);
    private final CustomProductItemServiceImpl service = new CustomProductItemServiceImpl(itemRepository,
            mock(CustomProductFileRepository.class), mock(OrderRepository.class), mock(S3FileStorage.class),
            mock(ConverterOrder.class), mock(ApplicationEventPublisher.class), mock(DeliveryNotifier.class));

    private CustomProductItem item(boolean nda) {
        Order order = new Order();
        order.setId(7L);
        order.setLeadId(777L);
        order.setContactName("Иван");
        CustomProductItem item = new CustomProductItem();
        item.setId(3L);
        item.setPublicId("AB12CD");
        item.setProductName("Корпус");
        item.setNda(nda);
        item.setStorageCell("A-1");
        item.setOrder(order);
        item.setFiles(new ArrayList<>());
        when(itemRepository.findByPublicId("AB12CD")).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void publicViewHidesInternalFields() {
        item(false);

        CustomProductItemDTO dto = service.getPublicByPublicId(" ab12cd ");

        assertEquals("Корпус", dto.getProductName());
        assertNull(dto.getId());
        assertNull(dto.getOrderId());
        assertNull(dto.getLeadId());
        assertNull(dto.getClientName());
        assertNull(dto.getStorageCell());
    }

    @Test
    void ndaItemIsNotFoundForThePublic() {
        item(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getPublicByPublicId("AB12CD"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Позиция не найдена: AB12CD", e.getReason());
        assertEquals("Корпус", service.getByPublicId("AB12CD").getProductName());
    }
}
