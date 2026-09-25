#!/usr/bin/env node
// harness-and-android-resample.probe.mjs — READ-ONLY byte probe behind the
// wave-52 plan brief `harness-and-android-resample.md` (queue 0(aa)).
//
// What it prints, from the committed baselines tools/visual/baseline/ and the
// wave51-fix gate captures, without writing anything:
//   1. per shadow-bearing visual-test stem: every pixel in rows 0..15 where the
//      Android PNG differs from the web PNG, classified by its neighbour on the
//      web PNG (on a glyph / right of a glyph / left of a glyph / other), the
//      Android byte histogram at web-glyph positions, and the exact x columns
//      whose Android byte is lit right of a vertical stroke.
//   2. box-edge profiles (left / top / right / bottom) on 023/024/025/027 ×3 so
//      a whole-frame horizontal resample would show as a LEFT != TOP asymmetry.
//   3. the composed-path control: the two blurred-shadow corpus carriers'
//      Android vs web captures, with a ±1-px horizontal-shift explanation test.
// Run: node <this file> [repo-root]   (default root: the wave-52 gate tree)
import { createRequire } from 'node:module';
import { readFileSync, existsSync } from 'node:fs';

const R = (process.argv[2] || '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf').replace(/\/?$/, '/');
const { PNG } = createRequire(R + 'tools/visual/')('pngjs');
const B = R + 'tools/visual/baseline/';
const load = (p) => PNG.sync.read(readFileSync(p));
const px = (img, x, y) => { const i = (img.width * y + x) << 2; return [img.data[i], img.data[i + 1], img.data[i + 2]]; };
const eq = (a, b) => a[0] === b[0] && a[1] === b[1] && a[2] === b[2];
const GROUND = [26, 26, 46], INK = [174, 174, 180];

function bandDiff(stem) {
  const a = load(`${B}Android__${stem}.png`), w = load(`${B}web__${stem}.png`), i = load(`${B}iOS__${stem}.png`);
  console.log(`\n== ${stem}  A ${a.width}x${a.height} W ${w.width}x${w.height} I ${i.width}x${i.height}`);
  let rightOfInk = 0, leftOfInk = 0, onInk = 0, other = 0, total = 0; const samples = [];
  for (let y = 0; y <= 15; y++) for (let x = 0; x < Math.min(a.width, w.width); x++) {
    const pa = px(a, x, y), pw = px(w, x, y);
    if (eq(pa, pw)) continue;
    total++;
    const L = x > 0 && eq(px(w, x - 1, y), INK), Rn = x + 1 < w.width && eq(px(w, x + 1, y), INK), on = eq(pw, INK);
    if (on) onInk++; else if (L) rightOfInk++; else if (Rn) leftOfInk++; else other++;
    if (samples.length < 5) samples.push(`(${x},${y}) A=${pa} W=${pw} I=${px(i, x, y)}`);
  }
  console.log(`  rows0..15 A!=W: total ${total}; on-glyph ${onInk}; right-of-glyph ${rightOfInk}; left-of-glyph ${leftOfInk}; other ${other}`);
  console.log('  samples: ' + samples.join(' | '));
  let sR = 0, sRlit = 0, sL = 0, sLlit = 0, sB = 0, sBlit = 0; const litCols = new Set(); const litRows = new Set();
  for (let y = 6; y <= 12; y++) for (let x = 1; x < w.width - 1; x++) {
    if (!eq(px(w, x, y), INK)) continue;
    if (eq(px(w, x + 1, y), GROUND)) { sR++; if (!eq(px(a, x + 1, y), GROUND)) { sRlit++; litCols.add(x + 1); litRows.add(y); } }
    if (eq(px(w, x - 1, y), GROUND)) { sL++; if (!eq(px(a, x - 1, y), GROUND)) sLlit++; }
    if (y + 1 < w.height && eq(px(w, x, y + 1), GROUND)) { sB++; if (!eq(px(a, x, y + 1), GROUND)) sBlit++; }
  }
  console.log(`  web glyph px with GROUND right nb: ${sR}, Android nb lit: ${sRlit} | ground LEFT nb: ${sL}, lit: ${sLlit} | ground BELOW: ${sB}, lit: ${sBlit}`);
  const hist = {};
  for (let y = 6; y <= 12; y++) for (let x = 0; x < w.width; x++) if (eq(px(w, x, y), INK)) { const k = px(a, x, y).join(','); hist[k] = (hist[k] || 0) + 1; }
  console.log('  Android bytes at web-glyph positions: ' + JSON.stringify(hist));
  console.log('  lit right-neighbour columns: ' + [...litCols].sort((p, q) => p - q).join(',') + '   rows: ' + [...litRows].sort().join(','));
  const dim = new Set();
  for (let y = 6; y <= 12; y++) for (let x = 0; x < w.width; x++) if (eq(px(w, x, y), INK) && px(a, x, y)[0] === 173) dim.add(x);
  console.log('  Android P-1 (173) glyph columns: ' + [...dim].sort((p, q) => p - q).join(','));
}
for (const s of ['023_Shadow_Simple', '024_Shadow_Colored', '025_Shadow_Multiple', '027_Shadow_Spread', '093_Avatar_Circle',
  '089_Card_Complete', '096_Tooltip_Style', '098_Neumorphic_Light', '026_Shadow_Inset', '107_Edge_InsetRoundShadow',
  '000_Sizing_Fixed', '032_Filter_Blur', '081_Outline_Solid']) bandDiff(s);

