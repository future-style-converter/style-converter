//
// wave50-B10/zneg-ab-probe.mjs — the EXECUTED proof that the ref-capture
// recipe erases `z-index: -1` decoration boxes.
//
//   node tools/titan/results/wave50-B10/zneg-ab-probe.mjs <ref.html> ...
//
// Renders each reference document twice under BROWSER_LAUNCH_ARGS at
// 390x600: once with the LIVE canvasFrameCss body half
// (`:where(html, body){ background }`) and once with the background only
// on `:where(html)`, where canvas propagation already reads it. Reports
// chromatic (saturated) and non-white pixel counts for each. A ref whose
// counts differ is one the live recipe hides ink from — CSS 2.1 Appendix
// E paints the body background (step 3) OVER negative-z-index body
// children (step 2).
//
// Measured 2026-09-14 (see _note.md): 033/034/035/036/037-ref and
// css-text hanging-punctuation-block-bound-001-ref all render ZERO
// chromatic pixels live and 2050-58184 under the control.
//
import path from 'node:path';
import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';
import { BROWSER_LAUNCH_ARGS } from '../../capture-browser-ref.mjs';
const BG = '#ffffff';
const BOTH = `:where(html, body){margin:0;padding:0;background:${BG};}
              :where(body){display:flow-root;box-sizing:border-box;min-height:100vh;}`;
const HTMLONLY = `:where(html){background:${BG};} :where(html, body){margin:0;padding:0;}
                  :where(body){display:flow-root;box-sizing:border-box;min-height:100vh;}`;
const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS });
for (const rel of process.argv.slice(2)) {
  const file = path.resolve(rel);
  const counts = [];
  for (const css of [BOTH, HTMLONLY]) {
    const page = await browser.newPage();
    await page.setViewport({ width: 390, height: 600, deviceScaleFactor: 1 });
    await page.goto('file://' + file, { waitUntil: 'load' });
    await page.addStyleTag({ content: css });
    const p = PNG.sync.read(await page.screenshot({ type: 'png' }));
    let ink = 0, nonwhite = 0;
    for (let i = 0; i < p.data.length; i += 4) {
      const r = p.data[i], g = p.data[i+1], b = p.data[i+2];
      if (Math.max(r,g,b) - Math.min(r,g,b) > 40) ink++;
      if (r < 250 || g < 250 || b < 250) nonwhite++;
    }
    counts.push([ink, nonwhite]);
    await page.close();
  }
  const [[i1, n1], [i2, n2]] = counts;
  console.log(`${path.basename(rel).padEnd(52)} live chroma=${String(i1).padEnd(6)} ctrl chroma=${String(i2).padEnd(6)} ` +
              `live nonwhite=${String(n1).padEnd(7)} ctrl nonwhite=${n2}  ${i1 !== i2 || n1 !== n2 ? 'ERASED-BY-RECIPE' : 'unaffected'}`);
}
await browser.close();
