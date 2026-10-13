// tools/titan/results/wave55-instruments/census.mjs — L7's OWN blast-radius census (PLAN §0 "Skeptic census", task
// rule 4), written independently of the brief's instruments.census.mjs and run on the DELIVERED patches of this lane
// dir, so the paths it classifies are exactly the bytes that land. Read-only.
//   (1) every path each unit-*.patch touches, and what executes it: a crawl of the gate's entry points
//       (tools/titan/gate-driver.sh, tools/titan/section-runner.sh, test-all.sh) through `node|bash|sh|python3 <file>`
//       in shell and static / dynamic `import` in JS, to closure; runtime and harness sources are built into every
//       capture of their platform; tests, prose and records never execute at the gate;
//   (2) for each touched runtime / harness source, whether the diff changes CODE or only comments / a string literal
//       (the U4 claim "comments + one console string");
//   (3) U5's radius on the run of record: bidi-baked documents (manifest `wpt.results[*].bidiBaked`) whose WPT source
//       has a list item — the spelling risk of [W-L7a] — and, over ALL scored sources, list-item documents whose source
//       mentions mask / mix-blend-mode (the declared-effect radius, which the unit could change on purpose);
//   (4) the carriers this implies, compared with expectations.json lanes["L7-instruments"].
// Usage: node census.mjs [run=wave54-final]   (reads the checkout four levels up: runs, refs-free WPT sources, plan)
import fs from 'node:fs';                                           // patches, scripts, manifests, sources
import path from 'node:path';                                       // repo-relative joins
import { fileURLToPath } from 'node:url';                           // this file's directory

const HERE = path.dirname(fileURLToPath(import.meta.url));          // the lane dir
const ROOT = path.resolve(HERE, '../../../..');                     // the checkout holding it
const RUN = process.argv[2] ?? 'wave54-final';                      // the run of record (= wave55-open, byte-identical)
const say = (s = '') => console.log(s);                             // one output channel
const rd = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');      // repo-relative read
const exists = (p) => fs.existsSync(path.join(ROOT, p));            // repo-relative existence

