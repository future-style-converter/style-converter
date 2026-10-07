// tools/titan/results/wave53-lists-bakes/skeptic/sk-differential.mjs — the L1 SKEPTIC's own static-extraction
// differential (independent of the lane's u1-differential.sh, which imports both module copies into ONE process).
// Usage: node sk-differential.mjs <tree-root> <tests.list> <out.json>
// Runs in ONE tree per process (an export of HEAD, or HEAD + seam-1 + the lane's files) the static half of
// extract-fixture.mjs main(): extractFixture(rel) then counter-style-bake on the fixture, exactly as main() does with
// no gate flags, and writes { test: sha1(JSON.stringify([fixture, refFixture])) | 'ERR …' } for a byte compare.
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
const [root, list, out] = process.argv.slice(2);
const { extractFixture } = await import(`${root}/tools/titan/extract-fixture.mjs`);
const { bakeCounterStyles } = await import(`${root}/tools/titan/counter-style-bake.mjs`);
const tests = readFileSync(list, 'utf8').split('\n').filter(Boolean);
const res = {};
const t0 = Date.now();
for (const t of tests) {
  try {
    const r = await extractFixture(t);
    // main()'s counter-style bake on the static fixture (the AUTHORED source, as main passes it).
    bakeCounterStyles(r.fixture, readFileSync(`${root}/tools/wpt/${t}`, 'utf8'));
    res[t] = createHash('sha1').update(JSON.stringify([r.fixture, r.refFixture])).digest('hex');
  } catch (e) { res[t] = 'ERR ' + String(e?.message ?? e).slice(0, 120); }
}
writeFileSync(out, JSON.stringify(res, null, 1));
console.log(`${tests.length} tests in ${((Date.now() - t0) / 1000).toFixed(1)} s → ${out}`);
