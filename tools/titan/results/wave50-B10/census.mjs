#!/usr/bin/env node
//
// wave50-B10/census.mjs — regenerates this lane's two committed artifacts
// from the wave49-final run dir and the frozen WPT corpus. Run from the
// repo root:
//
//     node tools/titan/results/wave50-B10/census.mjs
//
// Writes, beside this file:
//   css-gaps-band-blast-radius.json  which css-gaps cells the wave-50
//       line-band reconstruction (GapDecorationBands) can move, and why
//       every other cell is inert BY CONSTRUCTION rather than by opinion.
//   erased-refs.json  which scored cells sit behind a reference document
//       whose decoration boxes the ref-capture recipe erases (the
//       negative-z-index defect; see _note.md).
//
// Inputs are all repo paths: tools/titan/runs/wave49-final/sections/**
// (gitignored run dir — regenerate with the corpus-v6.15 `reproduce`
// recipe if it is gone) and tools/wpt/** (gitignored corpus mirror —
// tools/titan/fetch-wpt.sh).
//
// The erased-refs half is a STATIC census (which refs use a negative
// z-index AND are the match target of a scored test). The pixel proof
// that the recipe erases them is a separate, puppeteer-driven
// measurement; its numbers are quoted in _note.md and in the patch's own
// comment, and the recipe to re-run it is in _note.md.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RUNS = 'tools/titan/runs/wave49-final/sections';
const GAPS = path.join(RUNS, 'css-gaps');

// ── shared helpers ──────────────────────────────────────────────────────

/** The campaign scorer idiom: a cell counts only if it has a real ssim. */
const cell = (diffs, key) => {
  const x = diffs?.[key];
  if (!x || typeof x.ssim !== 'number' || x.scoreExcluded) return null;
  return { verdict: x.wptPass === true ? 'P' : 'f', ssim: Number(x.ssim.toFixed(4)) };
};
const props = c => c.properties ?? [];
const val = (p, t) => p.find(x => x.type === t)?.data;
const px = d => (d && typeof d === 'object' && typeof d.px === 'number') ? d.px : null;

