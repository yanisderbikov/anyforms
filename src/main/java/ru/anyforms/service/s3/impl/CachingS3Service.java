package ru.anyforms.service.s3.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.config.s3.S3Static;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Наследник S3Service с кешем по folder.
 * С момента первого подписания (первого запроса по folder) результат кешируется на 55 минут
 * (presigned URL живут 1 час — чтобы не отдавать истёкшие ссылки).
 */
@Service
@Slf4j
public class CachingS3Service extends S3Service {

    private static final int CACHE_EXPIRE_MINUTES = 55;

    private final Cache<String, List<String>> folderCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public CachingS3Service(S3Client s3Client, S3Presigner s3Presigner, S3Static s3Static) {
        super(s3Client, s3Presigner, s3Static);
    }

    @Override
    public List<String> getPhotos(String folder) {
        String cacheKey = normalizeFolderKey(folder);
        List<String> cached = folderCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> result = super.getPhotos(folder);
        folderCache.put(cacheKey, result);
        return result;
    }

    @Override
    public void invalidateFolder(String folder) {
        folderCache.invalidate(normalizeFolderKey(folder));
    }

    private static String normalizeFolderKey(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        return folder.trim().replaceAll("/+$", "");
    }
}
