package com.automatedinterview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Runs on application startup (after Flyway and seed loading) to ensure the
 * {@code vector_store} table is consistent with the authoritative {@code question} table.
 *
 * <p>Any ACTIVE question missing from {@code vector_store} (e.g. because the V2 migration
 * discarded 64-dimensional local embeddings, or because a previous {@code add()} call
 * failed) is re-embedded and inserted.
 *
 * <p>Ordered at {@code 20} so it runs after {@link com.automatedinterview.questionbank.QuestionSeedLoader}
 * (order 10).
 */
@Configuration
public class VectorReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(VectorReconciliationJob.class);

    @Bean
    @Order(20)
    public ApplicationRunner vectorReconciliationRunner(VectorSyncService vectorSyncService) {
        return args -> {
            log.info("VectorReconciliationJob: starting vector consistency check.");
            vectorSyncService.reconcileMissingVectors();
            log.info("VectorReconciliationJob: complete.");
        };
    }
}
