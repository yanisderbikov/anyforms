package ru.anyforms.service.s3;

import java.util.List;

public interface GetterPhotosFromS3Folder {
    List<String> getPhotos(String folder);

    /**
     * Сбросить кеш для папки (no-op, если кеша нет).
     * Вызывать при обновлении folder у продукта.
     */
    default void invalidateFolder(String folder) {
    }
}
