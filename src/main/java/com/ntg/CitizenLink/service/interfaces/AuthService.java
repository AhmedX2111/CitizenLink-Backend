package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import org.springframework.security.core.userdetails.UserDetails;


public interface AuthService {

    EncryptedAuthResponse login(LoginRequest request);

    EncryptedAuthResponse refreshToken(String rawRefreshToken);

    void logout(UserDetails userDetails);

    EncryptedAuthResponse getCurrentUser(String username);
}
