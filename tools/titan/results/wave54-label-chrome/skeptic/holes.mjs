// skeptic/holes.mjs — wave 54 L7 SKEPTIC: what the exempt path (checkExempt = P = ∅ + clause (iv)) lets through, on the
// REAL seeded 77fe41e8 PNGs of the three exempt stems. Each case paints the stem's own would-be label (ink 237 at
// alpha 179 src-over, the chrome's byte) into copies of the decoded PNGs and prints the verdict.
//   node tools/titan/results/wave54-label-chrome/skeptic/holes.mjs
import { readFileSync } from 'node:fs';                     // PNG bytes
import path from 'node:path';                               // joins
import { fileURLToPath, pathToFileURL } from 'node:url';    // ESM paths
const HERE = path.dirname(fileURLToPath(import.meta.url));  // skeptic dir
const ROOT = path.resolve(HERE, '../../../../..');           // repo root
const C = await import(pathToFileURL(path.join(ROOT, 'tools/visual/label-chrome-check.mjs')).href); // U1 checker
const { PNG } = await import(pathToFileURL(path.join(ROOT, 'node_modules/pngjs/lib/png.js')).href);  // decoder
const SEED = path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8'); // the 18 PNGs
const load = (stem) => C.PLATFORMS.map((p) => PNG.sync.read(readFileSync(path.join(SEED, `${p}__${stem}.png`)))); // fresh decode
const ink = (png, name, { dx = 0, dy = 0, keep = Infinity } = {}) => {  // draw the would-be label, shifted / truncated
  C.glyphPixels(name, png.width).slice(0, keep).forEach(([x, y]) => { const i = ((y + dy) * png.width + x + dx) * 4; for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((C.INK[c] * C.INK_ALPHA + png.data[i + c] * (255 - C.INK_ALPHA)) / 255); });
};
const show = (label, v) => console.log(`${label.padEnd(78)} → ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'GREEN'} [${v.mode}]`);
for (const stem of ['001_ATC_PropsThenAll_InGreenParent', '003_ATC_InitialUnderRedParent', '005_ATC_DirectionSurvives']) {
  const name = stem.slice(4);                                                     // component name
  let t = load(stem); show(`${stem} as seeded`, C.checkExempt(t, name));
  t = load(stem); t.forEach((p) => ink(p, name)); show(`  label drawn x3 at the spec origin`, C.checkExempt(t, name));
  t = load(stem); ink(t[2], name); show(`  label drawn on web only at the spec origin`, C.checkExempt(t, name));
  t = load(stem); t.forEach((p) => ink(p, name, { dx: 1 })); show(`  label drawn x3, origin off by +1 px (x)`, C.checkExempt(t, name));
  t = load(stem); ink(t[2], name, { dx: 1 }); show(`  label drawn on web only, origin off by +1 px (x)`, C.checkExempt(t, name));
  t = load(stem); ink(t[0], name, { dy: 1 }); show(`  label drawn on Android only, origin off by +1 px (y)`, C.checkExempt(t, name));
  t = load(stem); t.forEach((p) => ink(p, name, { keep: 100 })); show(`  first 100 label px drawn x3 (a truncated label)`, C.checkExempt(t, name));
  t = load(stem); ink(t[1], 'SOMETHING ELSE'); show(`  iOS draws a DIFFERENT label in the band`, C.checkExempt(t, name));
}
// Contrast: the label-due path (checkTriplet) on 000 with its label shifted by +1 px on one platform.
const s = '000_ATC_AllThenProps'; const t0 = load(s);
for (const [x, y] of C.glyphPixels(s.slice(4), t0[2].width)) { const i = (y * t0[2].width + x) * 4; t0[2].data.set(C.GROUND, i); } // erase web label
ink(t0[2], s.slice(4), { dx: 1 }); show(`${s} (label-due) web label moved +1 px (x)`, C.checkTriplet(t0, s.slice(4)));
