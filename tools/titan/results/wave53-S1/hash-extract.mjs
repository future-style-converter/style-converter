// S1 U1 differential: sha1 of the static extraction [fixture, refFixture] per test, for one tool tree.
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
const [tree, list, out] = process.argv.slice(2);
const EF = await import(`${tree}/tools/titan/extract-fixture.mjs`);
const tests = readFileSync(list, 'utf8').split('\n').filter(Boolean);
const res = {};
const quiet = console.log; console.log = () => {}; console.warn = () => {}; console.error = () => {};
for (const t of tests) {
  try { const r = await EF.extractFixture(t); res[t] = createHash('sha1').update(JSON.stringify([r.fixture, r.refFixture ?? null])).digest('hex'); }
  catch (e) { res[t] = 'ERROR ' + String(e.message).slice(0, 100); }
}
writeFileSync(out, JSON.stringify(res, null, 1));
quiet(`done ${tests.length}`);
