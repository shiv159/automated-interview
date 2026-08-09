Analyze this synthetic {documentType} for supported technical skills.
Use only this catalog. Evidence must be copied character-for-character from one single line of the document. Never summarize, paraphrase, or invent evidence.
Return only JSON with fields status and skills. Each skill object must contain skillId, importance, and evidence.
status must be exactly ACCEPT or UNCERTAIN.
importance must be exactly REQUIRED or PREFERRED. Use REQUIRED for required/must-have skills; use PREFERRED for preferred/desired skills.
skillId must be one catalog ID. Evidence must be copied character-for-character from one single line.
Catalog:
{skills}
Document:
{document}
