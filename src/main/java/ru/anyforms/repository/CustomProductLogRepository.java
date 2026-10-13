package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.anyforms.model.CustomProductLog;

import java.util.List;

public interface CustomProductLogRepository extends JpaRepository<CustomProductLog, Long> {

    List<CustomProductLog> findByItemIdOrderByCreatedAtAscIdAsc(Long itemId);
}
