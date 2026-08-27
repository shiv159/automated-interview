package com.automatedinterview.database;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlywayMigrationRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);
    private final DataSource dataSource;

    public FlywayMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .connectRetries(3)
                    .connectRetriesInterval(3)
                    .load()
                    .migrate();
                log.info("Flyway migration completed successfully.");
                return;
            } catch (Exception e) {
                log.warn("Flyway migration attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    throw e;
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }
}


