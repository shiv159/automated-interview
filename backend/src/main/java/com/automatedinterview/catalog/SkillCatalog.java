package com.automatedinterview.catalog;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Locale;

public final class SkillCatalog {
    public static final String VERSION = "2026-08-04.v1";
    public static final List<Skill> SKILLS = List.of(
        new Skill("CORE_JAVA", "Core Java", List.of("core java", "java se", "jdk", "jvm", "java")),
        new Skill("SPRING_BOOT", "Spring Boot", List.of("spring boot", "spring framework", "spring mvc", "spring data", "jpa", "hibernate", "spring")),
        new Skill("SQL_RELATIONAL", "SQL / Relational Databases", List.of("relational databases", "relational database", "rdbms", "postgresql", "postgres", "mysql", "oracle database", "sql server", "sql")),
        new Skill("ANGULAR", "Angular", List.of("angular framework", "angularjs", "angular"))
    );

    private SkillCatalog() { }

    public static List<String> matchingSkillIds(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Skill skill : SKILLS) {
            boolean found = skill.aliases().stream().sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .anyMatch(alias -> containsAlias(lower, alias));
            if (found) matches.add(skill.id());
        }
        return matches;
    }

    private static boolean containsAlias(String text, String alias) {
        int offset = text.indexOf(alias);
        while (offset >= 0) {
            int end = offset + alias.length();
            if ((offset == 0 || !Character.isLetterOrDigit(text.codePointBefore(offset)))
                && (end == text.length() || !Character.isLetterOrDigit(text.codePointAt(end)))) return true;
            offset = text.indexOf(alias, offset + 1);
        }
        return false;
    }

    public record Skill(String id, String displayName, List<String> aliases) { }
}