/** Per-test IR path for a wpt test key, as the run dir names it. */
const irPath = (section, test) =>
  path.join(RUNS, section, 'per-test-ir',
    'wpt__' + test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__') + '.json');

// ── artifact 1: the css-gaps band blast radius ─────────────────────────

// GapDecorationBands.resolve returns its INPUT — byte-identical paint —
// unless every one of these holds, and all are decidable from the IR:
//   1. the container declares a gap rule that paints  (hook is live)
//   2. flex-wrap: wrap                                (nowrap ⇒ one line)
//   3. align-content absent | normal | stretch        (css-align-3 §5.1)
//   4. a DEFINITE cross size                          (css-flexbox-1 §9.4 step 8)
//   5. a resolvable cross gap                         (else the rebuild refuses)
//   6. leftover > 0 after §9.3 line breaking          (else stretchLines is identity)
function blastRadius() {
  const m = JSON.parse(fs.readFileSync(path.join(GAPS, 'manifest.json'), 'utf8'));
  const out = [];
  for (const [test, o] of Object.entries(m.wpt?.results ?? {})) {
    const diffs = o.browserRef?.diffs;
    if (!diffs) continue;
    const row = {
      test,
      cells: { web: cell(diffs, 'web-ref'), ios: cell(diffs, 'ios-ref'), android: cell(diffs, 'android-ref') },
      verdict: 'unchanged',
      why: [],
    };
    const f = irPath('css-gaps', test);
    if (!fs.existsSync(f)) { row.why.push('no per-test IR'); out.push(row); continue; }
    const j = JSON.parse(fs.readFileSync(f, 'utf8'));
    // Gate 1 — a flex container that paints a rule.
    const containers = (j.components ?? []).filter(c =>
      props(c).some(x => x.type === 'Display' && x.data === 'FLEX') &&
      props(c).some(x => /Rule(Style)$/.test(x.type) &&
        !['NONE', 'HIDDEN'].includes(String(x.data).toUpperCase())));
    if (!containers.length) { row.why.push('no flex container paints a rule'); out.push(row); continue; }

    for (const c of containers) {
      const p = props(c);
      const mainH = !String(val(p, 'FlexDirection') ?? 'ROW').toUpperCase().startsWith('COLUMN');
      const wrap = String(val(p, 'FlexWrap') ?? 'NOWRAP').toUpperCase();
      const ac = val(p, 'AlignContent');
      const crossKey = mainH ? 'Height' : 'Width', mainKey = mainH ? 'Width' : 'Height';
      const crossGapRaw = val(p, mainH ? 'RowGap' : 'ColumnGap');
      const crossGap = crossGapRaw === undefined ? 0 : px(crossGapRaw);
      const mainGap = px(val(p, mainH ? 'ColumnGap' : 'RowGap')) ?? 0;
      const containerCross = px(val(p, crossKey)), containerMain = px(val(p, mainKey));
      const why = [];
      if (!wrap.startsWith('WRAP')) why.push('nowrap');
      if (!(ac === undefined || ['NORMAL', 'STRETCH', 'AUTO'].includes(String(ac).toUpperCase())))
        why.push('align-content:' + ac);
      if (containerCross === null) why.push('auto cross size');
      if (crossGap === null) why.push('unresolved cross gap');
      if (why.length) { row.why.push(...why); continue; }

      // Gate 6 — §9.3 line breaking over the declared item sizes, then
      // the §9.4-step-8 leftover. An item with NO declared cross size and
      // `align-items: normal` stretches to its line (css-flexbox-1 §8.4),
      // so its PLACED cross size equals the band: such a line contributes
      // its own band to `base` and the leftover is 0 by construction,
      // which is what the runtime's early return relies on.
      const kids = (j.components ?? []).filter(k => k.slot?.parent === c.id);
      const ai = String(val(p, 'AlignItems') ?? 'NORMAL').toUpperCase();
      const itemsStretch = ['NORMAL', 'STRETCH'].includes(ai);
      const sizes = kids.map(k => ({
        main: px(val(props(k), mainKey)) ?? 0,
        cross: px(val(props(k), crossKey)),
      }));
      if (itemsStretch && sizes.every(s => s.cross === null)) {
        row.why.push('items stretch to their line ⇒ union == band ⇒ leftover 0');
        continue;
      }
      const lines = []; let cur = [], used = 0;
      for (const s of sizes) {
        const add = s.main + (cur.length ? mainGap : 0);
        if (cur.length && containerMain !== null && used + add > containerMain) {
          lines.push(cur); cur = [s]; used = s.main;
        } else { cur.push(s); used += add; }
      }
      if (cur.length) lines.push(cur);
      const base = lines.map(l => Math.max(...l.map(s => s.cross ?? 0)));
      const leftover = containerCross - base.reduce((a, b) => a + b, 0) - crossGap * (base.length - 1);
      row.geometry = { lines: base.length, base, containerCross, crossGap, leftover };
      if (base.length < 2) { row.why.push('single line'); continue; }
      if (leftover <= 0) { row.why.push('no §9.4 leftover (lines already tile the box)'); continue; }
      row.verdict = 'PAINT CHANGES';
    }
    out.push(row);
  }
  out.sort((a, b) => a.test.localeCompare(b.test));
  return {
    _note: 'wave50-B10 · which css-gaps cells the §9.4-step-8 line-band reconstruction can move',
    source: 'tools/titan/runs/wave49-final/sections/css-gaps',
    scoredTests: out.length,
    changing: out.filter(r => r.verdict === 'PAINT CHANGES').map(r => r.test),
    rows: out,
  };
}

// ── artifact 2: the erased-reference census ────────────────────────────

// Measured 2026-09-14 by the A/B in _note.md — chromatic (saturated) pixel
// counts, live recipe vs the proposed body-background removal.
const PIXEL_PROOF = {
  'tools/wpt/css/css-gaps/flex/flex-gap-decorations-033-ref.html': { live: 0, fixed: 5200, erased: true },
  'tools/wpt/css/css-gaps/flex/flex-gap-decorations-034-ref.html': { live: 0, fixed: 2500, erased: true },
  'tools/wpt/css/css-gaps/flex/flex-gap-decorations-035-ref.html': { live: 0, fixed: 4050, erased: true },
  'tools/wpt/css/css-gaps/flex/flex-gap-decorations-036-ref.html': { live: 0, fixed: 2050, erased: true },
  'tools/wpt/css/css-gaps/flex/flex-gap-decorations-037-ref.html': { live: 0, fixed: 2050, erased: true },
  'tools/wpt/css/css-text/hanging-punctuation/reference/hanging-punctuation-block-bound-001-ref.html':
    { live: 0, fixed: 58184, erased: true },
  'tools/wpt/css/css-view-transitions/css-tags-paint-order-ref.html':
    { live: 234000, fixed: 234000, erased: false },
  'tools/wpt/css/css-view-transitions/css-tags-paint-order-with-entry-ref.html':
    { live: 234000, fixed: 234000, erased: false },
};

function erasedRefs() {
  const hits = [];
  let scored = 0, unresolved = 0;
  for (const section of fs.readdirSync(RUNS)) {
    const mf = path.join(RUNS, section, 'manifest.json');
    if (!fs.existsSync(mf)) continue;
    const m = JSON.parse(fs.readFileSync(mf, 'utf8'));
    for (const [test, o] of Object.entries(m.wpt?.results ?? {})) {
      const diffs = o.browserRef?.diffs;
      if (!diffs) continue;
      scored++;
      const abs = path.join('tools/wpt', test);
      if (!fs.existsSync(abs)) { unresolved++; continue; }
      const mm = fs.readFileSync(abs, 'utf8')
        .match(/rel=["']match["'][^>]*href=["']([^"']+)["']/);
      if (!mm) { unresolved++; continue; }
      const refAbs = path.resolve(path.dirname(abs), mm[1].split('#')[0]);
      if (!fs.existsSync(refAbs)) { unresolved++; continue; }
      const ref = fs.readFileSync(refAbs, 'utf8');
      // The shape the recipe erases: a decoration box at a negative
      // z-index whose nearest stacking context is the root, i.e. a body
      // child. (A negative z-index INSIDE another stacking context — the
      // css-view-transitions pair — is unaffected; the puppeteer A/B in
      // _note.md is what separates the two, and it cleared those two.)
      if (!/z-index:\s*-[0-9]/.test(ref)) continue;
      const refRel = path.relative('.', refAbs);
      hits.push({
        section,
        test,
        ref: refRel,
        // Executed A/B (puppeteer, BROWSER_LAUNCH_ARGS, 390x600): chromatic
        // pixels under the LIVE recipe vs the same sheet with `background`
        // dropped from the body half. `erased` means the recipe hides ink
        // the reference document really draws.
        pixelProof: PIXEL_PROOF[refRel] ?? null,
        cells: {
          web: cell(diffs, 'web-ref'),
          ios: cell(diffs, 'ios-ref'),
          android: cell(diffs, 'android-ref'),
        },
        nativeParity: o.nativeParity?.pairs ?? null,
      });
    }
  }
  hits.sort((a, b) => a.test.localeCompare(b.test));
  const cells = hits.flatMap(h => Object.values(h.cells)).filter(Boolean);
  return {
    _note: 'wave50-B10 · scored cells whose match reference draws its decoration boxes at a negative z-index',
    scoredTestsExamined: scored,
    unresolvedMatchLink: unresolved,
    affectedTests: hits.length,
    erasedTests: hits.filter(h => h.pixelProof?.erased).length,
    affectedCells: cells.length,
    erasedCells: hits.filter(h => h.pixelProof?.erased)
      .flatMap(h => Object.values(h.cells)).filter(Boolean).length,
    failingCells: cells.filter(c => c.verdict === 'f').length,
    rows: hits,
  };
}

const a = blastRadius();
const b = erasedRefs();
fs.writeFileSync(path.join(HERE, 'css-gaps-band-blast-radius.json'), JSON.stringify(a, null, 2) + '\n');
fs.writeFileSync(path.join(HERE, 'erased-refs.json'), JSON.stringify(b, null, 2) + '\n');
console.log(`blast radius: ${a.scoredTests} scored css-gaps tests, ${a.changing.length} change → ${a.changing.join(', ')}`);
console.log(`erased refs: ${b.affectedTests} tests / ${b.affectedCells} cells (${b.failingCells} currently failing)`);
