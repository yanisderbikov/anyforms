package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.anyforms.dto.CustomProductFileDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductLogDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.dto.marketplace.ShopSalesReportDTO;
import ru.anyforms.dto.mcp.McpCustomOrderDTO;
import ru.anyforms.dto.mcp.McpSalesAnalyticsDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.service.mcp.McpSalesAnalytics;
import ru.anyforms.service.product.ShopSalesReportService;
import ru.anyforms.dto.mcp.McpToolDescriptor;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.model.Order;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.GetterOrderDTOByType;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpToolRegistry;
import ru.anyforms.util.converter.ConverterOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomProductItemService itemService = mock(CustomProductItemService.class);
    private final CustomProductLogService logService = mock(CustomProductLogService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ConverterOrder converterOrder = mock(ConverterOrder.class);
    private final GetterOrderDTOByType getterOrderDTOByType = mock(GetterOrderDTOByType.class);
    private final ShopSalesReportService reportService = mock(ShopSalesReportService.class);
    private final GetterShop getterShop = mock(GetterShop.class);
    private final McpCustomOrderReader reader = new McpCustomOrderReader(orderRepository,
            mock(CustomProductItemRepository.class), converterOrder, itemService, getterOrderDTOByType);
    private final McpToolRegistry registry = new McpToolRegistry(List.of(
            new ListCustomOrdersTool(reader),
            new GetCustomOrderTool(reader),
            new ListCustomItemsTool(itemService, reader),
            new GetCustomItemTool(itemService),
            new GetItemLogsTool(logService),
            new ReadyToShipTool(itemService, reader),
            new InDeliveryTool(itemService, reader),
            new SetItemStatusTool(itemService, reader),
            new AddItemLogTool(logService),
            new ListRetailOrdersTool(reader),
            new GetSalesAnalyticsTool(new McpSalesAnalytics(reportService, getterShop))));

    @Test
    void onlyStatusAndLogToolsWrite() {
        List<McpToolDescriptor> tools = registry.descriptors();

        assertEquals(List.of("add_item_log", "get_custom_item", "get_custom_order", "get_item_logs", "get_sales_analytics",
                "list_custom_items",
                "list_custom_orders", "list_in_delivery", "list_ready_to_ship", "list_retail_orders", "set_item_status"),
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

    private OrderSummaryDTO orderWithPhone() {
        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setContactName("Иван Иванов");
        dto.setContactPhone("+79991234567");
        return dto;
    }

    @Test
    void ordersComeWithNameButWithoutPhone() throws Exception {
        Order order = new Order();
        order.setId(7L);
        when(orderRepository.findWorkableCustomOrders()).thenReturn(List.of(order));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(converterOrder.convert(order)).thenAnswer(inv -> orderWithPhone());
        ShipGroupDTO group = new ShipGroupDTO();
        group.setOrder(orderWithPhone());
        group.setItems(new ArrayList<>());
        when(itemService.getReadyToShipGroups()).thenReturn(List.of(group));

        List<OrderSummaryDTO> orders = new ArrayList<>((List<OrderSummaryDTO>) registry.find("list_custom_orders")
                .orElseThrow().call(objectMapper.readTree("{}")));
        orders.add(((McpCustomOrderDTO) registry.find("get_custom_order").orElseThrow()
                .call(objectMapper.readTree("{\"orderId\":7}"))).order());
        ((List<ShipGroupDTO>) registry.find("list_ready_to_ship").orElseThrow()
                .call(objectMapper.readTree("{}"))).forEach(g -> orders.add(g.getOrder()));

        assertEquals(3, orders.size());
        orders.forEach(dto -> {
            assertNull(dto.getContactPhone());
            assertEquals("Иван Иванов", dto.getContactName());
        });
    }

    @Test
    void retailOrdersKeepPaymentDateAndCommentButNotPhone() throws Exception {
        OrderSummaryDTO retail = orderWithPhone();
        retail.setPurchaseDate(LocalDateTime.of(2026, 10, 28, 14, 5));
        retail.setComment("Позвонить перед отправкой");
        when(getterOrderDTOByType.getOrdersWithoutTrackerDTOs()).thenReturn(List.of(retail));

        List<OrderSummaryDTO> orders = (List<OrderSummaryDTO>) registry.find("list_retail_orders").orElseThrow()
                .call(objectMapper.readTree("{}"));

        OrderSummaryDTO dto = orders.get(0);
        assertNull(dto.getContactPhone());
        assertEquals("Иван Иванов", dto.getContactName());
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 5), dto.getPurchaseDate());
        assertEquals("Позвонить перед отправкой", dto.getComment());
    }

    private ShopSalesReportDTO report(String slug, long orders, long molds, long totalKopecks) {
        return ShopSalesReportDTO.builder().shopSlug(slug).shopName(slug).ordersCount(orders).itemsCount(molds)
                .totalKopecks(totalKopecks)
                .products(List.of(ShopSalesReportDTO.ProductSalesDTO.builder()
                        .productName("Молд " + slug).quantity(molds).totalKopecks(null).build()))
                .build();
    }

    @Test
    void salesAnalyticsSumsShopsAndCountsAverageCheck() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        Shop pastry = new Shop();
        pastry.setSlug("af_pastry");
        Shop main = new Shop();
        main.setSlug("anyforms");
        when(getterShop.getAllShops()).thenReturn(List.of(pastry, main));
        when(reportService.getReport("af_pastry", from, to)).thenReturn(report("af_pastry", 3, 7, 1_000_000));
        when(reportService.getReport("anyforms", from, to)).thenReturn(report("anyforms", 0, 0, 0));
        var tool = registry.find("get_sales_analytics").orElseThrow();

        McpSalesAnalyticsDTO all = (McpSalesAnalyticsDTO) tool.call(
                objectMapper.readTree("{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}"));
        assertEquals(3, all.ordersCount());
        assertEquals(7, all.moldsCount());
        assertEquals(new BigDecimal("10000.00"), all.totalRub());
        assertEquals(new BigDecimal("3333.33"), all.averageCheckRub());
        assertEquals(2, all.shops().size());
        assertEquals(new BigDecimal("0.00"), all.shops().get(1).averageCheckRub());
        assertNull(all.shops().get(0).products().get(0).totalRub());

        McpSalesAnalyticsDTO one = (McpSalesAnalyticsDTO) tool.call(
                objectMapper.readTree("{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\",\"shop\":\"af_pastry\"}"));
        assertEquals(List.of("af_pastry"), one.shops().stream().map(McpSalesAnalyticsDTO.Shop::shopSlug).toList());

        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"from\":\"2026-10-31\",\"to\":\"2026-10-01\"}")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.call(objectMapper.readTree("{\"from\":\"01.10.2026\",\"to\":\"2026-10-31\"}")));
        assertThrows(IllegalArgumentException.class, () -> tool.call(objectMapper.readTree("{\"from\":\"2026-10-01\"}")));
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
