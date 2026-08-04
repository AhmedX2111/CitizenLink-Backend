package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.exception.ResourceNotFoundException;
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
import java.util.UUID;

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
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            log.info("AUTH EVENT: LOGIN_SUCCESS | username={} | userId={} | role={} | status=SUCCESS",
                    user.getUsername(), user.getId(), user.getRole().name());

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String accessToken = jwtService.generateToken(userDetails, Map.of("role", user.getRole().name()));

            String jti = UUID.randomUUID().toString();
            String refreshToken = jwtService.generateRefreshToken(user.getUsername(), jti);
            user.setRefreshTokenJti(jti);
            appUserRepository.save(user);

            AuthResponse authResponse = toAuthResponse(accessToken, refreshToken, user);
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
    public EncryptedAuthResponse refreshToken(String rawRefreshToken) {
        String username;
        String jti;
        String type;
        try {
            username = jwtService.extractUsername(rawRefreshToken);
            jti = jwtService.extractJti(rawRefreshToken);
            type = jwtService.extractTokenType(rawRefreshToken);
        } catch (Exception e) {
            log.warn("AUTH EVENT: REFRESH_FAILURE | reason=Invalid token format");
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (!"refresh".equals(type)) {
            log.warn("AUTH EVENT: REFRESH_FAILURE | reason=Invalid token type | type={}", type);
            throw new BadCredentialsException("Invalid refresh token");
        }

        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("AUTH EVENT: REFRESH_FAILURE | username={} | reason=User not found", username);
                    return new BadCredentialsException("Invalid refresh token");
                });

        if (!user.getActive()) {
            log.warn("AUTH EVENT: REFRESH_FAILURE | username={} | reason=Account is disabled", username);
            user.setRefreshTokenJti(null);
            appUserRepository.save(user);
            throw new BadCredentialsException("Account is disabled");
        }

        if (user.getRefreshTokenJti() == null || !user.getRefreshTokenJti().equals(jti)) {
            log.warn("AUTH EVENT: REFRESH_FAILURE | username={} | reason=Token revoked or reused", username);
            user.setRefreshTokenJti(null);
            appUserRepository.save(user);
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String newAccessToken = jwtService.generateToken(userDetails, Map.of("role", user.getRole().name()));

        String newJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtService.generateRefreshToken(username, newJti);
        user.setRefreshTokenJti(newJti);
        appUserRepository.save(user);

        log.info("AUTH EVENT: REFRESH_SUCCESS | username={} | userId={}", username, user.getId());

        AuthResponse authResponse = toAuthResponse(newAccessToken, newRefreshToken, user);
        String encryptedId = idEncryptionService.encryptId(user.getId());
        return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);
    }

    @Override
    public void logout(UserDetails userDetails) {
        if (userDetails != null) {
            appUserRepository.findByUsername(userDetails.getUsername())
                    .ifPresent(user -> {
                        user.setRefreshTokenJti(null);
                        appUserRepository.save(user);
                    });
            log.info("AUTH EVENT: LOGOUT | username={} | status=SUCCESS", userDetails.getUsername());
            log.info("User logged out: {}", userDetails.getUsername());
        }
    }

    @Override
    public EncryptedAuthResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", username));

        AuthResponse authResponse = toAuthResponse(null, null, user);
        String encryptedId = idEncryptionService.encryptId(user.getId());

        log.debug("Returning current user with encrypted ID: {}", user.getUsername());

        return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);
    }

    private AuthResponse toAuthResponse(String token, String refreshToken, AppUser user) {
        return new AuthResponse(
                token,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
