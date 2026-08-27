package com.automatedinterview.catalog;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SkillCatalog {
    public static final String VERSION = "2026-08-04.v1";
    public static final List<Skill> SKILLS = loadSkills();

    private SkillCatalog() { }

    private static List<Skill> loadSkills() {
        try (var stream = SkillCatalog.class.getResourceAsStream("/skills.json")) {
            if (stream == null) throw new IllegalStateException("skills.json is missing");
            List<Skill> skills = List.copyOf(new ObjectMapper().readValue(stream, new TypeReference<List<Skill>>() { }));
            validate(skills);
            return skills;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static List<String> matchingSkillIds(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Skill skill : activeSkills()) {
            boolean found = skill.aliases().stream().sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .anyMatch(alias -> containsAlias(lower, alias));
            if (found) matches.add(skill.id());
        }
        return matches;
    }

    public static List<Skill> activeSkills() { return SKILLS.stream().filter(Skill::active).toList(); }

    private static void validate(List<Skill> skills) {
        Set<String> ids = new HashSet<>();
        for (Skill skill : skills) {
            if (skill.id() == null || skill.id().isBlank() || !ids.add(skill.id()))
                throw new IllegalStateException("Skill IDs must be nonblank and unique");
            if (skill.displayName() == null || skill.displayName().isBlank())
                throw new IllegalStateException("Skill display names must be nonblank");
            if (skill.aliases() == null || skill.aliases().stream().anyMatch(alias -> alias == null || alias.isBlank()))
                throw new IllegalStateException("Skill aliases must be nonblank");
            if (skill.version() == null || skill.version().isBlank())
                throw new IllegalStateException("Skill versions must be nonblank");
        }
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

    public record Skill(String id, String displayName, List<String> aliases, boolean active, String version, String source) { }
}
