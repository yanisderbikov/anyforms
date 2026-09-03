package ru.anyforms.repository;

import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskType;

import java.util.List;

public interface GetterTask {
    List<Task> getRecentByType(TaskType type, int limit);

    List<Task> getAllByType(TaskType type);

    /** Есть ли таска типа, в payload которой встречается фрагмент (например, ID транзакции) — защита от дублей */
    boolean existsByTypeAndPayloadContaining(TaskType type, String payloadFragment);
}
