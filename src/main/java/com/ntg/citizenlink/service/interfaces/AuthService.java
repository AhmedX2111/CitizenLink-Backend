package com.ntg.citizenlink.service.interfaces;

import com.ntg.citizenlink.dto.EncryptedAuthResponse;
import com.ntg.citizenlink.dto.LoginRequest;
import org.springframework.security.core.userdetails.UserDetails;


public interface AuthService {

    EncryptedAuthResponse login(LoginRequest request);

    EncryptedAuthResponse refreshToken(String rawRefreshToken);

    void logout(UserDetails userDetails);

    EncryptedAuthResponse getCurrentUser(String username);
}
