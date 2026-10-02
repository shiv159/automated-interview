package com.automatedinterview.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.TimeZone;

@Testcontainers
class PostgresVectorContainerTest {
    @Test
    void baselineMigrationCreatesCurrentSchemaOnPgvector() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the container smoke test");
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("interview")
            .withUsername("interview")
            .withPassword("interview-local-only")) {
            postgres.start();
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT '[1,2,3]'::vector::text")) {
                    result.next();
                    assertEquals("[1,2,3]", result.getString(1));
                }
                try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                    result.next();
                    assertEquals(1, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                    SELECT to_regclass('question'), to_regclass('vector_store'), to_regclass('evaluation'),
                           to_regclass('question_embedding')
                    """)) {
                    result.next();
                    assertTrue(result.getString(1) != null);
                    assertTrue(result.getString(2) != null);
                    assertTrue(result.getString(3) != null);
                    assertNull(result.getString(4));
                }
                try (var result = statement.executeQuery("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'public' AND (
                        (table_name = 'question' AND column_name IN ('indexing_status', 'secondary_skills')) OR
                        (table_name = 'interview_session' AND column_name IN ('role_title', 'soft_skill_requirements', 'domain_requirements'))
                    )
                    """)) {
                    result.next();
                    assertEquals(5, result.getInt(1));
                }
                try (var result = statement.executeQuery("SELECT count(*) FROM skill WHERE active AND source = 'seed'")) {
                    result.next();
                    assertEquals(4, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                    SELECT count(*) FROM pg_constraint
                    WHERE conname IN ('question_indexing_status_check', 'question_secondary_skills_array',
                                      'session_question_position_check')
                    """)) {
                    result.next();
                    assertEquals(3, result.getInt(1));
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
