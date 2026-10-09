package ru.anyforms.service.task;

public interface TaskAdder {
    void addTask(Object payload);

    void addTaskOrThrow(Object payload);
}
