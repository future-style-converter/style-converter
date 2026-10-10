#!/usr/bin/env node
// Skeptic (wave 54 L6): score sk6-atrisk.replay.py's shifted captures with the gate's diffWebVsRef; the
// unshifted wave53-final capture is the calibration row (it must reproduce cells.mjs).
import path from 'node:path';
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const dir = process.argv[2];
const CASES = [['baseline-with-orthogonal-flow-001', 'css-writing-modes', 'baseline-with-orthogonal-flow-001'],
  ['attr-style-sharing-1', 'css-values', 'attr-style-sharing-1'],
  ['appearance-auto-input-non-widget-001', 'css-ui', 'appearance-auto-input-non-widget-001'],
  ['static-inside-inline-block', 'CSS2', 'abspos/static-inside-inline-block']];
for (const [name, sec, stem] of CASES) {
  const flat = stem.replaceAll('/', '__');
  const ref = `${REF}/${sec}/${flat}.png`;
  const cap = `${ROOT}/tools/titan/runs/wave53-final/sections/${sec}/screenshots/wpt__${sec}__${flat}.png`;
  const row = [(await diffWebVsRef(cap, ref)).ssim];
  for (const s of [4, 5]) row.push((await diffWebVsRef(`${dir}/${name}.shift${s}.png`, ref)).ssim);
  console.log(name.padEnd(40), 'capture', row[0], ' shift4', row[1], ' shift5', row[2]);
}
