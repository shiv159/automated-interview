package com.automatedinterview.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes configured skills before question seeding and session writes begin. */
@Component
@Order(1)
public class SkillCatalogSynchronizer implements CommandLineRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;

    public SkillCatalogSynchronizer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (SkillCatalog.Skill skill : SkillCatalog.activeSkills()) {
            jdbc.sql("""
                INSERT INTO skill (id, display_name, aliases, catalog_version)
                VALUES (:id, :displayName, CAST(:aliases AS jsonb), :version)
                ON CONFLICT (id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    aliases = EXCLUDED.aliases,
                    catalog_version = EXCLUDED.catalog_version
                """)
                .param("id", skill.id())
                .param("displayName", skill.displayName())
                .param("aliases", json(skill.aliases()))
                .param("version", skill.version())
                .update();
        }
    }

    private String json(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize the skill catalog", exception);
        }
    }
}
