package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.anyforms.model.CustomProductFile;

public interface CustomProductFileRepository extends JpaRepository<CustomProductFile, Long> {
}
