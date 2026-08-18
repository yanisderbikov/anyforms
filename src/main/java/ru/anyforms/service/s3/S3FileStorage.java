package ru.anyforms.service.s3;

/**
 * Key-based операции с S3 (в отличие от {@link GetterPhotosFromS3Folder}, работающего по папке).
 * Загрузка идёт напрямую из браузера по presigned PUT — бэкенд файлы не проксирует, только подписывает URL.
 */
public interface S3FileStorage {

    /** Подписанный URL для прямой загрузки из браузера в S3 (PUT), минуя бэкенд. */
    record PresignedUpload(String uploadUrl, String key) {
    }

    /**
     * Presigned PUT URL под ключом {@code keyPrefix/uuid.ext} (от исходного имени остаётся только расширение).
     * Content-Type входит в подпись: браузер обязан отправить PUT с тем же заголовком.
     */
    PresignedUpload presignUpload(String filename, String contentType, String keyPrefix);

    /** Удаляет объект по ключу (no-op при пустом ключе). */
    void delete(String key);

    /** Presigned GET URL для ключа. */
    String presignedUrl(String key);
}
