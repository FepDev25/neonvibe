package com.neonvibe;

import java.util.List;
import java.util.Map;

import com.neonvibe.domain.Track;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-PostgreSQL smoke test: boots the full application against a throwaway
 * Postgres container so Flyway applies V1..V7 and Hibernate's
 * {@code ddl-auto: validate} proves the entities match the migrated schema.
 *
 * <p>H2 in PostgreSQL mode cannot catch dialect-level mismatches (JSON, native
 * SQL, quoting, constraints), so this is the safety net for the production DB.
 * Automatically skipped when no Docker daemon is available.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("neonvibe.music.paths", () -> "/nonexistent-postgres-test-music");
        registry.add("neonvibe.covers.cache-path",
                () -> System.getProperty("java.io.tmpdir") + "/neonvibe-pg-covers");
        registry.add("neonvibe.lyrics.cache-path",
                () -> System.getProperty("java.io.tmpdir") + "/neonvibe-pg-lyrics");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrackRepository trackRepository;

    @Test
    void flyway_appliesAllMigrationsSuccessfully() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(rows).hasSize(7);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void entitiesMatchMigratedSchemaAndCustomSearchWorks() {
        // If ddl-auto=validate had failed, the context would not have started at all.
        Track track = new Track();
        track.setFilePath("/pg/test.mp3");
        track.setTitle("Postgres Song");
        track.setArtist("PG Artist");
        track.setGenre("Rock");
        track.setYear(2026);
        track.setHasLyrics(false);
        track.setAvailable(true);
        trackRepository.save(track);

        var page = trackRepository.search("postgres", null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Postgres Song");
    }
}
