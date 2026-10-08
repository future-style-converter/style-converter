// tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.census.mjs — wave-54 planning census (read-only).
//
// Family: web-out-of-flow-hyphen-box (BACKLOG obligation 0(c)). The web runtime renders `meta.runs` as one DOM
// text piece per `{text}` entry (the harness renderText hook wraps each in `<span>`), so a `{child}` entry sitting
// INSIDE a word splits that word into two inline text items. Measured mechanism (brief §4): the hyphenator then sees
// only the fragments, and a hyphenation point that falls exactly on the split is lost (hyphens-out-of-flow-002
// boxes 4 and 5). This census answers, over every per-test IR document of the gate of record:
//   A. every runs host with an OUT-OF-FLOW member (Position ABSOLUTE|FIXED, or a non-NONE Float) — where it sits
//      (mid-word / word-edge / spaced), the host's effective `hyphens`, whether the member is paint-inert, and the
//      host test's three cells;
//   B. every runs host (any child kind) whose child sits MID-WORD under an effective `hyphens: auto` — the wider
//      population the fragment mechanism could reach;
//   C. the reach of the two candidate fix shapes: W1 (join the word around a mid-word paint-inert out-of-flow member)
//      and W2 (bare text nodes for runs instead of the harness `<span>` wrapper — every runs host on the web).
// Usage: node tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.census.mjs [run-id]   (default wave53-final)
// Writes web-out-of-flow-hyphen-box.census.json beside itself and prints the summary.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RUN_ID = process.argv[2] ?? 'wave53-final';
const runDir = resolveRunDir(RUN_ID);
const run = loadRun(runDir);

// Scored cells keyed by test key (css/<sec>/<path>.html) → {web, ios, android} as "P 0.99" / "f 0.94".
const cellsOf = (testKey) => {
  const out = {};
  for (const s of Object.values(run.sections)) for (const p of ['web', 'ios', 'android']) {
    const c = s.cells.get(`${testKey}|${p}`);
    if (c) out[p] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  }
  return out;
};

// The member is paint-inert exactly when the Compose twin admits it (wave-53 L2 F2,
// runtimes/compose/.../typography/inline/InertOutOfFlowMember.kt `admits`): no children / runs / decorations, at
// least one Position ABSOLUTE|FIXED, a Color whose sRGB alpha is 0, and NO other property type except Hyphens.
const isOutOfFlowPos = (p) => p.type === 'Position' && (p.data === 'ABSOLUTE' || p.data === 'FIXED');
const isFloat = (p) => p.type === 'Float' && typeof p.data === 'string' && p.data !== 'NONE';
const transparent = (p) => p.type === 'Color' && p.data && p.data.srgb && p.data.srgb.a === 0;
const paintInert = (c, kids) =>
  kids.length === 0 && !(c.meta && (c.meta.runs || c.meta.decorations)) &&
  c.properties.some(isOutOfFlowPos) && c.properties.some(transparent) &&
  c.properties.every((p) => isOutOfFlowPos(p) || transparent(p) || p.type === 'Hyphens');

