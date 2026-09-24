package com.neonvibe.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link User} JPA lifecycle callbacks.
 */
class UserTest {

    @Test
    void onCreate_setsBothTimestamps() {
        User user = new User();

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void onUpdate_advancesUpdatedAtOnly() throws InterruptedException {
        User user = new User();
        user.onCreate();
        Instant created = user.getCreatedAt();

        Thread.sleep(5);
        user.onUpdate();

        assertThat(user.getCreatedAt()).isEqualTo(created);
        assertThat(user.getUpdatedAt()).isAfter(created);
    }
}
