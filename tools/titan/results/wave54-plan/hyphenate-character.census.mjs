#!/usr/bin/env node
// hyphenate-character.census.mjs — wave-54 planning census (read-only).
// Scans every per-test IR document of a run (default wave53-final) and reports:
//  (1) hyphenate-character carriers: typed `HyphenateCharacter` and the
//      `Generic {propertyName:'hyphenate-character', _unmapped}` the converter
//      emits when HyphenateCharacterPropertyParser returns null; for each, the
//      wire value, whether it still holds an undecoded CSS escape (a backslash),
//      and whether the carrier's text reaches a taken hyphenation point
//      (U+00AD in its text/runs, or Hyphens AUTO);
//  (2) the BR-HEIGHT radius: every `meta.role: line-break` child whose Height
//      is 20px although its parent's `meta.runs` put NON-WHITESPACE text between
//      the previous child entry and it — the brs the extractor's per-scope
//      `childLineCtx` (seeded once from ownText, never re-armed by interleaved
//      text) marks as a blank line when they END a text line (CSS 2.1 §9.5);
//  (3) other typed property data carrying a backslash (undecoded CSS escapes
//      outside hyphenate-character), as context for the converter fix's scope.
// Each test is joined to its scored cells with the gate scorer's own loadRun.
// Usage: node hyphenate-character.census.mjs [run-id] [--json out.json]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';
const args = process.argv.slice(2);
const runId = args.find((a) => !a.startsWith('--')) ?? 'wave53-final';
const jsonOut = args.includes('--json') ? args[args.indexOf('--json') + 1] : null;
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);
// test key "css/<sec>/<a>/<b>.html" ⇄ IR file "wpt__<sec>__<a>__<b>.json"
const cellsByIr = new Map();
for (const [sec, s] of Object.entries(run.sections)) {
  for (const [k, c] of s.cells) {
    const [test, plat] = k.split('|');
    const ir = 'wpt__' + test.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__') + '.json';
    if (!cellsByIr.has(ir)) cellsByIr.set(ir, { test: test.replace(/^css\//, ''), cells: {} });
    cellsByIr.get(ir).cells[plat] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  }
}
const SHY = '­';
const hc = [], br = [], esc = [];
let docs = 0;
for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  const d = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
    const comps = doc.components ?? [];
    const byName = new Map(comps.map((c) => [c.name, c]));
    const cell = cellsByIr.get(f) ?? { test: `${sec}/${f}`, cells: {} };
    for (const c of comps) {
      const runs = c.meta?.runs ?? [];
      const texts = [c.text ?? '', ...runs.filter((r) => typeof r.text === 'string').map((r) => r.text)].join('');
      for (const p of c.properties ?? []) {
        const generic = p.type === 'Generic' && p.data?.propertyName === 'hyphenate-character';
        if (p.type === 'HyphenateCharacter' || generic) {
          const value = generic ? `Generic rawValue ${p.data.rawValue}` : JSON.stringify(p.data);
          const hy = (c.properties.find((q) => q.type === 'Hyphens') ?? {}).data ?? null;
          hc.push({ sec, ir: f, test: cell.test, comp: c.id, value, undecodedEscape: /\\/.test(generic ? '' : p.data?.value ?? ''),
            rejectedByParser: generic, hyphens: hy, carriesShy: texts.includes(SHY), cells: cell.cells });
        } else if (p.type !== 'Generic' && /\\\\[0-9a-fA-F]/.test(JSON.stringify(p.data))) {
          esc.push({ sec, ir: f, test: cell.test, comp: c.id, type: p.type, data: JSON.stringify(p.data).slice(0, 120), cells: cell.cells });
        }
      }
      // (2) br-height radius over this host's runs
      let sawText = false;
      for (const r of runs) {
        if (typeof r.text === 'string') { if (r.text.trim() !== '') sawText = true; continue; }
        const ch = byName.get(r.child);
        if (ch?.meta?.role === 'line-break') {
          const h = (ch.properties ?? []).find((q) => q.type === 'Height')?.data?.px;
          const clear = (ch.properties ?? []).some((q) => q.type === 'Clear');
          if (sawText && h === 20 && !clear) br.push({ sec, ir: f, test: cell.test, host: c.id, br: ch.id, cells: cell.cells });
        }
        sawText = false; // any child entry closes the interleaved-text window
      }
    }
  }
}
const uniq = (xs) => [...new Map(xs.map((x) => [x.ir, x])).values()];
console.log(`docs scanned: ${docs} (run ${runId})`);
console.log(`\n(1) hyphenate-character carriers: ${hc.length} properties in ${uniq(hc).length} docs`);
for (const x of hc) console.log(`  ${x.test.padEnd(44)} ${x.value.padEnd(38)} rejected=${x.rejectedByParser} escape=${x.undecodedEscape} hyphens=${x.hyphens} shy=${x.carriesShy} | ${JSON.stringify(x.cells)}`);
console.log(`\n(2) br-height radius: ${br.length} brs (20px after interleaved text) in ${uniq(br).length} docs`);
const brDocs = new Map();
for (const x of br) brDocs.set(x.ir, { ...x, n: (brDocs.get(x.ir)?.n ?? 0) + 1 });
let pass = { web: 0, ios: 0, android: 0 }, scored = { web: 0, ios: 0, android: 0 };
for (const x of brDocs.values()) {
  for (const [p, v] of Object.entries(x.cells)) { scored[p]++; if (v.startsWith('P')) pass[p]++; }
  console.log(`  ${x.test.padEnd(60)} brs ${String(x.n).padStart(2)} | web ${x.cells.web ?? '—'} · ios ${x.cells.ios ?? '—'} · android ${x.cells.android ?? '—'}`);
}
console.log(`  cells: web ${pass.web}P/${scored.web} · ios ${pass.ios}P/${scored.ios} · android ${pass.android}P/${scored.android}`);
console.log(`\n(3) other typed data with a backslash-hex escape: ${esc.length} properties in ${uniq(esc).length} docs`);
for (const x of esc) console.log(`  ${x.test.padEnd(50)} ${x.type.padEnd(18)} ${x.data}`);
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify({ run: runId, docs, hyphenateCharacter: hc, brHeight: [...brDocs.values()], escapes: esc }, null, 1));
