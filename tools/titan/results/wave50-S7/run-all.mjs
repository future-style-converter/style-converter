// S7 — re-runs every replay in this directory and writes replays.json.
// Usage: S7_SCRATCH=<tmpdir> node tools/titan/results/wave50-S7/run-all.mjs
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPTS = ['replay-B1.mjs','replay-B7a.mjs','replay-B7a-sensitivity.mjs','replay-B7b.mjs',
  'replay-B7c.mjs','replay-B7c-twin.mjs','replay-B4.mjs','replay-B3.mjs','replay-B2.mjs',
  'replay-B9.mjs','replay-B11.mjs','replay-B11-pairs.mjs','replay-misc.mjs','replay-gaps.mjs'];
const out = { _note: 'wave-50 skeptic S7: raw stdout of every replay, re-runnable with this script.', results: {} };
for (const s of SCRIPTS) {
  const t0 = Date.now();
  let stdout, err = null;
  try { stdout = execFileSync(process.execPath, [join(HERE, s)], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }); }
  catch (e) { stdout = String(e.stdout ?? ''); err = String(e.message); }
  let parsed = null; try { parsed = JSON.parse(stdout); } catch {}
  out.results[s] = { ms: Date.now() - t0, error: err, json: parsed, text: parsed ? undefined : stdout };
  console.error(`${s}: ${err ? 'ERROR' : 'ok'} (${Date.now() - t0} ms)`);
}
writeFileSync(join(HERE, 'replays.json'), JSON.stringify(out, null, 1));
