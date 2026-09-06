import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

import path from 'node:path';

function getCodeContents(dir) {
  let content = '';
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      content += getCodeContents(fullPath);
    } else if (entry.name.endsWith('.ts') || entry.name.endsWith('.html')) {
      content += fs.readFileSync(fullPath, 'utf8') + '\n';
    }
  }
  return content;
}

const template = getCodeContents('src');
const styles = fs.readFileSync('src/styles.css', 'utf8');
const reportStyles = fs.readFileSync('src/app/components/report/report.component.scss', 'utf8');
const nginx = fs.readFileSync('nginx.conf', 'utf8');
const frontendCode = getCodeContents('src');
const sessionServiceCode = fs.readFileSync('../backend/src/main/java/com/automatedinterview/session/SessionService.java', 'utf8');
const vectorSyncCode = fs.readFileSync('../backend/src/main/java/com/automatedinterview/ai/VectorSyncService.java', 'utf8');

test('candidate and owner journeys expose required routes and actions', () => {
  for (const value of ['/question-bank', '/sessions/', '/interview', '/report']) {
    assert.match(template, new RegExp(value.replace('/', '\\/')));
  }
  for (const value of ['downloadReport', 'printReport', 'deleteSession', 'importQuestions', 'toggleStatus']) {
    assert.match(template, new RegExp(value));
  }
});

test('question-bank exposes asynchronous indexing status without polling', () => {
  const questionBankService = fs.readFileSync('src/app/services/question-bank.service.ts', 'utf8');
  const questionBankComponent = fs.readFileSync('src/app/components/question-bank/question-bank.component.ts', 'utf8');
  assert.match(questionBankService, /indexingStatus: "PENDING" \| "PROCESSING" \| "INDEXED" \| "FAILED"/);
  assert.match(questionBankComponent, /indexingLabel/);
  assert.match(template, /indexingStatus/);
  assert.doesNotMatch(questionBankComponent, /setInterval|setTimeout/);
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

test('deployed feature fixes are represented in the frontend contract usage', () => {
  assert.match(template, /roleTitle/);
  assert.match(template, /totalQuestions/);
  assert.match(template, /completionPercent/);
  assert.match(template, /primarySkill \?\? ["']BEHAVIORAL["']/);
  assert.match(template, /idealAnswer/);
  assert.match(template, /interviewScore \| number:'1\.0-1' }}(?:<span>)?\/100/);
  assert.match(template, /api\/v1\/documents\/preview/);
  assert.doesNotMatch(template, /JAVA FULL-STACK ENGINEER/);
  assert.doesNotMatch(template, /66% complete/);
  assert.doesNotMatch(styles, /width:\s*66%/);
});

test('candidate review exposes job, resume-only, and unsupported skill evidence', () => {
  assert.match(template, /additionalClaims/);
  assert.match(template, /resumeSkills/);
  assert.match(template, /unsupportedJobSkills/);
});

test('analysis is a separate focused view and action buttons share spacing', () => {
  assert.match(template, /components\/analysis\/analysis\.component/);
  const candidateReview = fs.readFileSync('src/app/components/candidate-review/candidate-review.component.html', 'utf8');
  assert.match(candidateReview, /Start candidate review/);
  assert.match(styles, /\.primary-button, button\[type=submit\]/);
  assert.match(styles, /\.primary-button[^}]*padding:\s*10px 16px/);
});

test('known API failures have actionable frontend messages', () => {
  for (const code of ['ATTESTATION_REQUIRED', 'NO_SUPPORTED_SKILLS', 'INVALID_ANSWER', 'AI_PROVIDER_UNAVAILABLE', 'SKILL_ANALYSIS_UNCERTAIN', 'SKILL_ANALYSIS_INVALID', 'SKILL_EVIDENCE_INVALID', 'REPORT_NOT_READY']) {
    assert.match(template, new RegExp(`${code}\\s*:`));
  }
  assert.match(template, /errors/);
  assert.match(template, /item\.hint/);
});

test('report exposes score breakdown and criterion scores', () => {
  assert.match(template, /technicalScore/);
  assert.match(template, /behavioralScore/);
  assert.match(template, /criteriaScores/);
  assert.match(template, /30%|0\.3/);
  assert.match(template, /80%|0\.8/);
});

test('experience input is constrained to whole years and progress tracks completed questions', () => {
  assert.match(template, /name="years"[^>]*step="1"/);
  assert.match(template, /completedQuestions/);
});

test('report export includes JSON and CSV output with print rules', () => {
  assert.match(template, /Download JSON \+ CSV/);
  assert.match(template, /text\/csv/);
  assert.match(reportStyles, /@media print/);
});

test('application code uses explicit frontend types and shared backend JSON handling', () => {
  assert.doesNotMatch(frontendCode, /\bany\b/);
  assert.doesNotMatch(sessionServiceCode, /new\s+ObjectMapper\s*\(/);
  assert.doesNotMatch(sessionServiceCode, /return\s+"\["\s*\+/);
  assert.doesNotMatch(vectorSyncCode, /new\s+com\.fasterxml\.jackson\.databind\.ObjectMapper\s*\(/);
});

test('controllers keep persistence behind application services', () => {
  const questionBankController = fs.readFileSync('../backend/src/main/java/com/automatedinterview/questionbank/QuestionBankController.java', 'utf8');
  const sessionLifecycleController = fs.readFileSync('../backend/src/main/java/com/automatedinterview/session/SessionLifecycleController.java', 'utf8');
  assert.doesNotMatch(questionBankController, /JdbcClient|\.sql\(/);
  assert.doesNotMatch(sessionLifecycleController, /JdbcClient|\.sql\(|private\s+SessionState\s+find\s*\(/);
});
