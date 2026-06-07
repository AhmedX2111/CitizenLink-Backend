package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges CitizenLink's AppUser entity with Spring Security's UserDetails contract.
 *
 * Role naming convention:
 *   Spring Security expects GrantedAuthority values prefixed with "ROLE_"
 *   when used with hasRole(). We store raw names (ADMIN, HANDLER, etc.)
 *   and add the prefix here so both hasRole("ADMIN") and
 *   hasAuthority("ROLE_ADMIN") work in SecurityConfig expressions.
 *
 * Active check:
 *   Passing active=false disables the account at the Spring Security level,
 *   which returns 401 before the request reaches any controller.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Loading from DATABASE: " + username); // Debug: always load to avoid cache surprises

        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with username: " + username));

        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name())))
                .disabled(!appUser.isActive())
                .build();
    }
}
