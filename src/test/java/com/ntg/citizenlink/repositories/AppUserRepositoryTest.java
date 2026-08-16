package com.ntg.citizenlink.repositories;

import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppUserRepositoryTest {

    @Autowired private AppUserRepository repository;

    @Test
    void findByUsername_returnsUser() {
        AppUser user = repository.save(EntityFactory.appUser(UserRole.AGENT));

        assertThat(repository.findByUsername(user.getUsername()))
                .isPresent()
                .get()
                .extracting(AppUser::getRole)
                .isEqualTo(UserRole.AGENT);
    }

    @Test
    void findByUsername_returnsEmpty_whenMissing() {
        assertThat(repository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void existsByUsername_trueForExistingUser() {
        AppUser user = repository.save(EntityFactory.appUser(UserRole.AGENT));

        assertThat(repository.existsByUsername(user.getUsername())).isTrue();
        assertThat(repository.existsByUsername("missing")).isFalse();
    }

    @Test
    void existsByEmail_trueForExistingUser() {
        AppUser user = repository.save(EntityFactory.appUser(UserRole.AGENT));

        assertThat(repository.existsByEmail(user.getEmail())).isTrue();
        assertThat(repository.existsByEmail("nobody@test.gov")).isFalse();
    }

    @Test
    void findByRoleAndActiveTrue_returnsOnlyActiveUsersOfRole() {
        AppUser activeAgent = repository.save(EntityFactory.appUser(UserRole.AGENT));
        AppUser inactiveAgent = repository.save(EntityFactory.appUser(UserRole.AGENT));
        inactiveAgent.setActive(false);
        repository.save(inactiveAgent);
        AppUser activeHandler = repository.save(EntityFactory.appUser(UserRole.HANDLER));

        List<AppUser> agents = repository.findByRoleAndActiveTrue(UserRole.AGENT);
        assertThat(agents).extracting(AppUser::getUsername)
                .contains(activeAgent.getUsername())
                .doesNotContain(inactiveAgent.getUsername());

        List<AppUser> handlers = repository.findByRoleAndActiveTrue(UserRole.HANDLER);
        assertThat(handlers).extracting(AppUser::getUsername)
                .containsExactly(activeHandler.getUsername());
    }
}
