package ru.anyforms.repository;

import ru.anyforms.model.task.Task;

public interface SaverTask {
    Task save(Task task);
}
