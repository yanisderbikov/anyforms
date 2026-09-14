package ru.anyforms.service.auth;

import ru.anyforms.dto.LoginResponseDTO;

public interface AuthService {
    void requestLoginCode(String email);

    LoginResponseDTO verifyLoginCode(String email, String code);
}
