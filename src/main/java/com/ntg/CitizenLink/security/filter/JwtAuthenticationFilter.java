package com.ntg.CitizenLink.security.filter;

import com.ntg.CitizenLink.security.JwtBlocklist;
import com.ntg.CitizenLink.service.interfaces.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per HTTP request (OncePerRequestFilter guarantee).
 *
 * Flow:
 *   1. Read the Authorization header — skip if absent or not "Bearer ...".
 *   2. Extract the JWT and parse the username from its subject claim.
 *   3. If no authentication is already set in the SecurityContext, load the
 *      UserDetails and validate the token.
 *   4. On success, set a UsernamePasswordAuthenticationToken in the context
 *      so downstream filters and controllers see the user as authenticated.
 *
 * Why check SecurityContextHolder first (step 3)?
 *   Prevents re-processing when another filter (e.g. BasicAuth) already set
 *   the authentication, and avoids the unnecessary DB load.
 *
 * This filter must be registered BEFORE UsernamePasswordAuthenticationFilter
 * in the filter chain — done via addFilterBefore() in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER   = "Authorization";

    private final JwtService          jwtService;
    private final JwtBlocklist        jwtBlocklist;
    private final UserDetailsService  userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTH_HEADER);

        // 1. No bearer token — pass through; security rules handle unauthorized access.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract token and username.
        final String jwt      = authHeader.substring(BEARER_PREFIX.length());
        final String username;

        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Only authenticate if username was parsed and context is not yet populated.
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                String jti = jwtService.extractJti(jwt);
                if (jti != null && jwtBlocklist.isBlocked(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // 4. Build authentication token and attach request details (IP, session id, etc.)
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials null → already authenticated
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
