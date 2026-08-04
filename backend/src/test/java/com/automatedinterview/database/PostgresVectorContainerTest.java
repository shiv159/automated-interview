package com.automatedinterview.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void postgresContainerProvidesVectorExtension() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the container smoke test");
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("interview")
            .withUsername("interview")
            .withPassword("interview-local-only")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
                try (var result = statement.executeQuery("SELECT '[1,2,3]'::vector::text")) {
                    result.next();
                    assertEquals("[1,2,3]", result.getString(1));
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
