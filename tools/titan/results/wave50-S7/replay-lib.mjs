// S7 (wave 50) — independent replay harness for the builder lanes' predicted
// cell flips. Uses the campaign's OWN scorer entry points (no re-implementation):
// diffComposedVsRef / diffPlatformVsRef from tools/titan/inject-wpt-block.mjs,
// which internally run diffWebVsRef + checkFuzzyMatch + computePresenceFailed +
// computeColorFailed + computeCoverageRatioFailed + computeNovelInkFailed +
// degenerateVetoFailed + computeWptPass exactly as the gate does.
//
// Contract: a replay is only trustworthy if the harness first reproduces the
// wave49-final manifest numbers EXACTLY on the UNMODIFIED capture. validate()
// below does that assertion; every mutation replay calls it first.
import { promises as fsp, readFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { diffComposedVsRef, diffPlatformVsRef, safe } from '../../inject-wpt-block.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
export const REPO = join(HERE, '..', '..', '..', '..');
export const RUN = join(REPO, 'tools/titan/runs/wave49-final/sections');
export const REFS = join(REPO, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins');
export const SCRATCH = process.env.S7_SCRATCH || '/tmp/s7-replay';

export const PLAT_DIR = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
export const DIFF_KEY = { web: 'web-ref', ios: 'ios-ref', android: 'android-ref' };

const _mcache = new Map();
export function manifest(section) {
  if (!_mcache.has(section)) _mcache.set(section, JSON.parse(readFileSync(join(RUN, section, 'manifest.json'), 'utf8')));
  return _mcache.get(section);
}

/** Resolve a test row + its capture/ref paths. `test` is the manifest key
 *  (e.g. 'css/css-values/angle-units-001.html'). */
export function cell(section, test, platform) {
  const m = manifest(section);
  const row = m.wpt.results[test];
  if (!row) throw new Error(`no row for ${test} in ${section}`);
  const stem = test.replace(/^css\//, '').replace(/\.html$/, '').split('/').slice(1).join('__');
  const testKey = `wpt__${row.specSection}__${stem}`;
  const capDir = join(RUN, section, PLAT_DIR[platform]);
  const capPath = join(capDir, `${safe(testKey)}.png`);
  const refPath = join(REFS, row.specSection, `${stem}.png`);
  return { row, testKey, capDir, capPath, refPath, fuzzy: row.fuzzy,
           recorded: row.browserRef?.diffs?.[DIFF_KEY[platform]] ?? null };
}

/** Score ONE png against the ref through the gate's composed path. */
export async function scorePng(pngPath, testKey, refPath, fuzzy) {
  const dir = join(SCRATCH, 'box', Math.random().toString(36).slice(2));
  mkdirSync(dir, { recursive: true });
  const dest = join(dir, `${safe(testKey)}.png`);
  await fsp.copyFile(pngPath, dest);
  return diffComposedVsRef({ platformDir: dir, testKey, refPng: refPath, fuzzy });
}

/** Validate the harness on the untouched capture: every scoring field must
 *  equal the manifest's recorded value. Returns {ok, diffs[]}. */
export async function validate(section, test, platform) {
  const c = cell(section, test, platform);
  if (!existsSync(c.capPath)) return { ok: false, reason: 'no composed capture', c };
  const got = await scorePng(c.capPath, c.testKey, c.refPath, c.fuzzy);
  const rec = c.recorded;
  if (!rec) return { ok: false, reason: 'no recorded diff', c, got };
  const fields = ['ssim', 'pixelMismatchedCount', 'fuzzyDifferingPixels', 'fuzzyMaxChannelDelta',
    'presenceFailed', 'colorFailed', 'coverageRatioFailed', 'novelInkFailed', 'wptPass', 'wptFuzzyMatch'];
  const bad = [];
  for (const f of fields) {
    const a = JSON.stringify(got?.[f]); const b = JSON.stringify(rec?.[f]);
    if (a !== b) bad.push(`${f}: replay=${a} manifest=${b}`);
  }
  return { ok: bad.length === 0, diffs: bad, c, got, rec };
}

// ── PNG mutation helpers (all pure buffer ops on a COPY) ────────────────────
export async function readPng(p) { return PNG.sync.read(await fsp.readFile(p)); }
export async function writePng(png, p) { mkdirSync(dirname(p), { recursive: true }); await fsp.writeFile(p, PNG.sync.write(png)); return p; }
export function clonePng(src) {
  const out = new PNG({ width: src.width, height: src.height });
  src.data.copy(out.data); return out;
}
/** Repaint every pixel matching `from` (exact RGB) to `to`. Returns count. */
export function repaint(png, from, to) {
  let n = 0;
  for (let i = 0; i < png.data.length; i += 4) {
    if (png.data[i] === from[0] && png.data[i+1] === from[1] && png.data[i+2] === from[2]) {
      png.data[i] = to[0]; png.data[i+1] = to[1]; png.data[i+2] = to[2]; n++;
    }
  }
  return n;
}
/** Fill an axis-aligned box with an RGB colour. */
export function fillBox(png, x0, y0, x1, y1, rgb) {
  let n = 0;
  for (let y = Math.max(0, y0); y < Math.min(png.height, y1); y++)
    for (let x = Math.max(0, x0); x < Math.min(png.width, x1); x++) {
      const i = (y * png.width + x) * 4;
      png.data[i] = rgb[0]; png.data[i+1] = rgb[1]; png.data[i+2] = rgb[2]; png.data[i+3] = 255; n++;
    }
  return n;
}
/** Shift a horizontal band of rows [yFrom,yTo) up by `dy`, white-filling the tail. */
export function shiftUp(src, dy) {
  const out = new PNG({ width: src.width, height: src.height });
  out.data.fill(0xFF);
  for (let y = dy; y < src.height; y++) {
    const s = y * src.width * 4, d = (y - dy) * src.width * 4;
    src.data.copy(out.data, d, s, s + src.width * 4);
  }
  return out;
}
/** Crop/pad to WxH (top-left aligned, white fill). */
export function frame(src, W, H) {
  const out = new PNG({ width: W, height: H });
  out.data.fill(0xFF);
  const cw = Math.min(src.width, W), ch = Math.min(src.height, H);
  for (let y = 0; y < ch; y++) {
    const s = y * src.width * 4;
    src.data.copy(out.data, y * W * 4, s, s + cw * 4);
  }
  return out;
}
/** Ink bbox against white canvas (tolerance 8, the scorer's SEMANTIC_PRESENCE_TOLERANCE). */
export function inkBBox(png, tol = 8) {
  let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1, n = 0;
  for (let y = 0; y < png.height; y++) for (let x = 0; x < png.width; x++) {
    const i = (y * png.width + x) * 4;
    if (Math.abs(png.data[i]-255) > tol || Math.abs(png.data[i+1]-255) > tol || Math.abs(png.data[i+2]-255) > tol) {
      n++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
    }
  }
  return { x0, y0, x1, y1, inkPx: n };
}
/** Count pixels whose max per-channel delta between two PNGs exceeds `d`. */
export function deltaOver(a, b, d) {
  const W = Math.min(a.width, b.width), H = Math.min(a.height, b.height);
  let n = 0, max = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y*a.width+x)*4, j = (y*b.width+x)*4;
    const m = Math.max(Math.abs(a.data[i]-b.data[j]), Math.abs(a.data[i+1]-b.data[j+1]), Math.abs(a.data[i+2]-b.data[j+2]));
    if (m > max) max = m;
    if (m > d) n++;
  }
  return { count: n, max, W, H };
}
export function sameSize(a, b) { return a.width === b.width && a.height === b.height; }
