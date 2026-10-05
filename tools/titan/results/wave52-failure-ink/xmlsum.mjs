// Wave 52 (lane L3 · failure-ink, fix pass) — lane tool, edits no project file.
// Summarise a JUnit XML dir: per-suite tests/failures/errors + failing test names.
import { readdirSync, readFileSync } from 'node:fs';
const d = process.argv[2]; let t = 0, f = 0, e = 0; const out = [];
for (const x of readdirSync(d).filter(x => x.endsWith('.xml'))) {
  const c = readFileSync(`${d}/${x}`, 'utf8');
  const m = c.match(/<testsuite name="([^"]+)" tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"/);
  if (!m) continue; t += +m[2]; f += +m[4]; e += +m[5]; out.push(`${m[1].split('.').pop()} ${m[2]}/${m[4]}/${m[5]}`);
  for (const k of c.matchAll(/<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]{0,160})/g)) out.push(`   FAILED: ${k[1]} — ${k[2]}`);
}
console.log(out.join('\n')); console.log(`TOTAL tests ${t} failures ${f} errors ${e}`);
