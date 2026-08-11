import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const template = fs.readFileSync('src/main.ts', 'utf8');
const styles = fs.readFileSync('src/styles.css', 'utf8');
const nginx = fs.readFileSync('nginx.conf', 'utf8');

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

test('production proxy bounds forwarded headers and response buffers', () => {
  assert.match(nginx, /proxy_set_header X-Forwarded-For \$remote_addr/);
  assert.match(nginx, /proxy_buffer_size 16k/);
  assert.match(nginx, /proxy_buffers 4 16k/);
  assert.match(nginx, /resolver 8\.8\.8\.8 ipv6=off valid=30s/);
  assert.match(nginx, /proxy_pass \$backend_upstream/);
  assert.match(nginx, /proxy_set_header Host \$proxy_host/);
});
