package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.anyforms.dto.CustomProductFileDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.mcp.McpToolDescriptor;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpToolRegistry;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomProductItemService itemService = mock(CustomProductItemService.class);
    private final McpCustomOrderReader reader = new McpCustomOrderReader(mock(OrderRepository.class),
            mock(CustomProductItemRepository.class), mock(ConverterOrder.class), itemService);
    private final McpToolRegistry registry = new McpToolRegistry(List.of(
            new ListCustomOrdersTool(reader),
            new GetCustomOrderTool(reader),
            new ListCustomItemsTool(itemService, reader),
            new GetCustomItemTool(itemService),
            new GetItemLogsTool(mock(CustomProductLogService.class)),
            new ReadyToShipTool(itemService, reader),
            new InDeliveryTool(itemService, reader)));

    @Test
    void allToolsAreRegisteredReadOnlyWithObjectSchemas() {
        List<McpToolDescriptor> tools = registry.descriptors();

        assertEquals(List.of("get_custom_item", "get_custom_order", "get_item_logs", "list_custom_items",
                "list_custom_orders", "list_in_delivery", "list_ready_to_ship"),
                tools.stream().map(McpToolDescriptor::name).toList());
        tools.forEach(tool -> {
            assertEquals("object", tool.inputSchema().get("type"), tool.name());
            assertEquals(true, tool.annotations().get("readOnlyHint"), tool.name());
            assertFalse(tool.description().isBlank(), tool.name());
        });
    }

    @Test
    void listOfItemsDropsFileLinksAndFiltersByStatus() throws Exception {
        CustomProductItemDTO item = new CustomProductItemDTO();
        item.setFiles(new ArrayList<>(List.of(new CustomProductFileDTO(5L, "https://s3/presigned", "model.stl"))));
        when(itemService.getAllByStatus(CustomProductStatus.MODELING)).thenReturn(List.of(item));

        Object result = registry.find("list_custom_items").orElseThrow()
                .call(objectMapper.readTree("{\"status\":\"modeling\"}"));

        CustomProductFileDTO file = ((List<CustomProductItemDTO>) result).get(0).getFiles().get(0);
        assertNull(file.url());
        assertEquals("model.stl", file.filename());
    }

    @Test
    void getItemNeedsExactlyOneKey() throws Exception {
        var tool = registry.find("get_custom_item").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> tool.call(objectMapper.readTree("{}")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"id\":1,\"publicId\":\"K7Q2ZD\"}")));

        tool.call(objectMapper.readTree("{\"publicId\":\"k7q2zd\"}"));
        verify(itemService).getByPublicId("K7Q2ZD");
    }
}
