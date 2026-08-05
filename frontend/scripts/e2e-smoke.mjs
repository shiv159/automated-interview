const base = process.env.BASE_URL ?? 'http://127.0.0.1:4200';
const checks = [
  ['/api/health', 'health'],
  ['/', 'candidate page'],
  ['/question-bank', 'owner page']
];

for (const [path, label] of checks) {
  const response = await fetch(`${base}${path}`);
  if (!response.ok) throw new Error(`${label} returned HTTP ${response.status}`);
  console.log(`${label}: ${response.status}`);
}

const health = await (await fetch(`${base}/api/health`)).json();
if (health.status !== 'ok') throw new Error('health payload is not ok');
console.log('provider-free smoke: PASS');
