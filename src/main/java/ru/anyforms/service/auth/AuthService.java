package ru.anyforms.service.auth;

import ru.anyforms.dto.LoginResponseDTO;

public interface AuthService {
    void requestLoginCode(String email);

    void requestMcpLoginCode(String email);

    LoginResponseDTO verifyLoginCode(String email, String code);

    LoginResponseDTO verifyMcpLoginCode(String email, String code);
}
