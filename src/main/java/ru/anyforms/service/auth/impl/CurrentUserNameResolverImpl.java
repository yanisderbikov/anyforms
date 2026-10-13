package ru.anyforms.service.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.anyforms.service.auth.CurrentUserNameResolver;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class CurrentUserNameResolverImpl implements CurrentUserNameResolver {

    private final UserAccessService userAccessService;

    @Override
    public Optional<String> currentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        // principal — email (см. JwtAuthFilter); межсервисный «service» в users не найдётся
        return userAccessService.resolve(auth.getName())
                .map(CurrentUserNameResolverImpl::displayName);
    }

    private static String displayName(UserAccess access) {
        return access.name() != null && !access.name().isBlank() ? access.name().trim() : access.email();
    }
}
