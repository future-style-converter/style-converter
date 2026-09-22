// refresh-check.mjs — wave 51 PR 3: the STOP-condition checker for the label-chrome
// baseline refresh (design-record.md §5, S1–S4). It compares ONE fixture's fresh
// captures (tools/visual/report/images/{Android,iOS,web}/NNN_Name.png, i.e. what
// `./test-all.sh <fixture>` just produced) against the committed triplet under
// tools/visual/baseline/ and prints, per stem, the numbers a reviewer needs
// BEFORE `UPDATE_BASELINE=1` is allowed to run:
//   S1  PNG dimensions unchanged per platform and equal across platforms.
//   S2  the new label band: how many of the expected glyph pixels P carry the
//       contract byte (174,174,180) exactly, per platform; whether rows 0..15 are
//       byte-identical across the three fresh captures (must be on every stem
//       whose band carries no component paint — the "band-identical" mode).
//   S3  every pixel that differs from the committed twin OUTSIDE the band, with
//       its bounding box — the old in-box label footprint is the only legitimate
//       region there ("label ink → underlying paint"); anything else is a defect.
//   S4  on glyph-mask stems (band paint under the glyphs), the glyph-mask bytes
//       must agree across platforms within one blend-rounding step.
// Exit status is always 0: this is a measurement for the review, not a gate; the
// gate is the tripwire + BASELINE=1 after the refresh. Usage:
//   node tools/titan/results/wave51-A/refresh-check.mjs [--images DIR] [--out FILE.json]
import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..'); // repo root
const require = createRequire(path.join(ROOT, 'package.json'));                        // pngjs from the workspace
const { PNG } = require('pngjs');
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const IMAGES = path.resolve(ROOT, opt('--images', 'tools/visual/report/images'));    // fresh captures
const BASELINE = path.join(ROOT, 'tools/visual/baseline');                              // committed twins
const OUT = opt('--out', null);
const FONT = JSON.parse(readFileSync(path.join(ROOT, 'tools/visual/block-font.json'), 'utf8')); // THE atlas
const PLATFORMS = ['web', 'iOS', 'Android'];                                            // report dir names == baseline prefixes
const ORIGIN = { x: 8, y: 6 }, EDGE = 8, BAND = 16;                                     // shared-spec geometry (tripwire mirrors)
const GROUND = [0x1a, 0x1a, 0x2e], CONTRACT = [174, 174, 180];                          // canvas ground; composited label byte

// ── shared-spec label layout, mirrored from tools/visual/label-chrome-tripwire.test.mjs ──
const normalize = (s) => Array.from(s.toUpperCase()).map((ch) => (FONT.glyphs[ch] ? ch : '-')).join('');
const truncatedCount = (len, w) => { const b = w - ORIGIN.x - EDGE; return b <= 0 ? 0 : Math.min(len, Math.floor(b / FONT.advance)); };
function glyphPixels(name, w) {                                                         // set of "x,y" keys for the expected label
  const chars = normalize(name.replace(/_/g, ' ')), n = truncatedCount(chars.length, w), P = new Set();
  for (let i = 0; i < n; i++) { const rows = FONT.glyphs[chars[i]]; for (let r = 0; r < rows.length; r++) for (let c = 0; c < rows[r].length; c++) if (rows[r][c] === '1') P.add(`${ORIGIN.x + i * FONT.advance + c},${ORIGIN.y + r}`); }
  return P;
}
const px = (png, x, y) => { const i = (y * png.width + x) * 4; return [png.data[i], png.data[i + 1], png.data[i + 2]]; };
const eq = (a, b, tol = 0) => Math.abs(a[0] - b[0]) <= tol && Math.abs(a[1] - b[1]) <= tol && Math.abs(a[2] - b[2]) <= tol;
const load = (f) => (existsSync(f) ? PNG.sync.read(readFileSync(f)) : null);

