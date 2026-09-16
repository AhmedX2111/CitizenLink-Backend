package com.ntg.citizenlink.repositories;

import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CitizenRepositoryTest {

    @Autowired private CitizenRepository repository;
    @Autowired private AppUserRepository userRepository;

    private AppUser saveCreator() {
        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        creator.setUsername("repo." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        // passwordHash doesn't matter for repository tests, but set a dummy
        creator.setPasswordHash("$2a$10$0123456789abcdef0123456789abcdef0123456789abcdef");
        return userRepository.save(creator);
    }

    private Citizen saveCitizen(String fullName, String fullNameNormalized,
                                String nationalId, String phone, AppUser creator) {
        Citizen citizen = new Citizen();
        citizen.setFullName(fullName);
        citizen.setFullNameNormalized(fullNameNormalized);
        citizen.setNationalId(nationalId);
        citizen.setPhone(phone);
        citizen.setEmail(UUID.randomUUID() + "@test.gov");
        citizen.setPreferredLanguage("en");
        citizen.setCreatedByUser(creator);
        return repository.save(citizen);
    }

    @Test
    void searchCitizens_byNormalizedName_partialMatch() {
        AppUser creator = saveCreator();
        saveCitizen("أحمد محمد", "احمد محمد", "1111111111111111", "01011111111", creator);
        saveCitizen("محمد علي", "محمد علي", "2222222222222222", "01022222222", creator);

        Page<Citizen> result = repository.searchCitizens(
                "احمد", "احمد", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("أحمد محمد");
    }

    @Test
    void searchCitizens_byNationalId_exactMatch() {
        AppUser creator = saveCreator();
        saveCitizen("Omar", "omar", "3333333333333333", "01033333333", creator);

        Page<Citizen> result = repository.searchCitizens(
                "omar", "3333333333333333", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Omar");
    }

    @Test
    void searchCitizens_byPhone_exactMatch() {
        AppUser creator = saveCreator();
        saveCitizen("Sara", "sara", "4444444444444444", "01044444444", creator);

        Page<Citizen> result = repository.searchCitizens(
                "sara", "4444444444444444", "01044444444", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Sara");
    }

    @Test
    void searchCitizens_arabicAlefVariant_normalizedMatch() {
        AppUser creator = saveCreator();
        saveCitizen("إبراهيم", "ابراهيم", "5555555555555555", "01055555555", creator);

        Page<Citizen> result = repository.searchCitizens(
                "ابراهيم", "ابراهيم", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("إبراهيم");
    }

    @Test
    void searchCitizens_combinedOrSearch_matchesAnyField() {
        AppUser creator = saveCreator();
        saveCitizen("Test User", "test user", "6666666666666666", "01066666666", creator);
        saveCitizen("Another User", "another user", "7777777777777777", "01077777777", creator);

        Page<Citizen> result = repository.searchCitizens(
                "01077777777", "01077777777", "01077777777", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Another User");
    }

    @Test
    void searchCitizens_noMatch_returnsEmpty() {
        AppUser creator = saveCreator();
        saveCitizen("Nobody Here", "nobody here", "8888888888888888", "01088888888", creator);

        Page<Citizen> result = repository.searchCitizens(
                "zzzzz", "zzzzz", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchCitizens_pagination_works() {
        AppUser creator = saveCreator();
        for (int i = 0; i < 5; i++) {
            saveCitizen(
                    "Page Test " + i,
                    "page test " + i,
                    String.format("%016d", 1000000000000000L + i),
                    String.format("010%08d", i),
                    creator
            );
        }

        Page<Citizen> page1 = repository.searchCitizens(
                "page test", "page test", null, PageRequest.of(0, 2));
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }
}
