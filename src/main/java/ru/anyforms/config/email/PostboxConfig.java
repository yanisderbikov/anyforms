package ru.anyforms.config.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "email.provider", havingValue = "postbox")
public class PostboxConfig {

    @Bean
    public SesV2Client postboxClient(@Value("${email.postbox.endpoint}") String endpoint,
                                     @Value("${email.postbox.region}") String region,
                                     @Value("${email.postbox.access-key-id}") String accessKeyId,
                                     @Value("${email.postbox.secret-access-key}") String secretAccessKey) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return SesV2Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
