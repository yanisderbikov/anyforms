package ru.anyforms.config.s3;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class S3Static {
    @Value("${s3.bucket.name}")
    private String bucketName;

    @Value("${s3.endpoint}")
    private String endpoint;

    public String publicUrl(String key) {
        return endpoint.replaceAll("/+$", "") + "/" + bucketName + "/" + key;
    }
}
