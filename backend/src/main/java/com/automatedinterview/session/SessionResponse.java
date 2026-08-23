package com.automatedinterview.session;

import com.automatedinterview.analysis.SkillClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
    UUID id,
    Instant expiresAt,
    String roleTitle,
    String difficulty,
    double profileMatch,
    List<SkillClaim> jobSkills,
    List<SkillClaim> resumeSkills,
    List<String> matchedSkills,
    List<String> missingSkills) { }
