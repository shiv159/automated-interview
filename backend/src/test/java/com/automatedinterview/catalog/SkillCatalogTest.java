package com.automatedinterview.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillCatalogTest {
    private static final List<SkillCatalog.Skill> SEED = List.of(
        new SkillCatalog.Skill("CORE_JAVA", "Core Java", List.of("core java", "java"), true, "1", "seed"),
        new SkillCatalog.Skill("SPRING_BOOT", "Spring Boot", List.of("spring boot", "spring"), true, "1", "seed"),
        new SkillCatalog.Skill("SQL_RELATIONAL", "SQL", List.of("postgresql", "sql"), true, "1", "seed"),
        new SkillCatalog.Skill("ANGULAR", "Angular", List.of("angular"), true, "1", "seed"));

    @Test
    void respectsAliasBoundariesAndGroups() {
        assertEquals(List.of("SQL_RELATIONAL"), SkillCatalog.matchingSkillIds(SEED, "PostgreSQL query design"));
        assertEquals(List.of(), SkillCatalog.matchingSkillIds(SEED, "JavaScript frontend"));
        assertEquals(List.of("CORE_JAVA", "SPRING_BOOT"), SkillCatalog.matchingSkillIds(SEED, "Java and Spring Boot"));
    }

    @Test
    void loadsSeedMetadataAndExposesActiveCatalog() {
        assertEquals(4, SEED.size());
        assertEquals("seed", SEED.get(0).source());
        assertEquals("1", SEED.get(0).version());
        assertTrue(SEED.stream().allMatch(skill -> skill.aliases().stream().allMatch(alias -> !alias.isBlank())));
        assertEquals(SEED.size(), SEED.stream().map(SkillCatalog.Skill::id).distinct().count());
    }

    @Test
    void validatesSecondarySkillsAgainstTheActiveCatalogWithoutThePrimarySkill() {
        List<SkillCatalog.Skill> skills = List.of(
            new SkillCatalog.Skill("SPRING_BOOT", "Spring Boot", List.of("spring"), true, "1", "seed"),
            new SkillCatalog.Skill("DOCKER", "Docker", List.of("docker"), true, "1", "owner"),
            new SkillCatalog.Skill("SQL_RELATIONAL", "SQL", List.of("sql"), true, "1", "seed"));

        assertEquals(List.of("DOCKER", "SQL_RELATIONAL"),
            SkillCatalog.validateSecondarySkills(skills, "SPRING_BOOT", List.of("DOCKER", "SQL_RELATIONAL")));
        assertThrows(IllegalArgumentException.class,
            () -> SkillCatalog.validateSecondarySkills(skills, "SPRING_BOOT", List.of("SPRING_BOOT")));
        assertThrows(IllegalArgumentException.class,
            () -> SkillCatalog.validateSecondarySkills(skills, "SPRING_BOOT", List.of("KUBERNETES")));
    }
}
