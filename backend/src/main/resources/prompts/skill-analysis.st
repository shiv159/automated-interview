Analyze this synthetic {documentType} for supported technical skills and categorize other requirements.
Use only this catalog. Evidence must be copied character-for-character from one single line of the document. Never summarize, paraphrase, or invent evidence.
Return only JSON with fields status, skills, unsupportedRequirements, softSkillRequirements, and domainRequirements. Put only concise unsupported technical technologies in unsupportedRequirements. Put communication, ownership, adaptability, problem-solving, and similar qualities in softSkillRequirements. Put FinTech, banking, and industry terms in domainRequirements. Do not include responsibilities, company context, or sentence fragments. Use empty arrays when there are none.
status must be exactly ACCEPT or UNCERTAIN.
importance must be exactly REQUIRED or PREFERRED. Use REQUIRED for required/must-have skills; use PREFERRED for preferred/desired skills.
skillId must be one catalog ID. Evidence must be copied character-for-character from one single line.
Catalog:
{skills}
Document:
{document}
