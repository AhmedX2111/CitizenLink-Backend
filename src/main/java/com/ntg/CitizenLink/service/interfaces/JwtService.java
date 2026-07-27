package com.ntg.CitizenLink.service.interfaces;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.Map;


public interface JwtService {

    String generateToken(UserDetails userDetails, Map<String, Object> extraClaims);

    String generateToken(UserDetails userDetails);

    String generateRefreshToken(String username, String jti);

    boolean isTokenValid(String token, UserDetails userDetails);

    String extractUsername(String token);

    String extractRole(String token);

    String extractTokenType(String token);

    String extractJti(String token);

    Date extractExpiration(String token);
}
