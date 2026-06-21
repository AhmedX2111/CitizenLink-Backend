package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import org.springframework.security.core.userdetails.UserDetails;


public interface AuthService {

    /**
     * Authenticates credentials and returns a signed JWT with encrypted ID.
     */
    EncryptedAuthResponse login(LoginRequest request);

    /**
     * Logs out a user by logging the event.
     */
    void logout(UserDetails userDetails);

    /**
     * Returns the authenticated user's profile with encrypted ID.
     */
    EncryptedAuthResponse getCurrentUser(String username);
}
