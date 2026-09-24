package com.neonvibe.service;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}: lookups, 404 and save delegation.
 */
class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("u@example.com");
        return user;
    }

    @Test
    void getById_found() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(user(id)));

        assertThat(service.getById(id).getId()).isEqualTo(id);
    }

    @Test
    void getById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByEmail_delegates() {
        when(repository.findByEmail("u@example.com")).thenReturn(Optional.of(user(UUID.randomUUID())));

        assertThat(service.findByEmail("u@example.com")).isPresent();
        verify(repository).findByEmail("u@example.com");
    }

    @Test
    void findByGoogleId_delegates() {
        when(repository.findByGoogleId("g-1")).thenReturn(Optional.empty());

        assertThat(service.findByGoogleId("g-1")).isEmpty();
        verify(repository).findByGoogleId("g-1");
    }

    @Test
    void save_delegates() {
        User user = user(UUID.randomUUID());
        when(repository.save(user)).thenReturn(user);

        assertThat(service.save(user)).isSameAs(user);
        verify(repository).save(user);
    }
}
