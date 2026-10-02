import { Session, SkillClaim } from "./session.service";

export type SkillGroup = "matched" | "missing" | "additional";

export function skillClaims(
  session: Session | null,
  group: SkillGroup,
): SkillClaim[] {
  if (!session) return [];

  if (group === "matched") {
    return session.jobSkills.filter((claim) =>
      session.matchedSkills.includes(claim.skillId),
    );
  }
  if (group === "missing") {
    return session.jobSkills.filter((claim) =>
      session.missingSkills.includes(claim.skillId),
    );
  }

  const jobSkillIds = new Set(session.jobSkills.map((claim) => claim.skillId));
  return session.resumeSkills.filter((claim) => !jobSkillIds.has(claim.skillId));
}
