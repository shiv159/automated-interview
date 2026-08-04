import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
test('contract artifacts exist', () => {
  for (const file of ['openapi.yaml', 'question-bank.schema.json', 'question-enrichment.schema.json']) assert.equal(fs.existsSync(file), true, file);
});
test('enrichment schema is closed', () => {
  const schema = JSON.parse(fs.readFileSync('question-enrichment.schema.json', 'utf8'));
  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(schema.properties.type.enum, ['TECHNICAL', 'BEHAVIORAL']);
});

