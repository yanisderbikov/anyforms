package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.telegram.RetailOrderTelegramPayload;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.telegram.TelegramService;

import java.util.List;

@Slf4j
@Component
class RetailOrderTelegramTaskRunner extends AbstractRunnableTask {

    private static final String ORDERS_WITHOUT_TRACKER_URL = "https://anyforms.ru/orders/without-tracker";

    private final GetterTaskByStatus getterTaskByStatus;
    private final TelegramService telegramService;
    private final OrderRepository orderRepository;
    private final Gson gson = new Gson();

    RetailOrderTelegramTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                  TelegramService telegramService,
                                  OrderRepository orderRepository,
                                  SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.telegramService = telegramService;
        this.orderRepository = orderRepository;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.RETAIL_ORDER_TELEGRAM, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        if (!telegramService.isConfigured()) {
            log.warn("Telegram не настроен — уведомление о розничном заказе пропущено (таска {})", task.getId());
            return;
        }
        RetailOrderTelegramPayload payload = gson.fromJson(task.getPayload(), RetailOrderTelegramPayload.class);
        if (payload == null || payload.getOrderId() == null) {
            throw new IllegalStateException("В payload таски нет orderId: " + task.getPayload());
        }
        Order order = orderRepository.findById(payload.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Заказ #" + payload.getOrderId() + " не найден"));
        telegramService.sendMessageWithUrlButton(buildMessage(order), "Заказы (anyforms)", ORDERS_WITHOUT_TRACKER_URL);
    }

    private String buildMessage(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🆕 Новый заказ в рознице №");
        sb.append(order.getPublicId() != null ? order.getPublicId() : order.getId());
        sb.append("\n");

        sb.append("Получатель: ").append(orDash(order.getContactName())).append("\n");
        sb.append("ПВЗ город: ").append(orDash(order.getPvzSdekCity())).append("\n");
        sb.append("ПВЗ улица: ").append(orDash(order.getPvzSdekStreet())).append("\n");
        List<OrderItem> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            sb.append("Состав:\n");
            for (OrderItem item : items) {
                sb.append("  • ").append(item.getProductName());
                if (item.getQuantity() != null && item.getQuantity() > 1) {
                    sb.append(" ×").append(item.getQuantity());
                }
                sb.append("\n");
            }
        }

        sb.append("\nСейчас в рознице:\n");
        sb.append("  • ждут отправки: ").append(orderRepository.countRetailAwaitingShipment()).append("\n");
        sb.append("  • в доставке: ").append(orderRepository.countRetailInDelivery());
        return sb.toString();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
