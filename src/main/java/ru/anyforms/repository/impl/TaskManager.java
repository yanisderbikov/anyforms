package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;

import java.util.List;

@Component
@AllArgsConstructor
@Log4j2
class TaskManager implements GetterTaskByStatus, SaverTask {

    private final TaskRepo taskRepo;

    @Override
    public List<Task> getByTaskTypeAndStatus(TaskType taskType, TaskStatus taskStatus, int batchSize) {
        try {
            return taskRepo.findByTypeAndStatusOrderByCreatedAtAsc(taskType, taskStatus, PageRequest.of(0, batchSize));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Task save(Task task) {
        try {
            return taskRepo.save(task);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
