package ru.anyforms.repository;

import java.util.List;

public interface OrderDeleter {
    void deleteByLeadId(List<Long> leadIds);
}
