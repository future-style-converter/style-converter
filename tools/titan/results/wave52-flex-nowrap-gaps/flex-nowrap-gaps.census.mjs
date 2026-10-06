#!/usr/bin/env node
// tools/titan/results/wave52-flex-nowrap-gaps/flex-nowrap-gaps.census.mjs —
// wave-52 lane L10 blast radius over EVERY wave51-fix per-test IR doc
// (tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json, 1435 docs),
// joined to the three wave51-fix verdicts. Read-only; writes the JSON beside it.
//
// Classes (each mirrors the CODE predicate it censuses, cited per class):
//  B   7(b)  a child of a FLEX parent whose FlexBasis is a bare number (the
//            percent wire). Split: nowrap+in-flow+definite-main (the iOS arm
//            CSSFlexMath.percentBasis now resolves) vs wrap / abspos (untouched).
//  M2  negative main-axis px margin on an in-flow child of a NOWRAP flex
//      parent (CSSFlexLayout's ungated `outer`), row → Left/Right, column → Top/Bottom.
//  G   the Compose gate — a JS port of FlexNowrapLine.engages, line by line,
//      including the fix-pass RTL refusal (`rtl` = the container or an
//      ancestor declares `direction: rtl` — ComponentRenderer provides
//      LocalLayoutDirection Rtl for that component and every descendant).
//  R   (fix pass) every RTL flex container — the population the RTL refusal
//      keeps on Row/Column; G_rtlRefused = the ones the pre-fix gate admitted.
//  S_rtl (fix pass) decorated containers whose negative-margin child gets a
//      MIRRORED paint shift (container RTL or the child's own direction: rtl).
//  S   decorated flex containers (a *-rule-style that paints) whose children
//      carry a negative px margin (the gap-anchor paint shift, both natives).
//  D   every decorated flex container (the 7(a′) snap/fractional-band surface:
//      integral geometry is inert by construction; listed for the gate's
//      `--movers 0.005` over css-gaps).
//  P   pre-existing (NOT changed): a px flex-basis on an item that declares no
//      main size inside a statically planned nowrap container (the WPT-mode
//      50-px floor still applies there — named, not touched).
// Usage: node tools/titan/results/wave52-flex-nowrap-gaps/flex-nowrap-gaps.census.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RUN = path.resolve(HERE, '..', '..', 'runs', 'wave51-fix', 'sections');

// ── IR readers (the same leaves the Swift / Kotlin readers accept) ─────────
const last = (props, t) => [...props].reverse().find(p => p.type === t);
// Concrete px leaf: {"px": n} / {"type":"length","px": n}.
const px = (props, t) => { const p = last(props, t); return p && p.data && typeof p.data === 'object' && typeof p.data.px === 'number' ? p.data.px : null; };
// Plain-string keyword, upper-cased.
const kw = (props, t) => { const p = last(props, t); return p && typeof p.data === 'string' ? p.data.toUpperCase() : null; };
// Flex factor normalizedValue.
const factor = (props, t) => { const p = last(props, t); return p && p.data && typeof p.data === 'object' && typeof p.data.normalizedValue === 'number' ? p.data.normalizedValue : null; };
const has = (props, t) => props.some(p => p.type === t);
const pxOrAbsent = (props, t) => !has(props, t) || px(props, t) !== null;
const isFlex = props => ['FLEX', 'INLINE_FLEX'].includes(kw(props, 'Display'));
const isWrap = props => ['WRAP', 'WRAP_REVERSE'].includes(kw(props, 'FlexWrap'));
const isColumn = props => ['COLUMN', 'COLUMN_REVERSE'].includes(kw(props, 'FlexDirection'));
const outOfFlow = props => ['ABSOLUTE', 'FIXED'].includes(kw(props, 'Position'));
const RULE_STYLES = ['ColumnRuleStyle', 'RowRuleStyle'];
const decorated = props => RULE_STYLES.some(t => { const s = kw(props, t); return s && s !== 'NONE' && s !== 'HIDDEN'; });

