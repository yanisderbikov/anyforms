package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.ReceiptEmailTaskPayload;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
class ReceiptEmailTaskRunner extends AbstractRunnableTask {

    private static final String RECEIPT_SUBJECT = "Ваш чек об оплате anyforms";
    private static final Pattern META_TAG = Pattern.compile("<meta[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CONTENT = Pattern.compile("content\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    ReceiptEmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                           EmailService emailService,
                           SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.RECEIPT_EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        ReceiptEmailTaskPayload payload = gson.fromJson(task.getPayload(), ReceiptEmailTaskPayload.class);
        String previewImage = resolvePreviewImage(payload.getLink());
        emailService.sendEmail(payload.getTo(), RECEIPT_SUBJECT,
                EmailTemplate.getReceiptEmail(payload.getLink(), previewImage));
    }

    private String resolvePreviewImage(String link) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(link))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.startsWith("image/")) {
                return link;
            }
            if (contentType.contains("text/html")) {
                return extractOgImage(new String(response.body(), StandardCharsets.UTF_8));
            }
            return null;
        } catch (Exception e) {
            log.warn("Не получилось построить превью чека по ссылке {}: {}", link, e.getMessage());
            return null;
        }
    }

    private static String extractOgImage(String html) {
        Matcher metaTags = META_TAG.matcher(html);
        while (metaTags.find()) {
            String tag = metaTags.group();
            if (tag.contains("og:image") || tag.contains("twitter:image")) {
                Matcher content = META_CONTENT.matcher(tag);
                if (content.find() && content.group(1).startsWith("http")) {
                    return content.group(1);
                }
            }
        }
        return null;
    }
}
