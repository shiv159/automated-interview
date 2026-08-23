import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
test('contract artifacts exist', () => {
  for (const file of ['openapi.yaml', 'question-bank.schema.json', 'question-enrichment.schema.json', 'question-import.schema.json', 'question-bank.json']) assert.equal(fs.existsSync(file), true, file);
});
test('cross-field enrichment fixtures obey technical and behavioral shape', () => {
  const root = path.join('..', 'fixtures', 'question-imports', 'cross-field');
  const accepted = JSON.parse(fs.readFileSync(path.join(root, 'technical-accept.json'), 'utf8'));
  const behavioral = JSON.parse(fs.readFileSync(path.join(root, 'behavioral-accept.json'), 'utf8'));
  const invalid = JSON.parse(fs.readFileSync(path.join(root, 'behavioral-with-skill.json'), 'utf8'));
  assert.equal(accepted.type, 'TECHNICAL');
  assert.ok(accepted.primarySkill && accepted.difficulty);
  assert.equal(behavioral.type, 'BEHAVIORAL');
  assert.equal(behavioral.primarySkill, null);
  assert.equal(behavioral.difficulty, null);
  assert.notEqual(invalid.primarySkill, null);
});
test('enrichment schema is closed', () => {
  const schema = JSON.parse(fs.readFileSync('question-enrichment.schema.json', 'utf8'));
  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(schema.properties.type.enum, ['TECHNICAL', 'BEHAVIORAL']);
});

test('API contract includes implemented feature responses', () => {
  const openapi = fs.readFileSync('openapi.yaml', 'utf8');
  assert.match(openapi, /roleTitle/);
  assert.match(openapi, /\/api\/v1\/documents\/preview/);
  assert.match(openapi, /QuestionBankResponse/);
  assert.match(openapi, /ReportResponse/);
  assert.match(openapi, /aggregate scores are 0-100/);
});