// ── JS port of FlexNowrapLine.engages (runtimes/compose/…/layout/flexbox/FlexNowrapLine.kt) ──
const START = new Set(['FLEX_START', 'START', 'NORMAL', 'LEFT']);
const CROSS_START = new Set(['NORMAL', 'STRETCH', 'FLEX_START', 'START', 'SELF_START']);
const MARGINS = ['MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft'];
function outerDelta(c, row) {
  const [s, e] = row ? ['MarginLeft', 'MarginRight'] : ['MarginTop', 'MarginBottom'];
  return Math.min(0, px(c, s) ?? 0) + Math.min(0, px(c, e) ?? 0);
}
function engages(box, kids, row, hasText, rtl) {
  if (hasText || kids.length === 0) return false;
  // Fix pass: RTL refuses (Row mirrors the line; Line would draw it from the left).
  if (rtl) return false;
  const w = kw(box, 'FlexWrap'); if (w !== null && w !== 'NOWRAP') return false;
  const d = kw(box, 'FlexDirection'); if (d !== null && d !== (row ? 'ROW' : 'COLUMN')) return false;
  const j = kw(box, 'JustifyContent'); if (j !== null && !START.has(j)) return false;
  const a = kw(box, 'AlignItems'); if (a !== null && !CROSS_START.has(a)) return false;
  const main = px(box, row ? 'Width' : 'Height'); if (main === null) return false;
  const gapT = row ? 'ColumnGap' : 'RowGap'; if (!pxOrAbsent(box, gapT)) return false;
  const gap = px(box, gapT) ?? 0;
  let outer = 0, anyNeg = false, allZero = true;
  for (const c of kids) {
    if (outOfFlow(c) || has(c, 'Float')) return false;
    const size = px(c, row ? 'Width' : 'Height'); if (size === null) return false;
    if (px(c, row ? 'Height' : 'Width') === null) return false;
    if ((factor(c, 'FlexGrow') ?? 0) !== 0) return false;
    if (['FlexBasis', 'AlignSelf', 'Order'].some(t => has(c, t))) return false;
    if (MARGINS.some(t => !pxOrAbsent(c, t))) return false;
    const [s, e] = row ? ['MarginLeft', 'MarginRight'] : ['MarginTop', 'MarginBottom'];
    const dl = outerDelta(c, row);
    outer += size + Math.max(0, px(c, s) ?? 0) + Math.max(0, px(c, e) ?? 0) + dl;
    anyNeg = anyNeg || dl < 0;
    allZero = allZero && factor(c, 'FlexShrink') === 0;
  }
  const line = outer + gap * (kids.length - 1);
  if (line > main && !allZero) return false;
  return anyNeg || line > main;
}

// ── verdicts: manifest.wpt.results[css/<sec>/<path>.html].browserRef.diffs[<plat>-ref] ──
const verdictCache = new Map();
function verdicts(section, file) {
  if (!verdictCache.has(section)) {
    const m = path.join(RUN, section, 'manifest.json');
    verdictCache.set(section, fs.existsSync(m) ? JSON.parse(fs.readFileSync(m, 'utf8')).wpt?.results ?? {} : {});
  }
  const results = verdictCache.get(section);
  // wpt__css-gaps__flex__x.json → css/css-gaps/flex/x.<ext>
  const stem = 'css/' + file.replace(/^wpt__/, '').replace(/\.json$/, '').split('__').join('/');
  const key = Object.keys(results).find(k => k.replace(/\.[a-z]+$/, '') === stem);
  const out = { key: key ?? stem };
  for (const plat of ['web', 'ios', 'android']) {
    const dd = key ? results[key]?.browserRef?.diffs?.[`${plat}-ref`] : null;
    out[plat] = dd ? `${dd.wptPass ? 'P' : 'f'} ${typeof dd.ssim === 'number' ? dd.ssim.toFixed(4) : '?'}` : 'unscored';
  }
  return out;
}

