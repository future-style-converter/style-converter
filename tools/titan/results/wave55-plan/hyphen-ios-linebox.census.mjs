#!/usr/bin/env node
// tools/titan/results/wave55-plan/hyphen-ios-linebox.census.mjs — wave-55 L1 (BACKLOG 0(f)) planning census, read-only.
//
// Why: the proposed unit changes ONE row of the iOS line-box table — a text run whose USED line-height is the
// DECLARED keyword `normal` under WPT capture (LineHeightNormal.lineBoxSource `.natural`, today nil = SwiftUI's raw
// face metrics, unpinned). Its radius is every text-bearing component whose effective LineHeight (own, else inherited
// through the iOS InheritedText channel, which carries "LineHeight") is `{original: "normal"}`. This census lists them
// over every per-test IR document of a run, with the face iOS paints (the generic `monospace` → the DejaVu Sans Mono
// pin, tools/titan/mono-pin.mjs; no family / sans-serif / Inter → bundled Inter; anything else → "other"), the used
// font size (own px, else inherited, else the monospace-UA 13px quirk for a monospace family, else 16), and the two
// line boxes per line:
//   raw     = (hhea ascender + |descender| + lineGap) / upem × size      — what iOS lays out today (UIFont.lineHeight)
//   chrome  = round(asc·size/upem) + round(desc·size/upem) + round(gap·size/upem) — Blink's FontMetrics line spacing,
//             what the frozen refs (Chromium) and Compose (FontMetricsInt) lay `normal` out on.
// It also lists the line-start <br> boxes under such hosts (the U3b brs) and joins every document to its scored cells
// with the gate scorer's own loadRun. Usage: node hyphen-ios-linebox.census.mjs [run-id=wave54-final]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const argv = process.argv.slice(2);
const runId = argv.find((a) => !a.startsWith('--')) ?? 'wave54-final';
// --json <file>: the machine-readable carrier / must-not-move stems (capture stems = the PNG basenames per platform).
const jsonOut = argv.includes('--json') ? argv[argv.indexOf('--json') + 1] : null;
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);
// hhea metrics (fontTools-measured in tools/titan/mono-pin.mjs and LineBoxMetrics.swift's Inter constants).
const FACES = {
  dejavu: { asc: 1901, desc: 483, gap: 0, upem: 2048 },   // DejaVu Sans Mono == Menlo (mono-pin.mjs banner)
  inter: { asc: 1984, desc: 494, gap: 0, upem: 2048 },    // Inter 4.0 (LineBoxMetrics.interContentRatio)
  // Linux Libertine 4.7.5 (hhea, hyphen-ios-linebox.facemetrics.out.txt): boundary-shaping's `@font-face {font-family:
  // test}`, delivered to the natives as a document face — the iOS 42 px stacked pitch (ceil 41.04) is its raw box.
  libertine: { asc: 894, desc: 246, gap: 0, upem: 1000 },
};
// JS Math.round matches lroundf / SkScalarRoundToScalar for the positive values here (half rounds up).
const boxes = (face, px) => {
  const f = FACES[face];
  if (!f) return null;
  const raw = (f.asc + f.desc + f.gap) / f.upem * px;
  const chrome = Math.round(f.asc * px / f.upem) + Math.round(f.desc * px / f.upem) + Math.round(f.gap * px / f.upem);
  return { raw: Number(raw.toFixed(4)), chrome };
};
// test key "css/<sec>/<a>/<b>.html" ⇄ IR file "wpt__<sec>__<a>__<b>.json" (the scorer's naming, as the wave-54 census).
const cellsByIr = new Map();
for (const s of Object.values(run.sections)) {
  for (const [k, c] of s.cells) {
    const [test, plat] = k.split('|');
    const ir = 'wpt__' + test.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__') + '.json';
    if (!cellsByIr.has(ir)) cellsByIr.set(ir, { test: test.replace(/^css\//, ''), cells: {} });
    cellsByIr.get(ir).cells[plat] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  }
}
const last = (c, type) => { const ps = (c.properties ?? []).filter((p) => p.type === type); return ps.length ? ps[ps.length - 1].data : undefined; };
const textOf = (c) => [c.text ?? '', ...(c.meta?.runs ?? []).filter((r) => typeof r.text === 'string').map((r) => r.text)].join('');
const rows = [], brs = [];
let docs = 0, comps = 0;
for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  const d = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
    const list = doc.components ?? [];
    comps += list.length;
    const byId = new Map(list.map((c) => [c.id, c]));
    // Effective (inherited) value of one property: own last declaration, else the parent's (slot.parent is an id).
    const memo = new Map();
    const eff = (c, type, depth = 0) => {
      const key = `${c.id}|${type}`;
      if (memo.has(key)) return memo.get(key);
      let v = last(c, type);
      if (v === undefined && depth < 64) { const p = byId.get(c.slot?.parent); v = p ? eff(p, type, depth + 1) : undefined; }
      memo.set(key, v);
      return v;
    };
    for (const c of list) {
      const lh = eff(c, 'LineHeight');
      if (!(lh && typeof lh === 'object' && lh.original === 'normal')) continue;
      const own = last(c, 'LineHeight') !== undefined;
      const fam = eff(c, 'FontFamily');
      const famList = Array.isArray(fam) ? fam.map((x) => (typeof x === 'string' ? x : (x?.name ?? x?.keyword ?? JSON.stringify(x)))) : [];
      const mono = famList.some((x) => /^(ui-)?monospace$/i.test(x));
      // The face iOS splits the line box around (LineBoxMetrics.contentHeight's resolution order): a document face
      // (boundary-shaping's `test`), the monospace pin, else the DEFAULT design = bundled Inter — which is also what a
      // concrete family with no document face (`Arial`) resolves to there (its 112 px measured box = ceil(111.32)).
      const face = famList.includes('test') ? 'libertine' : mono ? 'dejavu' : 'inter';
      const fsData = eff(c, 'FontSize');
      const px = (fsData && typeof fsData.px === 'number') ? fsData.px : (mono ? 13 : 16);
      if (c.meta?.role === 'line-break') {
        const h = last(c, 'Height');
        brs.push({ sec, ir: f, comp: c.id, height: h?.px ?? null, face, px });
        continue;
      }
      const text = textOf(c);
      if (!text.trim()) continue;
      const cell = cellsByIr.get(f) ?? { test: `${sec}/${f}`, cells: {} };
      rows.push({ sec, ir: f, test: cell.test, comp: c.id, own, face, families: famList.join(','), px,
        boxes: boxes(face, px), chars: text.length, ios: cell.cells.ios ?? '—', cells: cell.cells,
        // A run of the hyphenate-character family is a multi-line U+00AD pre-break (its N is measured in the pictures);
        // every other radius run is one line (no internal wrap at its box width — read off the captures).
        multiLine: /hyphenate-character-00[1-4]/.test(f) });
    }
  }
}
// ---- report ----
console.log(`census ${runId}: ${docs} per-test IR documents, ${comps} components`);
console.log(`(1) text-bearing components whose effective LineHeight is the declared keyword 'normal': ${rows.length}` +
  ` in ${new Set(rows.map((r) => r.ir)).size} documents (own declaration ${rows.filter((r) => r.own).length}, inherited ${rows.filter((r) => !r.own).length})`);
const byFace = {};
for (const r of rows) { const k = `${r.face} ${r.px}px`; (byFace[k] ??= { n: 0, docs: new Set(), b: r.boxes }).n++; byFace[k].docs.add(r.ir); }
console.log('    by iOS face × used size (per-line raw → chrome; Δ = chrome − raw):');
for (const [k, v] of Object.entries(byFace).sort()) {
  const b = v.b; const delta = b ? (b.chrome - b.raw).toFixed(4) : 'n/a';
  console.log(`      ${k.padEnd(16)} components ${String(v.n).padStart(4)}  documents ${String(v.docs.size).padStart(4)}  raw ${b ? b.raw : '?'} → chrome ${b ? b.chrome : '?'}  Δ/line ${delta}`);
}
console.log('(2) documents carrying such text, with their iOS cell (wave scorer; — = not scored on iOS):');
const docsSeen = new Map();
for (const r of rows) {
  const k = r.ir;
  if (!docsSeen.has(k)) docsSeen.set(k, { test: r.test, sec: r.sec, faces: new Set(), n: 0, ios: r.ios, cells: r.cells });
  const e = docsSeen.get(k); e.faces.add(`${r.face}@${r.px}`); e.n++;
}
const iosScored = [...docsSeen.values()].filter((e) => e.ios !== '—');
console.log(`    documents ${docsSeen.size}; scored on iOS ${iosScored.length} (P ${iosScored.filter((e) => e.ios.startsWith('P')).length} · f ${iosScored.filter((e) => e.ios.startsWith('f')).length})`);
const bySec = {};
for (const e of docsSeen.values()) (bySec[e.sec] ??= []).push(e);
for (const [sec, es] of Object.entries(bySec).sort()) {
  console.log(`    [${sec}] ${es.length}`);
  for (const e of es.sort((a, b) => a.test.localeCompare(b.test))) {
    console.log(`      ${e.test.padEnd(72)} ios ${String(e.ios).padEnd(9)} web ${String(e.cells.web ?? '—').padEnd(9)} android ${String(e.cells.android ?? '—').padEnd(9)} texts ${e.n}  faces ${[...e.faces].join(' ')}`);
  }
}
console.log(`(3) <br> line-break boxes whose effective LineHeight is 'normal': ${brs.length} in ${new Set(brs.map((b) => b.ir)).size} documents; heights:`);
const hs = {};
for (const b of brs) { const k = `${b.height}px`; (hs[k] ??= new Set()).add(b.ir); }
for (const [h, s] of Object.entries(hs).sort()) console.log(`      ${h.padEnd(8)} in ${[...s].join(', ')}`);

// ---- (4) carrier classification (iOS only: the unit changes no web / Compose / converter / extractor byte) ----
// A one-line run moves iff Blink's line box ≠ today's ceil(raw) box; a multi-line pre-broken run always moves (its
// in-run pitch changes from raw to Blink's L). A run whose box is unchanged is BYTE-IDENTICAL BY THE MODEL — but it
// still carries the R1 risk (its fractional half-leading pad snapped down a row), so it is a must-not-move CONTROL.
console.log('(4) iOS carrier classification per document (TARGET / MOVES = capture carrier; SAME = must-not-move by the model):');
const cls = new Map();
for (const r of rows) {
  const b = r.boxes; const one = b ? Math.ceil(b.raw - 1e-9) : null;
  const v = r.multiLine ? 'TARGET' : (b && b.chrome !== one ? 'MOVES' : 'SAME');
  const why = r.multiLine ? `multi-line pre-break, pitch ${b.raw} → ${b.chrome}` : `1 line: box ceil(${b.raw}) = ${one} → ${b.chrome}`;
  const prev = cls.get(r.ir);
  const rank = { TARGET: 2, MOVES: 1, SAME: 0 };
  if (!prev || rank[v] > rank[prev.v]) cls.set(r.ir, { v, why, test: r.test, ios: r.ios, face: `${r.face}@${r.px}` });
}
const tally = { TARGET: 0, MOVES: 0, SAME: 0 };
for (const [, e] of [...cls].sort((a, b) => a[1].test.localeCompare(b[1].test))) {
  tally[e.v]++;
  console.log(`      ${e.v.padEnd(6)} ${e.test.padEnd(72)} ios ${String(e.ios).padEnd(9)} ${e.face.padEnd(14)} ${e.why}`);
}
console.log(`    tally: TARGET ${tally.TARGET} · MOVES ${tally.MOVES} · SAME ${tally.SAME} (documents); web 0 · android 0 · wire 0 carriers`);
// Capture stems as the section runner names them: wpt__<sec>__<a>__<b>.png ⇄ per-test IR wpt__<sec>__<a>__<b>.json.
const stem = (ir) => ir.replace(/\.json$/, '');
const out = {
  run: runId,
  rule: 'iOS PlaceholderLabel run whose effective LineHeight is the declared keyword normal, under WPT capture, with an exact line count',
  captureCarriers: { web: [], android: [], ios: [...cls].filter(([, e]) => e.v !== 'SAME').map(([ir]) => stem(ir)).sort() },
  wireCarriers: [],
  mustNotMoveInRadius: { ios: [...cls].filter(([, e]) => e.v === 'SAME').map(([ir]) => stem(ir)).sort() },
  targets: [...cls].filter(([, e]) => e.v === 'TARGET').map(([ir]) => stem(ir)).sort(),
};
console.log(`(5) capture carriers: ios ${out.captureCarriers.ios.length} · web 0 · android 0; wire carriers 0; in-radius must-not-move ios ${out.mustNotMoveInRadius.ios.length}`);
for (const k of out.captureCarriers.ios) console.log(`      carrier ios ${k}.png`);
for (const k of out.mustNotMoveInRadius.ios) console.log(`      control ios ${k}.png`);
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify(out, null, 2) + '\n');
