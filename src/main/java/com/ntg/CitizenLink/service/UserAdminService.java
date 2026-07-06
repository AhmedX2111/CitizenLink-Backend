package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.agent.request.UserSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.UserResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(UserSearchRequest request) {
        log.info("Admin user list: role={}, active={}, page={}",
                request.getRole(), request.getActive(), request.getPage());

        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<AppUser> page = userRepository.findAllFiltered(
                request.getRole(), request.getActive(), pageable);

        List<UserResponse> content = page.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private UserResponse toResponse(AppUser u) {
        // Generate a human-readable display reference from the UUID
        // e.g. "USR-8492-X" — first 4 hex chars + dash + first char of role
        String hex  = u.getId().toString().replace("-", "").substring(0, 4).toUpperCase();
        String role = u.getRole().name().substring(0, 1);
        String ref  = "USR-" + hex + "-" + role;

        return UserResponse.builder()
                .id(u.getId())
                .displayRef(ref)
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .email(u.getEmail())
                .role(u.getRole())
                .active(u.getActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}