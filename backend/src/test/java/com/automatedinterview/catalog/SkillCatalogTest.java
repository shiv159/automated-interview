package com.automatedinterview.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCatalogTest {
    @Test
    void respectsAliasBoundariesAndGroups() {
        assertEquals(List.of("SQL_RELATIONAL"), SkillCatalog.matchingSkillIds("PostgreSQL query design"));
        assertEquals(List.of(), SkillCatalog.matchingSkillIds("JavaScript frontend"));
        assertEquals(List.of("CORE_JAVA", "SPRING_BOOT"), SkillCatalog.matchingSkillIds("Java and Spring Boot"));
    }

    @Test
    void loadsSeedMetadataAndExposesActiveCatalog() {
        assertEquals(4, SkillCatalog.activeSkills().size());
        assertEquals("seed", SkillCatalog.activeSkills().get(0).source());
        assertEquals("1", SkillCatalog.activeSkills().get(0).version());
        assertTrue(SkillCatalog.activeSkills().stream().allMatch(skill -> skill.aliases().stream().allMatch(alias -> !alias.isBlank())));
        assertEquals(SkillCatalog.activeSkills().size(), SkillCatalog.activeSkills().stream().map(SkillCatalog.Skill::id).distinct().count());
    }
}
