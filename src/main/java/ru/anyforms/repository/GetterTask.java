package ru.anyforms.repository;

import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskType;

import java.util.List;

public interface GetterTask {
    List<Task> getRecentByType(TaskType type, int limit);
}
