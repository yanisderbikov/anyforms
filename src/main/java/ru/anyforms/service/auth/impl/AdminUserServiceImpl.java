package ru.anyforms.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.auth.AdminUserCreateRequestDTO;
import ru.anyforms.dto.auth.AdminUserDTO;
import ru.anyforms.dto.auth.AdminUserUpdateRequestDTO;
import ru.anyforms.model.Role;
import ru.anyforms.model.User;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.repository.UserRepository;
import ru.anyforms.service.auth.AdminUserService;
import ru.anyforms.service.auth.SuperAdminResolver;
import ru.anyforms.service.auth.UserAccessService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final SuperAdminResolver superAdminResolver;
    private final UserAccessService userAccessService;
    private final GetterShop getterShop;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserDTO> list() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public AdminUserDTO create(AdminUserCreateRequestDTO request) {
        String email = SuperAdminResolver.normalize(request.getEmail());
        requireAdminPanelRole(request.getRole());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Этой почте доступ уже выдан");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(request.getName().trim());
        Role role = superAdminResolver.isSuperAdmin(email) ? Role.ADMIN : request.getRole();
        user.setRole(role);
        user.setShop(shopFor(role, request.getShopSlug()));
        User saved = userRepository.save(user);
        userAccessService.evict(saved.getEmail());
        log.info("Выдан доступ в админку: пользователь id={}, роль {}", saved.getId(), saved.getRole());
        return toDto(saved);
    }

    @Override
    @Transactional
    public AdminUserDTO update(Long id, AdminUserUpdateRequestDTO request) {
        User user = find(id);
        requireAdminPanelRole(request.getRole());
        if (superAdminResolver.isSuperAdmin(user.getEmail()) && request.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Супер-админ всегда ADMIN");
        }
        user.setName(request.getName().trim());
        user.setRole(request.getRole());
        user.setShop(shopFor(request.getRole(), request.getShopSlug()));
        User saved = userRepository.save(user);
        userAccessService.evict(saved.getEmail());
        log.info("Изменён пользователь админки id={}: роль {}", saved.getId(), saved.getRole());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = find(id);
        if (superAdminResolver.isSuperAdmin(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Супер-админа удалить нельзя");
        }
        userRepository.delete(user);
        userAccessService.evict(user.getEmail());
        log.info("Отозван доступ в админку: пользователь id={}", id);
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private Shop shopFor(Role role, String shopSlug) {
        if (role != Role.SHOP_OWNER) {
            return null;
        }
        if (shopSlug == null || shopSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для владельца магазина укажите магазин");
        }
        return getterShop.getBySlug(shopSlug.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Магазин не найден: " + shopSlug));
    }

    private static void requireAdminPanelRole(Role role) {
        if (role == null || !role.isAdminPanelRole()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Роль должна быть одной из: ADMIN, SALES_MANAGER, PROJECT_MANAGER, SHOP_OWNER");
        }
    }

    private AdminUserDTO toDto(User user) {
        return new AdminUserDTO(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                superAdminResolver.isSuperAdmin(user.getEmail()),
                user.getShop() == null ? null : user.getShop().getSlug(),
                user.getShop() == null ? null : user.getShop().getName(),
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
