package ru.anyforms.repository;

import java.util.UUID;

public interface PromoCodeDeleter {
    void deleteById(UUID id);
}