console.log('\n\n#### box-edge profiles (R channel) — a horizontal-only resample shows as LEFT != TOP on Android only');
for (const stem of ['023_Shadow_Simple', '024_Shadow_Colored', '025_Shadow_Multiple', '027_Shadow_Spread']) {
  for (const plat of ['Android', 'iOS', 'web']) {
    const im = load(`${B}${plat}__${stem}.png`);
    const H = im.height, W = im.width;
    let bx0 = -1, bx1 = -1; for (let x = 0; x < W; x++) if (px(im, x, 30)[0] === 255) { if (bx0 < 0) bx0 = x; bx1 = x; }
    let by0 = -1, by1 = -1; for (let y = 0; y < H; y++) if (px(im, 30, y)[0] === 255) { if (by0 < 0) by0 = y; by1 = y; }
    const ymid = Math.round((by0 + by1) / 2), xmid = Math.round((bx0 + bx1) / 2);
    const row = []; for (let x = bx0 - 8; x <= bx0 + 2; x++) row.push(px(im, x, ymid)[0]);
    const col = []; for (let y = by0 - 8; y <= by0 + 2; y++) col.push(px(im, xmid, y)[0]);
    const rowR = []; for (let x = bx1 - 2; x <= bx1 + 8; x++) rowR.push(px(im, x, ymid)[0]);
    const colB = []; for (let y = by1 - 2; y <= by1 + 8; y++) colB.push(px(im, xmid, y)[0]);
    console.log(`${stem} ${plat} ${W}x${H} box x=${bx0}..${bx1} y=${by0}..${by1}\n   LEFT  x=${bx0 - 8}..${bx0 + 2}@y${ymid}: ${row.join(' ')}\n   TOP   y=${by0 - 8}..${by0 + 2}@x${xmid}: ${col.join(' ')}\n   RIGHT x=${bx1 - 2}..${bx1 + 8}: ${rowR.join(' ')}\n   BOTTOM y=${by1 - 2}..${by1 + 8}: ${colB.join(' ')}`);
  }
}

console.log('\n\n#### composed-path control — corpus carriers of a BLURRED outset box-shadow (Android vs web capture)');
const S = R + 'tools/titan/runs/wave51-fix/sections/';
for (const [sec, key] of [['filter-effects', 'wpt__filter-effects__backdrop-filter-box-shadow'], ['css-color', 'wpt__css-color__currentcolor-003']]) {
  // web composed captures live in <section>/screenshots/, the natives in <platform>-screenshots/
  const pa = `${S}${sec}/android-screenshots/${key}.png`, pw = `${S}${sec}/screenshots/${key}.png`;
  if (!existsSync(pa) || !existsSync(pw)) { console.log(`  ${key}: missing capture (${pa} / ${pw})`); continue; }
  const a = load(pa), w = load(pw);
  let diff = 0, explainedShift = 0, rightEdges = 0, rightEdgeLit = 0;
  const W = Math.min(a.width, w.width), H = Math.min(a.height, w.height);
  for (let y = 0; y < H; y++) for (let x = 1; x < W - 1; x++) {
    const A = px(a, x, y), Wp = px(w, x, y);
    if (!eq(A, Wp)) { diff++; if (eq(A, px(w, x - 1, y)) || eq(A, px(w, x + 1, y))) explainedShift++; }
    const l = px(w, x - 1, y);
    if (l[0] + 100 < Wp[0] && eq(A, Wp) === false && Math.abs(A[0] - Wp[0]) <= 2) rightEdgeLit++;
    if (l[0] + 100 < Wp[0]) rightEdges++;
  }
  console.log(`  ${key}: A ${a.width}x${a.height} W ${w.width}x${w.height}; A!=W px ${diff} (${(100 * diff / (W * H)).toFixed(2)}%), byte-equal to a web pixel ±1 in x: ${explainedShift}; web dark→light vertical edges ${rightEdges}, of which Android differs by ≤2 LSB: ${rightEdgeLit}`);
}
