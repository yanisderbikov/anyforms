package ru.anyforms.service.calculator.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.service.calculator.CalculatorReferenceService;
import ru.anyforms.service.s3.S3FileStorage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class CalculatorReferenceServiceImpl implements CalculatorReferenceService {

    static final String KEY_PREFIX = "order-calculator/references";

    private final S3FileStorage s3FileStorage;

    @Override
    public S3FileStorage.PresignedUpload presignUpload(String filename, String contentType) {
        return s3FileStorage.presignUpload(filename, contentType, KEY_PREFIX);
    }

    @Override
    public Map<String, String> viewUrls(Collection<String> keys) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || !key.startsWith(KEY_PREFIX + "/") || key.contains("..") || urls.containsKey(key)) {
                continue;
            }
            try {
                urls.put(key, s3FileStorage.presignedUrl(key));
            } catch (Exception e) {
                log.warn("Не удалось подписать ссылку на референс {}: {}", key, e.getMessage());
            }
        }
        return urls;
    }
}
