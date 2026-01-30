package ru.anyforms.service.s3;

import java.util.List;

public interface GetterPhotosFromS3Folder {
    List<String> getPhotos(String folder);
}
