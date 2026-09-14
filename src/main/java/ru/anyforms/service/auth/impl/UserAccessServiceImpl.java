package ru.anyforms.service.auth.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.anyforms.repository.UserRepository;
import ru.anyforms.service.auth.SuperAdminResolver;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;

import java.time.Duration;
import java.util.Optional;

@Service
class UserAccessServiceImpl implements UserAccessService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final SuperAdminResolver superAdminResolver;
    private final LoadingCache<String, Optional<UserAccess>> cache;

    @Autowired
    UserAccessServiceImpl(UserRepository userRepository, SuperAdminResolver superAdminResolver) {
        this(userRepository, superAdminResolver, DEFAULT_TTL);
    }

    UserAccessServiceImpl(UserRepository userRepository, SuperAdminResolver superAdminResolver, Duration ttl) {
        this.userRepository = userRepository;
        this.superAdminResolver = superAdminResolver;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1_000)
                .build(CacheLoader.from(this::load));
    }

    @Override
    public Optional<UserAccess> resolve(String email) {
        String normalized = SuperAdminResolver.normalize(email);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        return cache.getUnchecked(normalized);
    }

    @Override
    public void evict(String email) {
        String normalized = SuperAdminResolver.normalize(email);
        if (normalized != null) {
            cache.invalidate(normalized);
        }
    }

    private Optional<UserAccess> load(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getRole().isAdminPanelRole())
                .map(user -> new UserAccess(user.getEmail(), user.getName(), user.getRole(),
                        superAdminResolver.isSuperAdmin(user.getEmail())));
    }
}
