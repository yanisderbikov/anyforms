package ru.anyforms.service.s3.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.config.s3.S3Static;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.service.s3.S3FileStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
class S3Service implements GetterPhotosFromS3Folder, S3FileStorage {

    private static final Duration PRESIGN_DURATION = Duration.ofHours(1);
    private static final String SHOP_PREFIX = "shop/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Static s3Static;

    @Override
    public List<String> getPhotos(String folder) {
        String bucketName = s3Static.getBucketName();
        String normalizedFolder = folder == null || folder.isBlank() ? "" : folder.trim().replaceAll("/+$", "");
        String prefix = SHOP_PREFIX + (normalizedFolder.isEmpty() ? "" : normalizedFolder + "/");
        List<String> urls = new ArrayList<>();

        String continuationToken = null;
        do {
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            for (S3Object obj : response.contents()) {
                String key = obj.key();
                if (!key.endsWith("/")) {
                    urls.add(generatePresignedUrl(key));
                }
            }
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return urls;
    }

    private String generatePresignedUrl(String pathToFile) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Static.getBucketName())
                .key(pathToFile)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(PRESIGN_DURATION)
                .build();

        return s3Presigner.presignGetObject(getObjectPresignRequest).url().toString();
    }

    @Override
    public String upload(MultipartFile file, String keyPrefix) {
        String prefix = keyPrefix == null ? "" : keyPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
        String ext = extractExtension(file.getOriginalFilename());
        String key = (prefix.isEmpty() ? "" : prefix + "/") + UUID.randomUUID() + ext;

        Path tempFile = null;
        try {
            // Грузим из файла (как в vizhuonline): SDK сам считает длину и SHA-256 → обычная подпись,
            // без streaming/chunked (иначе Yandex отдаёт SignatureDoesNotMatch).
            tempFile = Files.createTempFile("s3-upload-", ext.isEmpty() ? ".tmp" : ext);
            try (var in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Static.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, tempFile);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить файл в S3: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
        return key;
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Static.getBucketName())
                .key(key)
                .build());
    }

    @Override
    public String presignedUrl(String key) {
        return generatePresignedUrl(key);
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }
}
