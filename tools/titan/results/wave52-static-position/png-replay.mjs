#!/usr/bin/env node
// wave-52 lane L7 · static-position — PNG replay: the predicted post-fix
// picture of every L7 mover, simulated on the FROZEN wave51-fix captures and
// scored against the frozen refs with the pipeline's own SSIM (ssim.js
// `fast` on RGBA, same-size pairs — inject-wpt-block.mjs; the L2 replay's
// method). Read-only; writes png-replay.json beside itself.
//
//  NATIVES: static-position.census.json `moves` (per platform, per component,
//  dx/dy = new − old px). Each component's mark is found by COLOUR (its own
//  IR BackgroundColor) and assigned to its parent container (the parent's IR
//  BackgroundColor, connected components sorted row-major = document order of
//  floated containers); a single-container doc uses the whole frame. The
//  mark's pixels move by (dx, dy); vacated pixels take the container colour
//  inside its box, white outside. A doc whose container count does not match
//  the IR is reported UNMAPPED, never guessed.
//  WEB (T3): the browser owns the fallback once the keyword arrives, so the
//  post-fix web picture is the ref wherever the capture differs ONLY in mark
//  ink: reported as the count of differing pixels outside both images' mark
//  colour (0 ⇒ the predicted post-fix score is 1.0000).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const req = createRequire(path.join(ROOT, 'package.json'));
const { PNG } = req('pngjs');
const { ssim } = req('ssim.js');
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const census = JSON.parse(fs.readFileSync(path.join(HERE, 'static-position.census.json'), 'utf8'));
const manifests = new Map();
const irOf = (section, test) => {
  const stem = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__');
  return JSON.parse(fs.readFileSync(path.join(RUN, section, 'per-test-ir', `wpt__${stem}.json`), 'utf8'));
};
/** Frozen ref + capture PNGs and the recorded cell for one test/platform. */
function load(section, test, platform) {
  if (!manifests.has(section)) manifests.set(section, JSON.parse(fs.readFileSync(path.join(RUN, section, 'manifest.json'), 'utf8')));
  const res = manifests.get(section).wpt.results[test];
  const stem = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__');
  const cap = PNG.sync.read(fs.readFileSync(path.join(RUN, section, DIRS[platform], `wpt__${stem}.png`)));
  const ref = PNG.sync.read(fs.readFileSync(path.join(ROOT, res.browserRef.path)));
  return { cap, ref, cell: res.browserRef.diffs[`${platform}-ref`] };
}
/** The pipeline's score on a same-size pair (4 decimals, like the manifest). */
function score(a, b) {
  if (a.width !== b.width || a.height !== b.height) return null;
  const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
  return +ssim(img(a), img(b), { ssim: 'fast' }).mssim.toFixed(4);
}
const clone = (p) => { const o = new PNG({ width: p.width, height: p.height }); p.data.copy(o.data); return o; };
const at = (p, x, y) => (y * p.width + x) * 4;
// IR sRGB (0–1) → 0–255 triple; colour match within ±40 per channel.
const rgbOf = (c) => { const d = (c.properties || []).find((p) => p.type === 'BackgroundColor')?.data?.srgb; return d ? [d.r, d.g, d.b].map((v) => Math.round(v * 255)) : null; };
const near = (p, i, rgb) => Math.abs(p.data[i] - rgb[0]) <= 40 && Math.abs(p.data[i + 1] - rgb[1]) <= 40 && Math.abs(p.data[i + 2] - rgb[2]) <= 40;
/** 4-connected components of one colour (or of ANY of several colours when
 *  `rgb` is a list of triples): [{x0,y0,x1,y1,px:[[x,y]...]}]. */
