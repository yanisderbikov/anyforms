package ru.anyforms.repository;

import java.util.Collection;

public interface TransactionLock {

    void lock(String key);

    void lockAll(Collection<String> keys);
}