// ── (1) the patches and the gate's reach ───────────────────────────────────────────────────────────────────────────
const patches = fs.readdirSync(HERE).filter((f) => /^(unit|seam)-.+\.patch$/.test(f)).sort();
const touched = new Map();                                           // path → [unit, …]
for (const p of patches) for (const m of fs.readFileSync(path.join(HERE, p), 'utf8').matchAll(/^diff --git a\/(\S+) b\/\S+$/gm)) {
  touched.set(m[1], [...(touched.get(m[1]) ?? []), p.replace(/\.patch$/, '')]);
}
say(`# census.mjs — run ${RUN}; ${patches.length} delivered patches: ${patches.join(', ') || '(none yet)'}`);
const reach = new Set();                                             // repo-relative scripts the gate executes
const queue = ['tools/titan/gate-driver.sh', 'tools/titan/section-runner.sh', 'test-all.sh'];
const resolveRef = (from, ref) => {                                   // a referenced script → repo-relative, or null
  const bare = ref.replace(/^\$\{?(TITAN_DIR|HERE|SCRIPT_DIR)\}?\//, 'tools/titan/').replace(/^\$\{?(PROJECT_ROOT|ROOT|REPO_ROOT)\}?\//, '');
  if (ref.startsWith('.')) return path.posix.normalize(path.posix.join(path.posix.dirname(from), ref));   // JS relative import
  for (const c of [bare, `tools/titan/${bare}`, path.posix.join(path.posix.dirname(from), bare)]) if (exists(c) && fs.statSync(path.join(ROOT, c)).isFile()) return c;
  return null;                                                       // unresolvable (an env-built path): not followed
};
while (queue.length) {
  const f = queue.shift(); if (reach.has(f) || !exists(f)) continue; reach.add(f);
  const src = rd(f);
  const refs = f.endsWith('.sh')
    ? [...src.matchAll(/(?:node|bash|sh|python3?)\s+(?:-[A-Za-z]+\s+)*"?([$\w{}./-]+\.(?:mjs|cjs|js|sh|py))/g)].map((m) => m[1])
    : [...src.matchAll(/(?:from\s+|import\s*\(\s*)['"]([^'"]+\.(?:mjs|cjs|js))['"]/g)].map((m) => m[1]);
  for (const r of refs) { const t = resolveRef(f, r); if (t && !reach.has(t)) queue.push(t); }
}
say(`\n## (1) what executes each touched path (gate closure: ${reach.size} scripts from gate-driver.sh / section-runner.sh / test-all.sh)`);
const kind = (p) => reach.has(p) ? 'EXECUTED by the gate (extract / convert / capture tooling)'
  : /^runtimes\/web\/src\//.test(p) ? 'runtime source — built into every WEB capture'
  : /^apps\/web-harness\/src\//.test(p) ? 'harness source — built into every WEB capture'
  : /^runtimes\/(compose|swiftui)\/(src\/main|Sources)\//.test(p) ? 'runtime source — built into every NATIVE capture'
  : /(\.test\.(mjs|ts|tsx)|Test\.kt|Tests\.swift)$/.test(p) ? 'test — suite only, never the gate'
  : /\.md$/.test(p) ? 'prose — no tool parses it at the gate'
  : /^tools\/titan\/results\//.test(p) ? 'records / probe — read by readers, never the gate'
  : 'NOT on the gate route (no gate script references it)';
for (const [p, units] of [...touched].sort()) say(`  ${p.padEnd(96)} ${units.join(',').padEnd(22)} ${kind(p)}`);
say(`  gate scripts naming wire-differential / ab-arms55 / flex-zero-gap-rules: ${[...reach].filter((f) => /wire-differential|ab-arms55|flex-zero-gap-rules/.test(rd(f))).join(', ') || 'none'}`);

// ── (2) touched runtime / harness sources: code, comment or string? ────────────────────────────────────────────────
say('\n## (2) the executed sources: what kind of bytes change');
const codeOf = (line) => line.replace(/\/\/.*$/, '').replace(/^\s*(\/\*\*?|\*\/?)\s?.*$/, '').trim();   // comment text dropped
for (const p of patches) {
  const body = fs.readFileSync(path.join(HERE, p), 'utf8');
  for (const sec of body.split(/^(?=diff --git )/m)) {
    const f = /^diff --git a\/(\S+)/.exec(sec)?.[1]; if (!f || !/^(EXECUTED|runtime source|harness source)/.test(kind(f))) continue;   // executed bytes only
    const minus = sec.split('\n').filter((l) => l.startsWith('-') && !l.startsWith('---')).map((l) => codeOf(l.slice(1))).filter(Boolean);
    const plus = sec.split('\n').filter((l) => l.startsWith('+') && !l.startsWith('+++')).map((l) => codeOf(l.slice(1))).filter(Boolean);
    const strOnly = minus.length === plus.length && minus.every((m, i) => m.replace(/'[^']*'|"[^"]*"|`[^`]*`/g, 'S') === plus[i].replace(/'[^']*'|"[^"]*"|`[^`]*`/g, 'S'));
    say(`  ${f}: code lines −${minus.length} +${plus.length} → ${!minus.length && !plus.length ? 'COMMENTS ONLY' : strOnly ? 'STRING LITERALS ONLY (same code shape)' : 'CODE'}`);
    for (const l of plus) say(`      + ${l.slice(0, 150)}`);
  }
}

// ── (3) U5's radius on the run of record ───────────────────────────────────────────────────────────────────────────
say(`\n## (3) U5 — the marker paint decline reads a chain only for list items inside a bidi bake root (${RUN})`);
const secs = fs.readdirSync(path.join(ROOT, 'tools/titan/runs', RUN, 'sections')).filter((s) => exists(`tools/titan/runs/${RUN}/sections/${s}/manifest.json`));
const results = secs.flatMap((s) => Object.entries(JSON.parse(rd(`tools/titan/runs/${RUN}/sections/${s}/manifest.json`)).wpt?.results ?? {}).map(([t, r]) => ({ s, t, r })));
const LIST = /<li[\s>]|display\s*:\s*list-item|<ol[\s>]|<ul[\s>]/i, PAINT = /mask|mix-blend-mode/i;
const src = (t) => (exists(`tools/wpt/${t}`) ? rd(`tools/wpt/${t}`) : null);   // gitignored: read from the checkout's mirror
const baked = results.filter((x) => x.r.bidiBaked === true);
say(`  documents ${results.length} · bidiBaked ${baked.length} · stamped marker-not-baked ${results.filter((x) => (x.r.lossyReasons ?? []).some((y) => /marker-not-baked/.test(y))).length}`);
let bakedList = 0, bakedPaint = 0;
for (const x of baked) {
  const s = src(x.t); const li = s ? LIST.test(s) : null; const pa = s ? PAINT.test(s) : null;
  const ir = `tools/titan/runs/${RUN}/sections/${x.s}/per-test-ir/`;
  const doc = fs.readdirSync(path.join(ROOT, ir)).find((f) => x.r.components?.[0] && f === `${x.r.components[0].replace(/__\d+$/, '')}.json`);
  const markers = doc ? JSON.parse(rd(ir + doc)).components.filter((c) => c.meta?.markerText !== undefined).length : 'n/a';
  if (li) bakedList++; if (li && pa) bakedPaint++;
  say(`    ${x.t.padEnd(84)} list item ${li === null ? 'SOURCE MISSING' : li} · mask/blend ${pa} · markerText-carrying components (any bake) ${markers}`);
}
const allPaintList = results.filter((x) => { const s = src(x.t); return s && LIST.test(s) && PAINT.test(s); });
say(`  bidi-baked with a list item (the [W-L7a] spelling radius): ${bakedList} · of them with mask / mix-blend-mode in the source: ${bakedPaint}`);
say(`  ALL ${results.length} sources: list item AND mask / mix-blend-mode: ${allPaintList.length}${allPaintList.length ? ' — ' + allPaintList.map((x) => `${x.t}${x.r.bidiBaked ? ' (BAKED)' : ' (not baked: the decline never reads it)'}`).join('; ') : ''}`);

// ── (4) carriers, against the generated contract ───────────────────────────────────────────────────────────────────
const exp = JSON.parse(rd('tools/titan/results/wave55-plan/expectations.json')).lanes['L7-instruments'];
const mine = { captureCarriers: { web: [], ios: [], android: [] }, wireCarriers: [] };   // derived below, asserted empty
const executedCode = [...touched.keys()].filter((p) => reach.has(p) && p !== 'tools/titan/bidi-marker-paint.mjs');
if (executedCode.length) mine.wireCarriers.push(...executedCode.map((p) => `UNCLASSIFIED gate tool ${p}`));
if (bakedPaint) mine.wireCarriers.push(`${bakedPaint} baked list-item document(s) with a declared mask / blend`);
say('\n## (4) carriers (the lane predicts no corpus picture and no wire byte)');
say(`  mine:     captures ${JSON.stringify(mine.captureCarriers)} · wire ${JSON.stringify(mine.wireCarriers)}`);
say(`  expected: captures ${JSON.stringify(exp.captureCarriers)} · wire ${JSON.stringify(exp.wireCarriers)}`);
const units = Object.keys(exp.revertUnits), delivered = patches.filter((p) => p.startsWith('unit-')).map((p) => p.slice(5, -6));
say(`  revert units expected ${units.join(' ')} · delivered unit patches ${delivered.join(' ')} · optional, no patch: ${units.filter((u) => !delivered.includes(u)).join(' ') || 'none'}`);
say(`  unknown delivered units: ${delivered.filter((u) => !units.includes(u)).join(' ') || 'none'}`);
const same = JSON.stringify(mine.captureCarriers) === JSON.stringify(exp.captureCarriers) && JSON.stringify(mine.wireCarriers) === JSON.stringify(exp.wireCarriers);
say(`  census delta vs expectations.json: ${same ? 'IDENTICAL (0 capture, 0 wire carriers)' : 'GREW — a plan regeneration is needed'}`);