function blobs(p, rgb) {
  const list = Array.isArray(rgb[0]) ? rgb : [rgb];
  const hit = (i) => list.some((c) => near(p, i, c));
  const seen = new Uint8Array(p.width * p.height); const out = [];
  for (let y = 0; y < p.height; y++) for (let x = 0; x < p.width; x++) {
    const k = y * p.width + x; if (seen[k] || !hit(k * 4)) continue;
    const b = { x0: x, y0: y, x1: x, y1: y, px: [] }; const st = [[x, y]]; seen[k] = 1;
    while (st.length) {
      const [cx, cy] = st.pop(); b.px.push([cx, cy]);
      b.x0 = Math.min(b.x0, cx); b.y0 = Math.min(b.y0, cy); b.x1 = Math.max(b.x1, cx); b.y1 = Math.max(b.y1, cy);
      for (const [nx, ny] of [[cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]]) {
        if (nx < 0 || ny < 0 || nx >= p.width || ny >= p.height) continue;
        const nk = ny * p.width + nx; if (seen[nk] || !hit(nk * 4)) continue; seen[nk] = 1; st.push([nx, ny]);
      }
    }
    out.push(b);
  }
  return out;
}
/** Row-major order of floated boxes: rows by top edge (±3 px), then x. */
function rowMajor(list) {
  const s = [...list].sort((a, b) => a.y0 - b.y0 || a.x0 - b.x0); const rows = [];
  for (const b of s) { const r = rows.find((rr) => Math.abs(rr.y - b.y0) <= 3); if (r) r.items.push(b); else rows.push({ y: b.y0, items: [b] }); }
  return rows.flatMap((r) => r.items.sort((a, b) => a.x0 - b.x0));
}

