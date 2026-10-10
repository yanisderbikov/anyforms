package ru.anyforms.service.task.impl;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.task.TaskAdder;

@Service
@Slf4j
class TaskAdderService implements TaskAdder {

    private final SaverTask saverTask;
    private final Gson gson = new Gson();

    TaskAdderService(SaverTask saverTask) {
        this.saverTask = saverTask;
    }

    @Override
    public void addTask(Object payload) {
        try {
            addTaskOrThrow(payload);
        } catch (Exception e) {
            log.error("Ошибка добавления таски", e);
        }
    }

    @Override
    public void addTaskOrThrow(Object payload) {
        TaskType taskType = TaskType.fromObject(payload);
        saverTask.save(Task.builder()
                .type(taskType)
                .payload(gson.toJson(payload))
                .status(TaskStatus.NEW)
                .build());
    }
}
