package com.automatedinterview.catalog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCatalogService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SkillCatalogService(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public List<SkillCatalog.Skill> activeSkills() {
        return jdbc.sql("SELECT id, display_name, aliases, active, catalog_version, source FROM skill WHERE active = true ORDER BY id")
            .query((rs, row) -> new SkillCatalog.Skill(rs.getString("id"), rs.getString("display_name"),
                aliases(rs.getString("aliases")), rs.getBoolean("active"), rs.getString("catalog_version"), rs.getString("source"))).list();
    }

    public List<String> matchingSkillIds(String text) { return SkillCatalog.matchingSkillIds(activeSkills(), text); }

    public boolean isKnown(String id) { return jdbc.sql("SELECT count(*) FROM skill WHERE id = :id AND active = true").param("id", id).query(Long.class).single() > 0; }

    @Transactional
    public void approve(String id, String displayName, List<String> aliases, String version) {
        if (id == null || !id.matches("[A-Z][A-Z0-9_]{1,63}") || displayName == null || displayName.isBlank()
                || aliases == null || aliases.isEmpty() || aliases.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException("Invalid skill definition");
        if (jdbc.sql("SELECT count(*) FROM skill WHERE id = :id").param("id", id).query(Long.class).single() > 0)
            throw new IllegalArgumentException("Skill already exists");
        jdbc.sql("""
            INSERT INTO skill (id, display_name, aliases, catalog_version, active, source)
            VALUES (:id, :displayName, CAST(:aliases AS jsonb), :version, true, 'OWNER_APPROVAL')
            ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name,
                aliases = EXCLUDED.aliases, catalog_version = EXCLUDED.catalog_version,
                active = true, source = 'OWNER_APPROVAL'
            """).param("id", id).param("displayName", displayName.strip())
            .param("aliases", json(aliases)).param("version", version == null || version.isBlank() ? "1" : version).update();
    }

    private List<String> aliases(String value) {
        try { return json.readValue(value, new TypeReference<List<String>>() { }); }
        catch (Exception exception) { throw new IllegalStateException("Invalid skill aliases", exception); }
    }

    private String json(List<String> values) {
        try { return json.writeValueAsString(values); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize aliases", exception); }
    }
}
