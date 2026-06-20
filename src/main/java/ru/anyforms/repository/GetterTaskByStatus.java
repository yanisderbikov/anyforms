package ru.anyforms.repository;

import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;

import java.util.List;

public interface GetterTaskByStatus {
    List<Task> getByTaskTypeAndStatus(TaskType taskType, TaskStatus taskStatus, int batchSize);
}
