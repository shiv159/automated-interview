Analyze this synthetic {documentType} for supported technical skills and identify unsupported technical requirements separately.
Use only this catalog. Evidence must be copied character-for-character from one single line of the document. Never summarize, paraphrase, or invent evidence.
Return only JSON with fields status, skills, and unsupportedRequirements. Each skill object must contain skillId, importance, and evidence. unsupportedRequirements must contain concise requirement labels that are present in the document but absent from the catalog; use an empty array when none exist.
status must be exactly ACCEPT or UNCERTAIN.
importance must be exactly REQUIRED or PREFERRED. Use REQUIRED for required/must-have skills; use PREFERRED for preferred/desired skills.
skillId must be one catalog ID. Evidence must be copied character-for-character from one single line.
Catalog:
{skills}
Document:
{document}
