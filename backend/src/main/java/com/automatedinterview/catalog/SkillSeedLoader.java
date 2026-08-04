package com.automatedinterview.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class SkillSeedLoader implements CommandLineRunner {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SkillSeedLoader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws JsonProcessingException {
        for (SkillCatalog.Skill skill : SkillCatalog.SKILLS) {
            jdbc.sql("""
                INSERT INTO skill (id, display_name, aliases, catalog_version)
                VALUES (:id, :displayName, CAST(:aliases AS jsonb), :catalogVersion)
                ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name,
                    aliases = EXCLUDED.aliases, catalog_version = EXCLUDED.catalog_version
                """)
                .param("id", skill.id())
                .param("displayName", skill.displayName())
                .param("aliases", mapper.writeValueAsString(skill.aliases()))
                .param("catalogVersion", SkillCatalog.VERSION)
                .update();
        }
    }
}
