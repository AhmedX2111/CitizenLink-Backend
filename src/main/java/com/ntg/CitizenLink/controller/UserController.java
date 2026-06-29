package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.HandlerResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AppUserRepository appUserRepository;

    @GetMapping("/handlers")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<List<HandlerResponse>> listHandlers() {
        List<AppUser> handlers = appUserRepository.findByRoleAndActiveTrue(UserRole.HANDLER);
        List<HandlerResponse> response = handlers.stream()
                .map(h -> new HandlerResponse(h.getId(), h.getDisplayName(), h.getEmail()))
                .toList();
        return ResponseEntity.ok(response);
    }
}