const result = { method: 'ssim.js fast, RGBA, same-size; see header', natives: [], web: [] };
// ── NATIVES ──
const groups = new Map();
for (const m of census.moves) { const k = `${m.section}|${m.test}|${m.platform}`; if (!groups.has(k)) groups.set(k, []); groups.get(k).push(m); }
for (const [k, moves] of groups) {
  const [section, test, platform] = k.split('|');
  const { cap, ref, cell } = load(section, test, platform);
  const doc = irOf(section, test); const byId = new Map(doc.components.map((c) => [c.id, c]));
  // Every container of an out-of-flow child in doc order (moved or not).
  const containerIds = [...new Set(doc.components.filter((c) => /"(ABSOLUTE)"/.test(JSON.stringify((c.properties || []).find((p) => p.type === 'Position') || {})) && byId.has(c.slot?.parent)).map((c) => c.slot.parent))];
  const row = { section, test, platform, recorded: cell?.ssim, wptPass: cell?.wptPass, now: score(cap, ref), moved: moves.length };
  let boxes;
  if (containerIds.length === 1) boxes = [{ x0: 0, y0: 0, x1: cap.width - 1, y1: cap.height - 1 }];
  else {
    // A container = a connected region of its own colour UNION its marks'
    // colours (a tiny container can be almost all mark; the mark is inside
    // or hangs off its own container, and black borders separate them).
    const crgb = rgbOf(byId.get(containerIds[0]));
    const markRgbs = [...new Set(doc.components.filter((c) => containerIds.includes(c.slot?.parent)).map((c) => JSON.stringify(rgbOf(c))))].map((x) => JSON.parse(x)).filter(Boolean);
    const found = crgb ? rowMajor(blobs(cap, [crgb, ...markRgbs])) : [];
    if (found.length !== containerIds.length) { row.unmapped = `containers IR ${containerIds.length} vs capture ${found.length}`; result.natives.push(row); continue; }
    boxes = found;
  }
  const out = clone(cap); const fill = [];
  for (const m of moves) {
    const ci = containerIds.indexOf(m.parent); const box = boxes[ci]; const comp = byId.get(m.id); const mrgb = rgbOf(comp);
    // Vacated ink takes the container's own background (white when none).
    const crgb = rgbOf(byId.get(m.parent)) ?? [255, 255, 255];
    if (!mrgb || !box) { row.unmapped = `no mark colour / box for ${m.id}`; break; }
    // Mark blobs assigned to this container: centre inside its box (±2 px).
    const mine = blobs(cap, mrgb).filter((b) => { const cx = (b.x0 + b.x1) / 2; const cy = (b.y0 + b.y1) / 2; return cx >= box.x0 - 2 && cx <= box.x1 + 2 && cy >= box.y0 - 2 && cy <= box.y1 + 2; });
    for (const b of mine) for (const [x, y] of b.px) {
      // Vacate: the container colour inside its box, white outside.
      const inside = x >= box.x0 && x <= box.x1 && y >= box.y0 && y <= box.y1;
      out.data.set(inside ? [...crgb, 255] : [255, 255, 255, 255], at(out, x, y));
      fill.push([x + Math.round(m.dx), y + Math.round(m.dy), [...cap.data.subarray(at(cap, x, y), at(cap, x, y) + 4)]]);
    }
  }
  for (const [x, y, c] of fill) if (x >= 0 && y >= 0 && x < out.width && y < out.height) out.data.set(c, at(out, x, y));
  if (!row.unmapped) row.after = score(out, ref);
  result.natives.push(row);
}
// ── WEB (T3): differing pixels NOT explained by mark ink ──
const webDocs = [...new Set(census.T3.filter((r) => r.wireNew?.typed && ['LAST_BASELINE', 'NORMAL'].includes(r.wireNew.typed)).map((r) => `${r.section}|${r.test}`))];
for (const k of webDocs) {
  const [section, test] = k.split('|');
  const { cap, ref, cell } = load(section, test, 'web');
  const doc = irOf(section, test);
  // Mark colours: every T3 carrier's own background.
  const marks = [...new Set(census.T3.filter((r) => r.test === test).map((r) => JSON.stringify(rgbOf(doc.components.find((c) => c.id === r.id)))))].map((s) => JSON.parse(s)).filter(Boolean);
  let diff = 0; let unexplained = 0;
  for (let i = 0; i < cap.data.length; i += 4) {
    if (cap.data[i] === ref.data[i] && cap.data[i + 1] === ref.data[i + 1] && cap.data[i + 2] === ref.data[i + 2]) continue;
    diff++; if (!marks.some((m) => near(cap, i, m) || near(ref, i, m))) unexplained++;
  }
  result.web.push({ section, test, recorded: cell?.ssim, wptPass: cell?.wptPass, differingPx: diff, unexplainedPx: unexplained, predicted: unexplained === 0 ? 1 : null });
}
// ── UNMAPPED Android docs: the iOS capture as the post-T1 proxy ──
// iOS's grid overlay already aligns by the REAL box size (no RC1 zero mount),
// so where the two natives' captures differ ONLY in mark ink, the post-T1
// Android picture is the iOS one (before T3's own iOS moves). Reported as
// android-vs-ios differing pixels outside mark colour (0 ⇒ proxy valid).
result.proxy = [];
for (const r of result.natives.filter((n) => n.unmapped && n.platform === 'android')) {
  const a = load(r.section, r.test, 'android'); const i = load(r.section, r.test, 'ios');
  const doc = irOf(r.section, r.test);
  const marks = [...new Set(doc.components.filter((c) => (c.properties || []).some((p) => p.type === 'Position' && p.data === 'ABSOLUTE')).map((c) => JSON.stringify(rgbOf(c))))].map((x) => JSON.parse(x)).filter(Boolean);
  let diff = 0; let unexplained = 0;
  if (a.cap.width === i.cap.width && a.cap.height === i.cap.height) {
    for (let k = 0; k < a.cap.data.length; k += 4) {
      if (a.cap.data[k] === i.cap.data[k] && a.cap.data[k + 1] === i.cap.data[k + 1] && a.cap.data[k + 2] === i.cap.data[k + 2]) continue;
      diff++; if (!marks.some((m) => near(a.cap, k, m) || near(i.cap, k, m))) unexplained++;
    }
  }
  result.proxy.push({ section: r.section, test: r.test, android: a.cell?.ssim, ios: i.cell?.ssim, differingPx: diff, unexplainedPx: unexplained });
}
fs.writeFileSync(path.join(HERE, 'png-replay.json'), JSON.stringify(result, null, 1) + '\n');
for (const r of result.proxy) console.log('proxy   ', r.test.replace('css/', '').padEnd(92), 'android', r.android, 'ios', r.ios, 'diff', r.differingPx, 'unexplained', r.unexplainedPx);
for (const r of result.natives) console.log(r.platform.padEnd(8), (r.test.replace('css/', '')).padEnd(92), r.wptPass ? 'P' : 'f', r.recorded, r.now, '→', r.after ?? r.unmapped);
for (const r of result.web) console.log('web     ', r.test.replace('css/', '').padEnd(92), r.wptPass ? 'P' : 'f', r.recorded, 'diff', r.differingPx, 'unexplained', r.unexplainedPx);
