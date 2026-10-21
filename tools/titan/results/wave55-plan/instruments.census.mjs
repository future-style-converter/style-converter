#!/usr/bin/env node
// tools/titan/results/wave55-plan/instruments.census.mjs — the carrier census of lane L7-instruments (brief
// instruments.md §5). Read-only: it reads tracked source, the wave54-final manifests / per-test IR, the frozen WPT
// sources under tools/wpt/css/ and the recorded W1 census; it builds, runs and captures nothing.
//
// What it proves (the lane predicts NO corpus picture change, so its carrier sets must come out EMPTY):
//   (1) gate reach: which owned paths the gate executes at all (gate-driver.sh → section-runner.sh → the extract /
//       convert / capture scripts and the three runtimes). A path the gate never executes carries nothing by construction.
//   (2) U5 (mask-image / mix-blend-mode in the marker paint decline, tools/titan/bidi-marker-paint.mjs) is on the
//       EXTRACT path: its radius is the set of documents whose marker bake reads a paint chain — bidi-baked documents
//       with a list item — and among those, the ones whose WPT source mentions mask / mix-blend-mode anywhere.
//   (3) U4 (the W1 "rule 1" citations, one of them a console.warn STRING in runtimes/web/src/renderer/InlineRuns.ts:174):
//       the hosts where that warn fires (the W1 joins), re-derived from wave54-final per-test IR and compared with the
//       recorded wave53-final census (tools/titan/results/wave54-web-tail/w1-joins.wave53-final.out.txt).
//   (4) the "rule 1" sites are classified comment vs string literal, and every other spec-03 §4.1 rule citation in
//       the tree is listed (the latitude paragraph must renumber none of them).
// Usage: node tools/titan/results/wave55-plan/instruments.census.mjs [run=wave54-final]
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const RUN = process.argv[2] ?? 'wave54-final';
const SECTIONS = path.join(ROOT, 'tools/titan/runs', RUN, 'sections');
const rel = (p) => path.relative(ROOT, p);
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const tracked = new Set(execFileSync('git', ['-C', ROOT, 'ls-files'], { encoding: 'utf8' }).split('\n').filter(Boolean));
const out = [];
const say = (s = '') => out.push(s);

say(`# instruments.census.mjs — run ${RUN}, HEAD ${execFileSync('git', ['-C', ROOT, 'rev-parse', '--short', 'HEAD'], { encoding: 'utf8' }).trim()}`);

