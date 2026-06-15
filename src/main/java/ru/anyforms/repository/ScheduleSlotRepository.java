package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.ScheduleSlot;

import java.util.List;

@Repository
public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    /** Активные слоты заданного дня недели (ISO: 1=Пн ... 7=Вс). */
    List<ScheduleSlot> findByWeekdayAndEnabledIsTrue(Integer weekday);
}
