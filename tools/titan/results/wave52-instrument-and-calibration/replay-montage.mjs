#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/replay-montage.mjs
//
// wave-52 L12 PNG replay: one row per test — the RE-FROZEN ref ('…-rootbg-uamargin'
// tree) | web | iOS | Android captures of the calibration run — so every gained /
// lost cell of the calibration is LOOKED AT against the new ref before a sentence
// about it is written (standing constraint: a score is not a look).
// Usage: node …/replay-montage.mjs <out.png> <section>:<stem> …
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
// sharp lives in the repo's workspace node_modules (resolved from the repo root).
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const sharp = createRequire(path.join(REPO, 'package.json'))('sharp');
const NEW = path.join(REPO, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin');
const RUN = path.join(REPO, 'tools/titan/runs/wave52-calib/sections');
const [out, ...items] = process.argv.slice(2);
// Tile order per row: ref, web, ios, android — each the top 390×600 scaled to 250 wide.
const tiles = [];
for (const [sec, st] of items.map((x) => x.split(':'))) {
  tiles.push(path.join(NEW, sec, `${st}.png`));
  for (const d of ['screenshots', 'ios-screenshots', 'android-screenshots']) tiles.push(path.join(RUN, sec, d, `wpt__${sec}__${st}.png`));
}
const W = 250, TH = Math.round(600 * W / 390);
const bufs = await Promise.all(tiles.map((t) => sharp(t).extract({ left: 0, top: 0, width: 390, height: 600 }).resize(W, TH).png().toBuffer()));
// Magenta gutters so a white tile edge is never mistaken for the page edge.
await sharp({ create: { width: 4 * (W + 6), height: items.length * (TH + 6), channels: 4, background: '#ff00ff' } })
  .composite(bufs.map((b, i) => ({ input: b, left: (i % 4) * (W + 6), top: Math.floor(i / 4) * (TH + 6) }))).png().toFile(out);
console.log(`replay-montage → ${out}`);
