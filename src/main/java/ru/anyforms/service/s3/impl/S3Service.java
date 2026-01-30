package ru.anyforms.service.s3.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.config.s3.S3Static;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
class S3Service implements GetterPhotosFromS3Folder {

    private static final Duration PRESIGN_DURATION = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Static s3Static;

    @Override
    public List<String> getPhotos(String folder) {
        String bucketName = s3Static.getBucketName();
        String prefix = folder == null ? "" : folder.endsWith("/") ? folder : folder + "/";
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
}
