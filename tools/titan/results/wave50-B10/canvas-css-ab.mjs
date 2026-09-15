//
// wave50-B10/canvas-css-ab.mjs — blast radius of
// capture-browser-ref-body-background.patch, by BYTE HASH.
//
//   node tools/titan/results/wave50-B10/canvas-css-ab.mjs <list.txt | ref.html ...>
//
// Renders each reference document under the live canvasFrameCss and under
// the patched sheet and sha1s the two PNGs. Identical hash = the patch
// cannot move that ref. Measured 2026-09-14 over the 42 corpus refs that
// use a negative z-index: 28 differ, 14 identical; of the 28, six are the
// match target of a scored wave49-final test (erased-refs.json).
//
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import puppeteer from 'puppeteer';
import { BROWSER_LAUNCH_ARGS, canvasFrameCss } from '../../capture-browser-ref.mjs';

const LIVE = await canvasFrameCss();
// The proposal: the body half keeps margin/padding, loses `background`.
const PROPOSED = LIVE.replace(
  ':where(html, body) { margin: 0; padding: 0; background: #FFFFFF; }',
  ':where(html) { background: #FFFFFF; }\n      :where(html, body) { margin: 0; padding: 0; }');
if (PROPOSED === LIVE) { console.error('PATCH TEXT DID NOT APPLY — check the literal'); process.exit(2); }

const refs = process.argv[2].endsWith('.txt')
  ? fs.readFileSync(process.argv[2],'utf8').split('\n').filter(Boolean)
  : process.argv.slice(2);
const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS });
let diff = 0;
for (const rel of refs) {
  const file = 'file://' + path.resolve(rel);
  const h = [];
  for (const css of [LIVE, PROPOSED]) {
    const page = await browser.newPage();
    await page.setViewport({ width: 390, height: 600, deviceScaleFactor: 1 });
    try { await page.goto(file, { waitUntil: 'load', timeout: 15000 }); } catch { }
    try { await page.addStyleTag({ content: css }); } catch { h.push('SKIP'); await page.close(); continue; }
    h.push(crypto.createHash('sha1').update(await page.screenshot({ type: 'png' })).digest('hex'));
    await page.close();
  }
  if (h.length < 2 || h.includes('SKIP')) { console.log('SKIP', rel); continue; }
  if (h[0] !== h[1]) { diff++; console.log('DIFFERS', rel); }
}
console.log(`sample=${refs.length} differs=${diff} identical=${refs.length - diff}`);
await browser.close();