// ── (1) gate reach ────────────────────────────────────────────────────────────────────────────────────────────────
// The gate's executed tooling: the driver, the section runner and every tools/titan/*.mjs a gate script names, closed
// over static `import … from './x.mjs'` and dynamic `import('./x.mjs')` edges. Runtimes / harnesses are executed by the capture step (all of them).
const GATE_SCRIPTS = ['tools/titan/gate-driver.sh', 'tools/titan/section-runner.sh', 'test-all.sh'];
const named = new Set();
for (const s of GATE_SCRIPTS) for (const m of read(s).matchAll(/tools\/titan\/([\w.-]+\.(?:mjs|sh))|\$TITAN_DIR\/([\w.-]+\.(?:mjs|sh))/g)) named.add(`tools/titan/${m[1] ?? m[2]}`);
const closure = new Set();
const visit = (p) => {
  if (closure.has(p) || !tracked.has(p)) return;
  closure.add(p);
  if (!p.endsWith('.mjs')) return;
  for (const m of read(p).matchAll(/(?:from\s+|import\(\s*)['"](\.{1,2}\/[^'"]+\.mjs)['"]/g)) visit(path.posix.normalize(path.posix.join(path.posix.dirname(p), m[1])));
};
for (const p of named) visit(p);
const OWNED = [
  ['tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py', 'U1 probe copy (reads PNGs only)'],
  ['tools/titan/wire-differential.sh', 'U2 NEW instrument (orchestrator window only)'],
  ['tools/titan/wire-differential.mjs', 'U2 NEW classifier'],
  ['tools/titan/wire-differential.test.mjs', 'U2 NEW pins (tooling suite)'],
  ['tools/titan/results/wave55-gate/ab-arms55.sh', 'U3 NEW A/B runner (orchestrator, after the closing gate)'],
  ['tools/titan/results/wave55-gate/ab-arms55.selftest.sh', 'U3 NEW self-test (throwaway git repo)'],
  ['schema/spec/03-children.md', 'U4 prose'],
  ['runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts', 'U4 comment line :32'],
  ['runtimes/web/src/renderer/InlineRuns.ts', 'U4 comments :52/:167 + warn string :174'],
  ['tools/titan/bidi-marker-paint.mjs', 'U5 two keys in MARKER_PAINT_INITIAL'],
  ['tools/titan/bidi-bake.test.mjs', 'U5 S6-2 effects table +2 entries'],
];
say('\n## (1) gate reach of every owned path');
say(`gate-executed tools/titan scripts (closure of ${GATE_SCRIPTS.join(', ')} + static/dynamic imports): ${closure.size}`);
for (const [p, what] of OWNED) {
  const exists = fs.existsSync(path.join(ROOT, p));
  const reach = p.startsWith('runtimes/') ? 'EXECUTED (runtime: every capture of its platform)'
    : closure.has(p) ? 'EXECUTED (gate extract/convert/capture path)'
      : p.endsWith('.test.mjs') || p.includes('.test.') ? 'suite only (never the gate)'
        : 'not executed by the gate';
  say(`  ${p.padEnd(62)} ${exists ? (tracked.has(p) ? 'tracked' : 'untracked') : 'NEW'}  ${reach}  — ${what}`);
}
// The chain that puts bidi-marker-paint.mjs on the extract path (printed so the claim is checkable).
const chainTo = (target) => {
  for (const p of named) {
    const seen = new Map([[p, null]]); const q = [p];
    while (q.length) {
      const c = q.shift();
      if (c === target) { const pathOut = []; for (let n = c; n; n = seen.get(n)) pathOut.unshift(n); return pathOut.join(' → '); }
      if (!c.endsWith('.mjs') || !tracked.has(c)) continue;
      for (const m of read(c).matchAll(/(?:from\s+|import\(\s*)['"](\.{1,2}\/[^'"]+\.mjs)['"]/g)) {
        const n = path.posix.normalize(path.posix.join(path.posix.dirname(c), m[1]));
        if (!seen.has(n)) { seen.set(n, c); q.push(n); }
      }
    }
  }
  return 'NOT REACHED';
};
say(`  import chain: ${chainTo('tools/titan/bidi-marker-paint.mjs')}`);
for (const p of ['wire-differential', 'wl3-wire-differential', 'ab-arms', 'flex-zero-gap-rules']) {
  const hits = GATE_SCRIPTS.filter((s) => read(s).includes(p));
  say(`  gate scripts naming "${p}": ${hits.length ? hits.join(', ') : 'none'}`);
}

// ── (2) U5 radius: bidi-baked documents with a list item, and their WPT sources ───────────────────────────────────
say('\n## (2) U5 radius — the marker paint chain is read only for list items inside a bidi bake root');
const docs = [];
for (const sec of fs.readdirSync(SECTIONS)) {
  const mf = path.join(SECTIONS, sec, 'manifest.json');
  if (!fs.existsSync(mf)) continue;
  const results = JSON.parse(fs.readFileSync(mf, 'utf8')).wpt?.results ?? {};
  for (const [test, r] of Object.entries(results)) docs.push({ sec, test, r });
}
const baked = docs.filter((d) => d.r.bidiBaked === true);
const stamped = docs.filter((d) => (d.r.lossyReasons ?? []).some((x) => /marker-not-baked/.test(x)));
say(`documents in ${RUN}: ${docs.length} · bidiBaked: ${baked.length} · lossyReasons marker-not-baked: ${stamped.length}`);
const PAINT = /mask(?:-image)?\s*:|-webkit-mask|mix-blend-mode|\bmask\s*:/i;
let withList = 0, withPaint = 0;
for (const d of baked) {
  const html = path.join(ROOT, 'tools/wpt', d.test);
  const src = fs.existsSync(html) ? fs.readFileSync(html, 'utf8') : null;
  const stem = `wpt__${d.sec}__${d.test.replace(/^css\/[^/]+\//, '').replace(/\.html$/, '').replace(/\//g, '__')}.json`;
  const irp = path.join(SECTIONS, d.sec, 'per-test-ir', stem);
  const ir = fs.existsSync(irp) ? JSON.parse(fs.readFileSync(irp, 'utf8')) : null;
  const comps = ir?.components ?? [];
  // Root-owned marker runs (M′): anonymous (no sourceTag) ABSOLUTE|FIXED runs whose slot parent is the <ol>/<ul> bake root.
  const byId = new Map(comps.map((c) => [c.id, c]));
  const markerRuns = comps.filter((c) => !c.meta?.sourceTag && /^(ol|ul|menu|dir)$/.test(byId.get(c.slot?.parent)?.meta?.sourceTag ?? '')
    && c.properties?.some((p) => p.type === 'Position' && /^(ABSOLUTE|FIXED)$/i.test(String(p.data))));
  const lis = comps.filter((c) => c.meta?.sourceTag === 'li').length;
  const hasList = src ? /<li[\s>]|list-item/i.test(src) : null;
  const paint = src ? (src.match(new RegExp(PAINT.source, 'gi')) ?? []) : null;
  if (hasList) withList++;
  if (paint?.length) withPaint++;
  say(`  ${d.test.padEnd(64)} li-components ${String(lis).padStart(2)} · root-owned marker runs ${markerRuns.length}${markerRuns.length ? ' [' + markerRuns.map((c) => c.text).join(' ') + ']' : ''} · source <li>/list-item ${hasList === null ? 'NO SOURCE' : hasList} · mask/mix-blend-mode ${paint === null ? '?' : paint.length ? paint.join('|') : 0}`);
}
say(`bidi-baked documents with a list item in their source: ${withList} · with mask / mix-blend-mode in their source: ${withPaint}`);
say(`=> U5 wire carriers predicted: ${withPaint === 0 ? 'NONE (no bake-root list item can meet a mask / blend; counter-suffix keeps its marker runs only if Chromium spells both initials as read)' : 'SEE ROWS ABOVE'}`);

// ── (3) U4: the hosts where the W1 warn fires ─────────────────────────────────────────────────────────────────────
say('\n## (3) U4 — W1 join hosts (the InlineRuns.ts:174 console.warn fires once per such host; web only)');
const alpha0 = (c) => c.properties?.some((p) => p.type === 'Color' && Number(p.data?.srgb?.a ?? 1) <= 0);
const oof = (c) => c.properties?.some((p) => p.type === 'Position' && /^(ABSOLUTE|FIXED)$/i.test(String(p.data)));
const autoHy = (c) => c.properties?.some((p) => p.type === 'Hyphens' && /^AUTO$/i.test(String(p.data)));
let hostsAuto = 0; const joins = [];
for (const sec of fs.readdirSync(SECTIONS)) {
  const dir = path.join(SECTIONS, sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir)) {
    const comps = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')).components ?? [];
    const byName = new Map(comps.map((c) => [c.name, c]));
    for (const h of comps) {
      const runs = h.meta?.runs; if (!Array.isArray(runs) || !autoHy(h)) continue;
      hostsAuto++;
      let n = 0;
      for (let i = 1; i < runs.length - 1; i++) {
        const m = runs[i].child != null ? byName.get(runs[i].child) : null;
        const prev = runs[i - 1].text, next = runs[i + 1].text;
        // A member that splits a WORD: text on both sides with no CSS white space at the boundary.
        if (m && oof(m) && alpha0(m) && typeof prev === 'string' && typeof next === 'string' && /\S$/.test(prev) && /^\S/.test(next)) n++;
      }
      if (n) joins.push(`${sec}/${f} ${h.id} joined ${n}`);
    }
  }
}
say(`own hyphens:auto runs hosts: ${hostsAuto} · hosts joined (approximate predicate: OOF + alpha-0 member between two non-space text runs): ${joins.length}`);
for (const j of joins) say(`  ${j}`);
const rec = path.join(ROOT, 'tools/titan/results/wave54-web-tail/w1-joins.wave53-final.out.txt');
if (fs.existsSync(rec)) {
  const recorded = fs.readFileSync(rec, 'utf8').split('\n').filter((l) => /^[\w-]+\/wpt__\S+ \S+ joined \d+$/.test(l)).map((l) => l.split(' ')[1]);
  const now = joins.map((j) => j.split(' ')[1]);
  const same = recorded.length === now.length && recorded.every((x) => now.includes(x));
  say(`recorded wave53-final census (${rel(rec)}): ${recorded.length} hosts — ${same ? 'SAME host set' : 'DIFFERENT: ' + JSON.stringify({ recorded, now })}`);
}
say('=> U4 capture carriers predicted: NONE (a console string and comments; the manifest records no console output)');

// ── (4) the citations ─────────────────────────────────────────────────────────────────────────────────────────────
say('\n## (4) spec 03 §4.1 rule citations in tracked source');
const grep = execFileSync('git', ['-C', ROOT, 'grep', '-n', '-E', '(spec 03 )?§4\\.1 rule [0-9]', '--', 'runtimes', 'apps', 'converter', 'tools/titan/*.mjs', 'schema'], { encoding: 'utf8' }).split('\n').filter(Boolean);
for (const g of grep) {
  const [file, line, ...rest] = g.split(':'); const text = rest.join(':');
  const n = (text.match(/§4\.1 rule ([0-9])/) ?? [])[1];
  const kind = /CSS Text §4\.1/.test(text) ? 'OTHER SPEC (CSS Text §4.1, not spec 03)' : file.endsWith('.md') ? 'doc' : /^\s*(\/\/|\*|\/\*)/.test(text) ? 'comment' : /['"`].*§4\.1 rule/.test(text) ? 'STRING LITERAL' : 'other';
  const w1 = /InertOutOfFlowWordJoin|InlineRuns\.ts/.test(file) && n === '1';
  say(`  ${`${file}:${line}`.padEnd(78)} rule ${n} · ${kind}${w1 ? '  ← W1 site to correct (rule 2)' : ''}`);
}
const spec = read('schema/spec/03-children.md');
const rules = [...spec.slice(spec.indexOf('**Rules, all frozen with the key:**'), spec.indexOf('## 5.')).matchAll(/^(\d)\. \*\*([^*]+)\*\*/gm)].map((m) => `${m[1]} ${m[2]}`);
say(`spec 03 §4.1 numbered rules today: ${rules.join(' · ')}`);

// ── carriers ──────────────────────────────────────────────────────────────────────────────────────────────────────
say('\n## carriers (the lane predicts no corpus picture change)');
say('capture carriers: web {} · ios {} · android {}');
say('wire carriers: {} (U5 is the only owned path on the extract route; its predicted wire diff is empty — proven at its orchestrator window by the U2 differential)');
fs.writeFileSync(path.join(HERE, 'instruments.census.out.txt'), out.join('\n') + '\n');
console.log(out.join('\n'));
