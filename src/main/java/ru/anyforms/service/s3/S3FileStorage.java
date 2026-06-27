package ru.anyforms.service.s3;

import org.springframework.web.multipart.MultipartFile;

/**
 * Key-based операции с S3 (в отличие от {@link GetterPhotosFromS3Folder}, работающего по папке).
 * Используется для файлов кастомных позиций: загрузка/удаление конкретного объекта по ключу.
 */
public interface S3FileStorage {

    /**
     * Загружает файл под ключом {@code keyPrefix/uuid.ext}.
     * @return полный ключ объекта в бакете.
     */
    String upload(MultipartFile file, String keyPrefix);

    /** Удаляет объект по ключу (no-op при пустом ключе). */
    void delete(String key);

    /** Presigned GET URL для ключа. */
    String presignedUrl(String key);
}