const A = [], B = [];
let docs = 0, runsHosts = 0, textEntries = 0;
const runsTests = new Set();
for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  const sdir = path.join(runDir, 'sections', sec);
  const mf = path.join(sdir, 'manifest.json');
  const irDir = path.join(sdir, 'per-test-ir');
  if (!fs.existsSync(mf) || !fs.existsSync(irDir)) continue;
  const results = JSON.parse(fs.readFileSync(mf, 'utf8')).wpt?.results ?? {};
  // per-test-ir file stem → manifest test key.
  const keyOf = new Map(Object.keys(results).map((k) => {
    const rest = k.replace(/^css\//, '').replace(/\.html?$/, '');
    return [`wpt__${rest.replace(/\//g, '__')}`, k];
  }));
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    if (!Array.isArray(doc.components)) continue;
    docs++;
    const testKey = keyOf.get(f.replace(/\.json$/, '')) ?? `?${sec}/${f}`;
    const byId = new Map(doc.components.map((c) => [c.id, c]));
    const byName = new Map(doc.components.map((c) => [c.name, c]));
    const kidsOf = new Map();
    for (const c of doc.components) {
      const par = c.slot?.parent;
      if (par) { if (!kidsOf.has(par)) kidsOf.set(par, []); kidsOf.get(par).push(c); }
    }
    // Effective `hyphens`: own, else the nearest ancestor's (inherited property, css-text-3 §5.4); initial MANUAL.
    const hyphensOf = (c) => {
      for (let cur = c, guard = 0; cur && guard < 64; cur = byId.get(cur.slot?.parent) ?? byName.get(cur.slot?.parent), guard++) {
        const h = cur.properties.find((p) => p.type === 'Hyphens');
        if (h) return h.data;
      }
      return 'MANUAL(initial)';
    };
    for (const host of doc.components) {
      const runs = host.meta?.runs;
      if (!Array.isArray(runs) || runs.length === 0) continue;
      runsHosts++; runsTests.add(testKey);
      textEntries += runs.filter((r) => typeof r.text === 'string' && r.text.length > 0).length;
      const hy = hyphensOf(host);
      runs.forEach((r, i) => {
        if (!r.child) return;
        const child = byName.get(r.child) ?? byId.get(r.child);
        if (!child) return;
        const prev = typeof runs[i - 1]?.text === 'string' ? runs[i - 1].text : null;
        const next = typeof runs[i + 1]?.text === 'string' ? runs[i + 1].text : null;
        const glueL = prev !== null && /\S$/.test(prev);
        const glueR = next !== null && /^\S/.test(next);
        const where = glueL && glueR ? 'mid-word' : (glueL || glueR) ? 'word-edge' : 'spaced';
        const oof = child.properties.some(isOutOfFlowPos) ? 'abspos' : child.properties.some(isFloat) ? 'float' : null;
        const kids = kidsOf.get(child.id) ?? kidsOf.get(child.name) ?? [];
        const row = {
          test: testKey, host: host.id, entry: i, where, hyphens: hy, lang: host.meta?.lang ?? null,
          split: [prev, `<${child.meta?.sourceTag ?? '?'}${oof ? ' ' + oof : ''}>`, next],
          member: { id: child.id, tag: child.meta?.sourceTag ?? null, props: child.properties.map((p) => p.type), paintInert: oof === 'abspos' && paintInert(child, kids) },
          cells: cellsOf(testKey),
        };
        if (oof) A.push({ ...row, kind: oof });
        if (where === 'mid-word' && hy === 'AUTO') B.push(row);
      });
    }
  }
}
const w1 = A.filter((r) => r.kind === 'abspos' && r.where === 'mid-word' && r.member.paintInert);
// W1 as recommended (brief §5): additionally gated on the host's effective `hyphens: auto`.
const w1auto = w1.filter((r) => r.hyphens === 'AUTO');
const summary = {
  run: RUN_ID, perTestIrDocs: docs, runsHosts, runsTextEntries: textEntries, testsWithRunsHosts: runsTests.size,
  outOfFlowMembersInRuns: A.length,
  byKindWhere: A.reduce((m, r) => { const k = `${r.kind}/${r.where}`; m[k] = (m[k] ?? 0) + 1; return m; }, {}),
  midWordUnderHyphensAuto: B.length,
  w1ReachUngated: { members: w1.length, tests: [...new Set(w1.map((r) => r.test))] },
  w1ReachHyphensAuto: { members: w1auto.length, hosts: w1auto.map((r) => r.host), tests: [...new Set(w1auto.map((r) => r.test))] },
  paintInertOutOfFlowMembers: A.filter((r) => r.member.paintInert).length,
  w2Reach: { runsHosts, tests: runsTests.size },
};
fs.writeFileSync(path.join(HERE, 'web-out-of-flow-hyphen-box.census.json'), JSON.stringify({ summary, outOfFlowMembers: A, midWordUnderHyphensAuto: B }, null, 1) + '\n');
console.log(JSON.stringify(summary, null, 1));
console.log('\n# A. out-of-flow members inside runs (kind/where hyphens lang paintInert | split | web ios android)');
for (const r of A) console.log(`${r.test.replace('css/', '')}  ${r.kind}/${r.where} ${r.hyphens} ${r.lang ?? '-'} inert=${r.member.paintInert} | ${JSON.stringify(r.split)} | ${r.cells.web ?? '—'} · ${r.cells.ios ?? '—'} · ${r.cells.android ?? '—'}`);
console.log('\n# B. mid-word children under effective hyphens:auto (any child kind)');
for (const r of B) console.log(`${r.test.replace('css/', '')}  ${r.member.tag} ${r.lang ?? '-'} | ${JSON.stringify(r.split)} | ${r.cells.web ?? '—'} · ${r.cells.ios ?? '—'} · ${r.cells.android ?? '—'}`);
