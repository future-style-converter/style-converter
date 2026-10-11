#!/usr/bin/env node
// tools/titan/results/wave55-plan/android-paint-order.replay-score.mjs — scores the wave54-final android captures
// (sanity: must reproduce the gate's own numbers, cells.mjs) and the replay pictures written by
// android-paint-order.replay.py, with the gate's composed metric (inject-wpt-block.mjs diffWebVsRef, the
// composed-ref scoring recipe). Read-only on the run dir and the frozen refs.
// Usage: node android-paint-order.replay-score.mjs [run-id=wave54-final]
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const run = process.argv[2] ?? 'wave54-final';
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const flat = (t) => t.split('/').join('__');
const CASES = [
  ['filter-effects', 'backdrop-filter-clip-rect', 'U1'],
  ...[
    'abspos/flex-abspos-staticpos-align-self-safe-001', 'abspos/flex-abspos-staticpos-align-self-safe-002',
    'abspos/flex-abspos-staticpos-justify-self-001', 'abspos/flex-abspos-staticpos-margin-001',
    'abspos/flex-abspos-staticpos-margin-002', 'abspos/flex-abspos-staticpos-margin-003',
    'abspos/flex-abspos-staticpos-fallback-justify-content-001',
  ].map((t) => ['css-flexbox', t, 'U2']),
  ...[
    'align-self-001', 'align-self-002', 'align-self-img-001', 'align-self-img-002', 'align-self-rtl-001',
    'align-self-rtl-002', 'align-self-rtl-003', 'align-self-rtl-004', 'align-self-safe-001', 'align-self-vertWM-001',
    'align-self-vertWM-002', 'align-self-vertWM-003', 'align-self-vertWM-004', 'justify-self-001', 'justify-self-002',
  ].map((t) => ['css-grid', `abspos/grid-abspos-staticpos-${t}`, 'U2']),
  ...['hsl', 'hwb', 'lch', 'oklch'].map((h) => ['css-images', `gradient/gradient-powerless-hue-${h}`, 'U2']),
];
for (const [sec, test, unit] of CASES) {
  const ref = `${REFS}/${sec}/${flat(test)}.png`;
  const capture = `${ROOT}/tools/titan/runs/${run}/sections/${sec}/android-screenshots/wpt__${sec}__${flat(test)}.png`;
  const replay = `${HERE}/android-paint-order-replay/${flat(test)}-android-${unit}.png`;
  const a = (await diffWebVsRef(capture, ref)).ssim;
  const b = (await diffWebVsRef(replay, ref)).ssim;
  console.log(`${sec}/${test} android  capture ${a}  ${unit} ${b}  Δ ${(b - a).toFixed(4)}`);
}
