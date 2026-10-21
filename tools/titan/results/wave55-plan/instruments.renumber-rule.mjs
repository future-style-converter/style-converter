// tools/titan/results/wave55-plan/instruments.renumber-rule.mjs — wave-55 L7 (brief instruments.md §2.2, §4 U2): the
// RENUMBERED class as the wave-53 rule defines it (wave54-plan/expectations.json wireRenumbering.rule: identical once the
// id counter is stripped, EVERY id / slot.parent shifted by the SAME amount, equal to the running component-count delta
// of the content-changed documents before it in the section) — measured on the parked wave-54 [W-L3] trees
// (tools/titan/runs/wave54-wl3-diff, gitignored) in tests.list order. The wave-54 classifier only strips the suffix, so
// a document with NON-uniform or wrong shifts would also print RENUMBERED; this read shows what the stricter rule
// returns on real data (it must accept exactly the 15 recorded documents). Read-only.
import fs from 'node:fs';
import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const T = path.join(ROOT, 'tools/titan/runs/wave54-wl3-diff');
const RUN = path.join(ROOT, 'tools/titan/runs/wave54-open/sections');
const ids = (doc) => doc.components.flatMap((c) => [c.id, c.slot?.parent].filter(Boolean));
const num = (s) => Number(/-(\d+)$/.exec(s)?.[1]);
const strip = (s) => s.replace(/("(?:id|parent)":\s*"[^"]*?)-\d+"/g, '$1"');
let total = { renumbered: 0, uniformOk: 0, bad: [], visited: 0, files: 0 };
for (const sec of fs.readdirSync(path.join(T, 'pre'))) {
  const list = fs.readFileSync(path.join(RUN, sec, 'tests.list'), 'utf8').split('\n').filter(Boolean);
  let delta = 0;
  total.files += fs.readdirSync(path.join(T, 'pre', sec, 'per-test-ir')).length;
  for (const t of list) {
    const f = `wpt__${sec}__${t.replace(/^css\/[^/]+\//, '').replace(/^[^/]+\//, (m) => (t.startsWith('css/') ? m : '')).replace(/\.html$/, '').replace(/\//g, '__')}.json`;
    const a = path.join(T, 'pre', sec, 'per-test-ir', f), b = path.join(T, 'post', sec, 'per-test-ir', f);
    if (!fs.existsSync(a) || !fs.existsSync(b)) continue;
    total.visited++;
    const x = fs.readFileSync(a, 'utf8'), y = fs.readFileSync(b, 'utf8');
    if (x === y) continue;
    const X = JSON.parse(x), Y = JSON.parse(y);
    if (strip(x) === strip(y)) {
      total.renumbered++;
      const shifts = new Set(ids(X).map((s, i) => num(ids(Y)[i]) - num(s)));
      const ok = shifts.size === 1 && [...shifts][0] === delta;
      if (ok) total.uniformOk++; else total.bad.push(`${sec}/${f} shifts ${[...shifts]} vs running delta ${delta}`);
    } else {
      delta += Y.components.length - X.components.length;   // a content-changed document moves the counter for the rest
    }
  }
}
console.log(`documents visited in tests.list order ${total.visited} of ${total.files} per-test-ir files`);
console.log(`renumbered (suffix-strip, the wave-54 class) ${total.renumbered} · of them uniform shift == running delta ${total.uniformOk}`);
for (const b of total.bad) console.log(`  NOT the wave-53 class: ${b}`);
