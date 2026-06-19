package ru.anyforms.service.task.impl;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.task.TaskAdder;

/**
 * Кладёт новую таску в очередь (таблица {@code task}) со статусом {@link TaskStatus#NEW}.
 * Тип таски определяется по классу payload через {@link TaskType#fromObject(Object)}.
 */
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
            TaskType taskType = TaskType.fromObject(payload);
            Task task = Task.builder()
                    .type(taskType)
                    .payload(gson.toJson(payload))
                    .status(TaskStatus.NEW)
                    .build();
            saverTask.save(task);
        } catch (Exception e) {
            log.error("Ошибка добавления таски", e);
        }
    }
}
