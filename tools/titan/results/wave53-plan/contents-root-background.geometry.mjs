#!/usr/bin/env node
// tools/titan/results/wave53-plan/contents-root-background.geometry.mjs
//
// Geometry replay for the brief `contents-root-background.md` §5/§6: which
// canvas surface (framed outer surface vs ICB-only) and which tile origin the
// propagated root background-image must use. Scores simulated captures with
// the scorer's SSIM call (ssim.js 'fast', inject-wpt-block.mjs:478). Reads 5
// PNGs; every simulation is built from the frozen refs themselves (the
// margin-root patterns vary along y only, with a 100-px period) or from the
// wave52-ship captures (the target's text), so no renderer is imitated.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';

const ROOT = new URL('../../../../', import.meta.url).pathname;
const REFS = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const SHIP = `${ROOT}tools/titan/runs/wave52-ship/sections`;
const load = (p) => PNG.sync.read(readFileSync(p));
const score = (a, b) => ssim({ data: new Uint8ClampedArray(a.data), width: a.width, height: a.height },
                             { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height },
                             { ssim: 'fast' }).mssim.toFixed(4);
const F = 16;                                             // CANVAS_PAD_PX / CaptureCanvasFrame
const inFrame = (x, y, w, h) => x < F || x >= w - F || y < F || y >= h - F;
const blank = (w, h) => new PNG({ width: w, height: h });
const put = (png, x, y, rgb) => { const i = (y * png.width + x) * 4; png.data[i] = rgb[0]; png.data[i + 1] = rgb[1]; png.data[i + 2] = rgb[2]; png.data[i + 3] = 255; };
const get = (png, x, y) => { const i = (y * png.width + x) * 4; return [png.data[i], png.data[i + 1], png.data[i + 2]]; };

// ── target: display-contents-root-background — frame painted vs ICB-only ──
{
  const S = 'css-display', ST = 'display-contents-root-background';
  const ref = load(`${REFS}/${S}/${ST}.png`);
  for (const [plat, dir] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    const cap = load(`${SHIP}/${S}/${dir}/wpt__${S}__${ST}.png`);
    const framed = blank(cap.width, cap.height), icbOnly = blank(cap.width, cap.height);
    for (let y = 0; y < cap.height; y++) for (let x = 0; x < cap.width; x++) {
      const v = get(cap, x, y).reduce((a, b) => a + b) / 3;          // black ink over white
      const g = [0, Math.round(128 * v / 255), 0];                    // same ink over the 1x1 tile
      put(framed, x, y, g);
      put(icbOnly, x, y, inFrame(x, y, cap.width, cap.height) ? [255, 255, 255] : g);
    }
    console.log(`target ${plat.padEnd(8)} framed-surface ${score(framed, ref)}  icb-only(frame white) ${score(icbOnly, ref)}`);
  }
}

// ── carriers: background-attachment-margin-root-001/-002 ──
// The ref interior (x 16..373, y 16..583) holds the true tiling; it is constant
// along x and 100-periodic along y, so the ref's own row y±100k is the
// pattern at any row, including the frame rows the ref leaves white.
for (const n of ['001', '002']) {
  const S = 'css-backgrounds', ST = `background-attachment-margin-root-${n}`;
  const ref = load(`${REFS}/${S}/${ST}.png`);
  const { width: w, height: h } = ref;
  const row = (y) => { let yy = y; while (yy < F) yy += 100; while (yy >= h - F) yy -= 100; return get(ref, w >> 1, yy); };
  const variants = {
    'spec origin, framed surface': (x, y) => row(y),
    'origin 50px off spec (001: ICB origin for its scroll layer; 002: root-box origin for its fixed layer), framed surface': (x, y) => row(y + 50),
    'origin 50px off spec, ICB-only (frame white)': (x, y) => inFrame(x, y, w, h) ? [255, 255, 255] : row(y + 50),
  };
  const out = [];
  for (const [name, fn] of Object.entries(variants)) {
    const png = blank(w, h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(png, x, y, fn(x, y));
    out.push(`${name} ${score(png, ref)}`);
  }
  for (const [plat, dir] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    out.push(`shipped ${plat} ${score(load(`${SHIP}/${S}/${dir}/wpt__${S}__${ST}.png`), ref)}`);
  }
  console.log(`${ST}\n  ${out.join('\n  ')}`);
}

// ── carriers, per platform, full fix (spec origin per layer, ICB-only, white
// frame): built from EACH platform's own shipped gradient box (x 66..323,
// y 66..365 — three whole 100-px tiles anchored at the box origin y=66), so
// the native gradient rasterisation is the platform's own, not the ref's.
for (const n of ['001', '002']) {
  const S = 'css-backgrounds', ST = `background-attachment-margin-root-${n}`;
  const ref = load(`${REFS}/${S}/${ST}.png`);
  const { width: w, height: h } = ref;
  const origin = n === '001' ? 66 : 16;          // scroll layer → root box (66); fixed → ICB (16)
  const line = [];
  for (const [plat, dir] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    const cap = load(`${SHIP}/${S}/${dir}/wpt__${S}__${ST}.png`);
    const phaseRow = (y) => 66 + ((((y - origin) % 100) + 100) % 100);   // the box row at the same tile phase
    const png = blank(w, h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(png, x, y, inFrame(x, y, w, h) ? [255, 255, 255] : get(cap, 200, phaseRow(y)));
    line.push(`${plat} ${score(png, ref)}`);
  }
  console.log(`${ST} full fix (own-platform tiles, ICB-only, white frame): ${line.join('  ')}`);
}
