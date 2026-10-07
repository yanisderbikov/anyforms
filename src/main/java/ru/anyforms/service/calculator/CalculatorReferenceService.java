package ru.anyforms.service.calculator;

import ru.anyforms.service.s3.S3FileStorage;

import java.util.Collection;
import java.util.Map;

public interface CalculatorReferenceService {

    S3FileStorage.PresignedUpload presignUpload(String filename, String contentType);

    Map<String, String> viewUrls(Collection<String> keys);
}
