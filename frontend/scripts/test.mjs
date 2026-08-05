import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const template = fs.readFileSync('src/main.ts', 'utf8');
const styles = fs.readFileSync('src/styles.css', 'utf8');

test('candidate and owner journeys expose required routes and actions', () => {
  for (const value of ['/question-bank', '/sessions/', '/interview', '/report']) {
    assert.match(template, new RegExp(value.replace('/', '\\/')));
  }
  for (const value of ['downloadReport', 'printReport', 'deleteSession', 'importQuestions', 'toggleStatus']) {
    assert.match(template, new RegExp(value));
  }
});

test('UI exposes accessible status and mobile layout rules', () => {
  assert.match(template, /aria-live="polite"/);
  assert.match(template, /aria-label="Question bank rows"/);
  assert.match(styles, /max-width: 480px/);
  assert.match(styles, /button:focus-visible/);
});
