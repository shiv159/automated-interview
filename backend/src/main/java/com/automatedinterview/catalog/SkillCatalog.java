package com.automatedinterview.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SkillCatalog {
    private SkillCatalog() { }

    public static List<String> matchingSkillIds(List<Skill> skills, String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Skill skill : skills.stream().filter(Skill::active).toList()) {
            boolean found = skill.aliases().stream().sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .anyMatch(alias -> containsAlias(lower, alias));
            if (found) matches.add(skill.id());
        }
        return matches;
    }

    public static List<String> validateSecondarySkills(List<Skill> skills, String primarySkill, List<String> secondarySkills) {
        Set<String> known = skills.stream().filter(Skill::active).map(Skill::id).collect(java.util.stream.Collectors.toSet());
        if (secondarySkills == null) return List.of();
        Set<String> distinct = new java.util.LinkedHashSet<>(secondarySkills);
        if (distinct.size() != secondarySkills.size() || distinct.contains(primarySkill)
                || distinct.stream().anyMatch(id -> !known.contains(id)))
            throw new IllegalArgumentException("Invalid secondary skills");
        return List.copyOf(distinct);
    }

    public static void validate(List<Skill> skills) {
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
