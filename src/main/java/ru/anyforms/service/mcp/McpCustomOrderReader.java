package ru.anyforms.service.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.CustomProductFileDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.dto.mcp.McpCustomOrderDTO;
import ru.anyforms.model.Order;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.GetterOrderDTOByType;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.List;

@Component
@RequiredArgsConstructor
public class McpCustomOrderReader {

    private final OrderRepository orderRepository;
    private final CustomProductItemRepository customProductItemRepository;
    private final ConverterOrder converterOrder;
    private final CustomProductItemService itemService;
    private final GetterOrderDTOByType getterOrderDTOByType;

    public List<OrderSummaryDTO> workableOrders() {
        return orderRepository.findWorkableCustomOrders().stream()
                .filter(o -> o.getTracker() == null || o.getTracker().isBlank())
                .map(this::convertWithCount)
                .toList();
    }

    public McpCustomOrderDTO order(long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + orderId));
        return new McpCustomOrderDTO(convertWithCount(order), itemService.getAllByOrderId(orderId));
    }

    public List<CustomProductItemDTO> withoutFileLinks(List<CustomProductItemDTO> items) {
        items.forEach(this::dropFileLinks);
        return items;
    }

    public List<ShipGroupDTO> groupsWithoutFileLinks(List<ShipGroupDTO> groups) {
        groupsWithoutPhones(groups).forEach(group -> withoutFileLinks(group.getItems()));
        return groups;
    }

    public List<ShipGroupDTO> groupsWithoutPhones(List<ShipGroupDTO> groups) {
        groups.forEach(group -> group.getOrder().setContactPhone(null));
        return groups;
    }

    public List<OrderSummaryDTO> retailOrdersWithoutTracker() {
        List<OrderSummaryDTO> orders = getterOrderDTOByType.getOrdersWithoutTrackerDTOs();
        orders.forEach(order -> order.setContactPhone(null));
        return orders;
    }

    private void dropFileLinks(CustomProductItemDTO item) {
        if (item.getFiles() != null) {
            item.setFiles(item.getFiles().stream()
                    .map(file -> new CustomProductFileDTO(file.id(), null, file.filename()))
                    .toList());
        }
    }

    private OrderSummaryDTO convertWithCount(Order order) {
        OrderSummaryDTO dto = converterOrder.convert(order);
        dto.setCustomItemsCount(customProductItemRepository.countByOrderId(order.getId()));
        dto.setContactPhone(null);
        return dto;
    }
}
