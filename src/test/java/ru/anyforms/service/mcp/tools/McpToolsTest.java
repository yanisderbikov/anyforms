package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.anyforms.dto.CustomProductFileDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductLogDTO;
import ru.anyforms.dto.mcp.McpToolDescriptor;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpToolRegistry;
import ru.anyforms.util.converter.ConverterOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomProductItemService itemService = mock(CustomProductItemService.class);
    private final CustomProductLogService logService = mock(CustomProductLogService.class);
    private final McpCustomOrderReader reader = new McpCustomOrderReader(mock(OrderRepository.class),
            mock(CustomProductItemRepository.class), mock(ConverterOrder.class), itemService);
    private final McpToolRegistry registry = new McpToolRegistry(List.of(
            new ListCustomOrdersTool(reader),
            new GetCustomOrderTool(reader),
            new ListCustomItemsTool(itemService, reader),
            new GetCustomItemTool(itemService),
            new GetItemLogsTool(logService),
            new ReadyToShipTool(itemService, reader),
            new InDeliveryTool(itemService, reader),
            new SetItemStatusTool(itemService, reader),
            new AddItemLogTool(logService)));

    @Test
    void onlyStatusAndLogToolsWrite() {
        List<McpToolDescriptor> tools = registry.descriptors();

        assertEquals(List.of("add_item_log", "get_custom_item", "get_custom_order", "get_item_logs", "list_custom_items",
                "list_custom_orders", "list_in_delivery", "list_ready_to_ship", "set_item_status"),
                tools.stream().map(McpToolDescriptor::name).toList());
        Set<String> writers = Set.of("add_item_log", "set_item_status");
        tools.forEach(tool -> {
            assertEquals("object", tool.inputSchema().get("type"), tool.name());
            assertEquals(!writers.contains(tool.name()), tool.annotations().get("readOnlyHint"), tool.name());
            assertEquals(false, tool.annotations().get("destructiveHint"), tool.name());
            assertFalse(tool.description().isBlank(), tool.name());
        });
        assertEquals(false, registry.find("add_item_log").orElseThrow().descriptor().annotations().get("idempotentHint"));
    }

    private CustomProductItemDTO itemIn(CustomProductStatus status) {
        CustomProductItemDTO item = new CustomProductItemDTO();
        item.setStatus(status);
        item.setFiles(new ArrayList<>());
        return item;
    }

    @Test
    void statusChangesOnlyForItemsInWork() throws Exception {
        var tool = registry.find("set_item_status").orElseThrow();
        when(itemService.getById(1L)).thenReturn(itemIn(CustomProductStatus.MODELING));
        when(itemService.updateStatus(1L, CustomProductStatus.READY_TO_SHIP)).thenReturn(itemIn(CustomProductStatus.READY_TO_SHIP));

        CustomProductItemDTO updated = (CustomProductItemDTO) tool.call(
                objectMapper.readTree("{\"itemId\":1,\"status\":\"ready_to_ship\"}"));
        assertEquals(CustomProductStatus.READY_TO_SHIP, updated.getStatus());

        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"itemId\":1,\"status\":\"COMPLETED\"}")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"itemId\":1,\"status\":\"DELIVERING\"}")));

        when(itemService.getById(2L)).thenReturn(itemIn(CustomProductStatus.DELIVERING));
        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"itemId\":2,\"status\":\"MODELING\"}")));

        verify(itemService, times(1)).updateStatus(anyLong(), any());
    }

    @Test
    void logEntryIsAddedWithTrimmedTextAndLimits() throws Exception {
        var tool = registry.find("add_item_log").orElseThrow();
        CustomProductLogDTO entry = new CustomProductLogDTO(10L, "Отлили 20 шт", Instant.now());
        when(logService.add(3L, "Отлили 20 шт")).thenReturn(entry);

        assertSame(entry, tool.call(objectMapper.readTree("{\"itemId\":3,\"text\":\"  Отлили 20 шт \"}")));

        assertThrows(IllegalArgumentException.class, () -> tool.call(objectMapper.readTree("{\"itemId\":3,\"text\":\"  \"}")));
        String tooLong = "a".repeat(AddItemLogTool.MAX_TEXT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"itemId\":3,\"text\":\"" + tooLong + "\"}")));
        verify(logService, times(1)).add(anyLong(), anyString());
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