// ── the walk ────────────────────────────────────────────────────────────────
const classes = { B_nowrapResolved: [], B_untouched: [], M2: [], G: [], R: [], G_rtlRefused: [], S: [], S_rtl: [], D: [], P: [] };
let docs = 0;
for (const section of fs.readdirSync(RUN).sort()) {
  const dir = path.join(RUN, section, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const file of fs.readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
    docs++;
    const comps = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8')).components ?? [];
    const kidsOf = id => comps.filter(c => c.slot?.parent === id);
    const byId = new Map(comps.map(c => [c.id, c]));
    // Own-or-ancestor `direction: rtl` ⇔ Compose LocalLayoutDirection Rtl here.
    const rtlChain = c => { for (let n = c; n; n = byId.get(n.slot?.parent)) if (kw(n.properties ?? [], 'Direction') === 'RTL') return true; return false; };
    const hits = new Set();
    for (const box of comps) {
      const bp = box.properties ?? [];
      if (!isFlex(bp)) continue;
      const kids = kidsOf(box.id);
      const kp = kids.map(k => k.properties ?? []);
      const row = !isColumn(bp);
      const nowrap = !isWrap(bp);
      const inFlow = kp.filter(p => !outOfFlow(p));
      // B — percent basis (bare number wire).
      for (const p of kp) {
        const fb = last(p, 'FlexBasis');
        if (!fb || typeof fb.data !== 'number') continue;
        const resolves = nowrap && !outOfFlow(p) && px(bp, row ? 'Width' : 'Height') !== null;
        (resolves ? classes.B_nowrapResolved : classes.B_untouched).push({ file, box: box.id, pct: fb.data, nowrap, abspos: outOfFlow(p) });
        hits.add(resolves ? 'B_nowrapResolved' : 'B_untouched');
      }
      // M2 — negative main-axis margin on an in-flow child of a nowrap parent.
      if (nowrap) for (const p of inFlow) {
        if (outerDelta(p, row) < 0) { classes.M2.push({ file, box: box.id, delta: outerDelta(p, row) }); hits.add('M2'); }
      }
      // G — the Compose gate (with the RTL refusal); R / G_rtlRefused — RTL lines.
      const rtl = rtlChain(box);
      if (engages(bp, kp, row, !!box.text, rtl)) { classes.G.push({ file, box: box.id, row }); hits.add('G'); }
      if (rtl) { classes.R.push({ file, box: box.id, row }); hits.add('R'); }
      if (rtl && engages(bp, kp, row, !!box.text, false)) { classes.G_rtlRefused.push({ file, box: box.id, row }); hits.add('G_rtlRefused'); }
      // S / D — decorated containers.
      if (decorated(bp)) {
        classes.D.push({ file, box: box.id, wrap: !nowrap, alignContent: kw(bp, 'AlignContent'), justify: kw(bp, 'JustifyContent') });
        hits.add('D');
        if (kp.some(p => ['MarginLeft', 'MarginRight', 'MarginTop', 'MarginBottom'].some(t => (px(p, t) ?? 0) < 0))) {
          classes.S.push({ file, box: box.id }); hits.add('S');
          // S_rtl — the hook would MIRROR a shift (container or child RTL).
          if (kids.some(k => (rtl || kw(k.properties ?? [], 'Direction') === 'RTL') && ['MarginLeft', 'MarginRight'].some(t => (px(k.properties ?? [], t) ?? 0) < 0))) {
            classes.S_rtl.push({ file, box: box.id }); hits.add('S_rtl');
          }
        }
      }
      // P — px basis + no main size in a nowrap definite container (pre-existing).
      if (nowrap && px(bp, row ? 'Width' : 'Height') !== null) for (const p of inFlow) {
        const fb = last(p, 'FlexBasis');
        if (fb && typeof fb.data === 'object' && fb.data?.value?.px !== undefined && px(p, row ? 'Width' : 'Height') === null) {
          classes.P.push({ file, box: box.id }); hits.add('P');
        }
      }
    }
    for (const h of hits) for (const row of classes[h]) if (row.file === file && !row.verdict) row.verdict = verdicts(section, file);
  }
}
const tests = rows => [...new Set(rows.map(r => r.file))];
const summary = Object.fromEntries(Object.entries(classes).map(([k, v]) => [k, { rows: v.length, tests: tests(v).length }]));
const out = { run: 'wave51-fix', docs, summary, classes };
fs.writeFileSync(path.join(HERE, 'flex-nowrap-gaps.census.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`docs ${docs}`);
for (const [k, v] of Object.entries(summary)) {
  console.log(`${k.padEnd(18)} rows ${String(v.rows).padStart(3)}  tests ${String(v.tests).padStart(3)}`);
  for (const f of tests(classes[k])) {
    const r = classes[k].find(x => x.file === f);
    console.log(`   ${r.verdict.key.padEnd(72)} web ${r.verdict.web} · ios ${r.verdict.ios} · android ${r.verdict.android}`);
  }
}
