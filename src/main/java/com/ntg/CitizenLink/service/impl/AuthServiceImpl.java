package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.service.interfaces.AuthService;
import com.ntg.CitizenLink.service.interfaces.IdEncryptionService;
import com.ntg.CitizenLink.service.interfaces.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final IdEncryptionService idEncryptionService;

    @Override
    public EncryptedAuthResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.username());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            AppUser user = appUserRepository.findByUsername(request.username())
                    .orElseThrow();

            log.info("AUTH EVENT: LOGIN_SUCCESS | username={} | userId={} | role={} | status=SUCCESS",
                    user.getUsername(), user.getId(), user.getRole().name());

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtService.generateToken(userDetails, Map.of("role", user.getRole().name()));

            AuthResponse authResponse = toAuthResponse(token, user);
            String encryptedId = idEncryptionService.encryptId(user.getId());

            log.info("Login successful for user: {}", user.getUsername());

            return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);

        } catch (BadCredentialsException e) {
            log.warn("AUTH EVENT: LOGIN_FAILURE | username={} | reason=Bad credentials | status=FAILED", request.username());
            throw e;
        } catch (DisabledException e) {
            log.warn("AUTH EVENT: LOGIN_FAILURE | username={} | reason=Account is disabled/inactive | status=FAILED", request.username());
            throw e;
        } catch (Exception e) {
            log.error("AUTH EVENT: LOGIN_FAILURE | username={} | reason={} | status=FAILED",
                    request.username(), e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public void logout(UserDetails userDetails) {
        if (userDetails != null) {
            log.info("AUTH EVENT: LOGOUT | username={} | status=SUCCESS", userDetails.getUsername());
            log.info("User logged out: {}", userDetails.getUsername());
        }
    }

    @Override
    public EncryptedAuthResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow();

        AuthResponse authResponse = toAuthResponse(null, user);
        String encryptedId = idEncryptionService.encryptId(user.getId());

        log.debug("Returning current user with encrypted ID: {}", user.getUsername());

        return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);
    }

    private AuthResponse toAuthResponse(String token, AppUser user) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
