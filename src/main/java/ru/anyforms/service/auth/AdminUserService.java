package ru.anyforms.service.auth;

import ru.anyforms.dto.auth.AdminUserCreateRequestDTO;
import ru.anyforms.dto.auth.AdminUserDTO;
import ru.anyforms.dto.auth.AdminUserUpdateRequestDTO;

import java.util.List;

public interface AdminUserService {
    List<AdminUserDTO> list();

    AdminUserDTO create(AdminUserCreateRequestDTO request);

    AdminUserDTO update(Long id, AdminUserUpdateRequestDTO request);

    void delete(Long id);
}
