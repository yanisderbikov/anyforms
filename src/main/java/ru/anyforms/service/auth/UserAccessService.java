package ru.anyforms.service.auth;

import java.util.Optional;

public interface UserAccessService {
    Optional<UserAccess> resolve(String email);

    void evict(String email);
}
