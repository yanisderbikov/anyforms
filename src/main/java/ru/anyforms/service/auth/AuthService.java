package ru.anyforms.service.auth;

import ru.anyforms.dto.LoginRequestDTO;
import ru.anyforms.dto.LoginResponseDTO;
import ru.anyforms.dto.RegisterAdminRequestDTO;

public interface AuthService {
    void registerAdmin(RegisterAdminRequestDTO request);

    LoginResponseDTO login(LoginRequestDTO request);
}