// ── stems present in the fresh capture (all three platforms) ──
const stems = readdirSync(path.join(IMAGES, 'web')).filter((f) => f.endsWith('.png')).sort();
const report = [];
for (const file of stems) {
  const stem = file.replace(/\.png$/, ''), name = stem.replace(/^\d+_/, '');
  const fresh = Object.fromEntries(PLATFORMS.map((p) => [p, load(path.join(IMAGES, p, file))]));
  const old = Object.fromEntries(PLATFORMS.map((p) => [p, load(path.join(BASELINE, `${p}__${file}`))]));
  if (PLATFORMS.some((p) => !fresh[p])) { report.push({ stem, error: 'fresh capture missing on a platform' }); continue; }
  const row = { stem, name, s1: {}, s2: {}, s3: {}, mode: null, crossBandIdentical: null, s4: null };
  // S1 — dimensions.
  for (const p of PLATFORMS) row.s1[p] = old[p] ? `${fresh[p].width}x${fresh[p].height}${fresh[p].width === old[p].width && fresh[p].height === old[p].height ? '' : ` (WAS ${old[p].width}x${old[p].height})`}` : `${fresh[p].width}x${fresh[p].height} (no baseline)`;
  row.s1.equalAcross = PLATFORMS.every((p) => fresh[p].width === fresh.web.width && fresh[p].height === fresh.web.height);
  // S2 — the band: P bytes and cross-platform identity of rows 0..15.
  const P = glyphPixels(name, fresh.web.width);
  let bandPaintOutsideP = false;
  for (const p of PLATFORMS) {
    let exact = 0, nonGround = 0;
    for (const key of P) { const [x, y] = key.split(',').map(Number); if (x >= fresh[p].width || y >= fresh[p].height) continue; const v = px(fresh[p], x, y); if (eq(v, CONTRACT)) exact++; if (!eq(v, GROUND, 1)) nonGround++; }
    for (let y = 0; y < Math.min(BAND, fresh[p].height) && !bandPaintOutsideP; y++) for (let x = 0; x < fresh[p].width; x++) if (!P.has(`${x},${y}`) && !eq(px(fresh[p], x, y), GROUND, 1)) { bandPaintOutsideP = true; break; }
    row.s2[p] = { P: P.size, exact, nonGround };
  }
  row.mode = bandPaintOutsideP ? 'glyph-mask' : 'band-identical';
  let bandDiff = 0;
  for (let y = 0; y < Math.min(BAND, fresh.web.height); y++) for (let x = 0; x < fresh.web.width; x++) { const a = px(fresh.web, x, y); if (!eq(a, px(fresh.iOS, x, y)) || !eq(a, px(fresh.Android, x, y))) bandDiff++; }
  row.crossBandIdentical = bandDiff === 0 ? true : `${bandDiff} px differ`;
  // S4 — glyph-mask stems: the paint UNDER the glyphs legitimately differs per platform
  // (penumbra, blur, transforms), so cross-platform byte agreement is informational only.
  // The real check (S4b) is per platform: every fresh glyph pixel must equal the label ink
  // (237,237,237) src-over composited at that platform's alpha byte (web 180, natives 179)
  // onto the COMMITTED twin's pixel at the same spot — the committed baselines carry no
  // chrome in the band, so that pixel IS the platform's own underlying paint — within ±1.
  if (row.mode === 'glyph-mask') {
    let off = 0; for (const key of P) { const [x, y] = key.split(',').map(Number); const a = px(fresh.web, x, y); if (!eq(a, px(fresh.iOS, x, y), 1) || !eq(a, px(fresh.Android, x, y), 1)) off++; }
    row.s4 = off === 0 ? 'ok' : `${off} glyph px differ by >1 across platforms (informational)`;
    row.s4b = {};
    for (const p of PLATFORMS) {
      if (!old[p] || old[p].width !== fresh[p].width || old[p].height !== fresh[p].height) { row.s4b[p] = 'no comparable baseline'; continue; }
      const alpha = p === 'web' ? 180 : 179; let bad = 0, worst = 0;
      for (const key of P) { const [x, y] = key.split(',').map(Number); const u = px(old[p], x, y), a = px(fresh[p], x, y);
        const expect = u.map((c) => (237 * alpha + c * (255 - alpha)) / 255); const d = Math.max(...expect.map((e, i) => Math.abs(e - a[i])));
        if (d > 1.5) bad++; if (d > worst) worst = d; }
      row.s4b[p] = bad === 0 ? 'ok' : `${bad}/${P.size} glyph px are not ink-over-committed (worst Δ ${worst.toFixed(1)})`;
    }
  }
  // S3 — diff vs the committed twin, split by region.
  for (const p of PLATFORMS) {
    if (!old[p] || old[p].width !== fresh[p].width || old[p].height !== fresh[p].height) { row.s3[p] = old[p] ? 'dims differ — see S1' : 'no baseline'; continue; }
    let inBand = 0, outside = 0, oldInk = 0, x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let y = 0; y < fresh[p].height; y++) for (let x = 0; x < fresh[p].width; x++) {
      const a = px(fresh[p], x, y), b = px(old[p], x, y); if (eq(a, b)) continue;
      if (y < BAND) { inBand++; continue; }
      outside++; if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y;
      // "label ink → underlying paint": the committed pixel was light, near-grey label ink and the fresh one is not.
      const lum = (b[0] + b[1] + b[2]) / 3, grey = Math.max(b[0], b[1], b[2]) - Math.min(b[0], b[1], b[2]) <= 14;
      if (lum >= 150 && grey && (a[0] + a[1] + a[2]) / 3 < lum) oldInk++;
    }
    row.s3[p] = { inBand, outside, oldInkLike: oldInk, bbox: outside ? [x0, y0, x1, y1] : null };
  }
  report.push(row);
}
// ── print ──
for (const r of report) {
  if (r.error) { console.log(`${r.stem}: ${r.error}`); continue; }
  const s2 = PLATFORMS.map((p) => `${p[0]}:${r.s2[p].exact}/${r.s2[p].P}`).join(' ');
  const s3 = PLATFORMS.map((p) => (typeof r.s3[p] === 'string' ? `${p[0]}:${r.s3[p]}` : `${p[0]}:${r.s3[p].outside}out(${r.s3[p].oldInkLike}ink)${r.s3[p].bbox ? '@' + r.s3[p].bbox.join(',') : ''}`)).join(' ');
  const s1 = r.s1.equalAcross && !Object.values(r.s1).some((v) => typeof v === 'string' && v.includes('WAS')) ? 'ok' : JSON.stringify(r.s1);
  console.log(`${r.stem.padEnd(34)} S1:${s1} ${r.mode.padEnd(14)} S2[${s2}] band×3:${r.crossBandIdentical === true ? 'identical' : r.crossBandIdentical} ${r.s4 ? 'S4:' + r.s4 + ' S4b[' + PLATFORMS.map((p) => p[0] + ':' + r.s4b[p]).join(' ') + ']' : ''} S3[${s3}]`);
}
if (OUT) writeFileSync(OUT, JSON.stringify(report, null, 1) + '\n');
console.log(`stems: ${report.length}; band-identical: ${report.filter((r) => r.mode === 'band-identical').length}; glyph-mask: ${report.filter((r) => r.mode === 'glyph-mask').length}; cross-band identical: ${report.filter((r) => r.crossBandIdentical === true).length}; S1 issues: ${report.filter((r) => !r.error && (!r.s1.equalAcross || Object.values(r.s1).some((v) => typeof v === 'string' && v.includes('WAS')))).length}`);
