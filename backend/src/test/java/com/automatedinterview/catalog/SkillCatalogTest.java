package com.automatedinterview.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCatalogTest {
    @Test
    void respectsAliasBoundariesAndGroups() {
        assertEquals(List.of("SQL_RELATIONAL"), SkillCatalog.matchingSkillIds("PostgreSQL query design"));
        assertEquals(List.of(), SkillCatalog.matchingSkillIds("JavaScript frontend"));
        assertEquals(List.of("CORE_JAVA", "SPRING_BOOT"), SkillCatalog.matchingSkillIds("Java and Spring Boot"));
    }
}
