package com.ntg.citizenlink.service;

import com.ntg.citizenlink.dto.agent.request.UpdateUserRequest;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.exception.BusinessRuleException;
import com.ntg.citizenlink.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the M-06 user-admin guards:
 * deactivation sets an explicit state (never toggles back), self-deactivation
 * is rejected, and the last remaining active ADMIN can neither be deactivated
 * nor demoted — so /api/v1/users/** can never be locked out permanently.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserAdminService userAdminService;

    private final UUID id = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    private AppUser activeUser(UserRole role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername("it.user.admin");
        u.setPasswordHash("$2a$10$0123456789abcdef0123456789abcdef0123456789abcdef");
        u.setDisplayName("Test Admin");
        u.setEmail("test@test.gov");
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @Test
    void deactivateUser_setsActiveFalse_explicitNotToggle() {
        AppUser user = activeUser(UserRole.AGENT);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.deactivateUser(id, callerId);
        assertThat(user.getActive()).isFalse();
        assertThat(user.getRefreshTokenJti()).isNull();

        // Calling deactivate again on the already-inactive user must NOT reactivate it.
        userAdminService.deactivateUser(id, callerId);
        assertThat(user.getActive()).isFalse();
    }

    @Test
    void deactivateUser_ownId_isRejected() {
        assertThatThrownBy(() -> userAdminService.deactivateUser(id, id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own account");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_lastActiveAdmin_isRejected() {
        AppUser admin = activeUser(UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> userAdminService.deactivateUser(id, callerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last active ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_admin_whenOtherAdminsExist_succeeds() {
        AppUser admin = activeUser(UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.deactivateUser(id, callerId);
        assertThat(admin.getActive()).isFalse();
    }

    @Test
    void activateUser_setsActiveTrue() {
        AppUser user = activeUser(UserRole.AGENT);
        user.setActive(false);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.activateUser(id, callerId);
        assertThat(user.getActive()).isTrue();
    }

    @Test
    void updateUser_demoteLastActiveAdmin_isRejected() {
        AppUser admin = activeUser(UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("Renamed");
        request.setEmail("test@test.gov");
        request.setRole(UserRole.SUPERVISOR);

        assertThatThrownBy(() -> userAdminService.updateUser(id, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last active ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_demoteAdmin_whenOtherAdminsExist_succeeds() {
        AppUser admin = activeUser(UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("Renamed");
        request.setEmail("test@test.gov");
        request.setRole(UserRole.SUPERVISOR);

        userAdminService.updateUser(id, request);
        assertThat(admin.getRole()).isEqualTo(UserRole.SUPERVISOR);
    }
}