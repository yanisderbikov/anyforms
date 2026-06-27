package ru.anyforms.config.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
public class S3Config {

    @Value("${s3.access-key-id}")
    private String accessKey;

    @Value("${s3.secret-access-key}")
    private String accessSecretKey;

    @Value("${s3.region}")
    private String region;

    @Value("${s3.endpoint}")
    private String endpointApi;

    private AwsBasicCredentials credentials() {
        // trim — на случай переноса строки/пробелов в .env (частая причина SignatureDoesNotMatch).
        String key = accessKey == null ? null : accessKey.trim();
        String secret = accessSecretKey == null ? null : accessSecretKey.trim();
        log.info("S3 config: endpoint='{}', region='{}', bucketAccessKeyIdPrefix='{}', accessKeyLen={}, secretLen={}",
                endpointApi == null ? null : endpointApi.trim(),
                region == null ? null : region.trim(),
                key != null && key.length() >= 4 ? key.substring(0, 4) + "…" : "?",
                key == null ? 0 : key.length(),
                secret == null ? 0 : secret.length());
        return AwsBasicCredentials.create(key, secret);
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region.trim()))
                .endpointOverride(URI.create(endpointApi.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(false)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner createS3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region.trim()))
                .endpointOverride(URI.create(endpointApi.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .build();
    }
}
