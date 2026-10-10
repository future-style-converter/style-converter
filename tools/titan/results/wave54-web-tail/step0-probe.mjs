#!/usr/bin/env node
// tools/titan/results/wave54-web-tail/step0-probe.mjs — wave 54 lane L6 (web-tail), [W-L6] step 0.
//
// WHICH LAYER SPLITS `high|way`? (web-out-of-flow-hyphen-box.md §5 "Step 0").
// On wave53-final web, hyphens-out-of-flow-002 boxes 4/5 (`high<span abspos>
// abspos</span>way`, hyphens:auto, width 6ch) are 26-px one-line boxes where
// the ref has 46 px (`high‐` / `way`). This probe asks the host's own
// puppeteer Chromium (the capture browser) to lay out seven pre-registered
// shapes and reads each box's border-box height:
//   V0  raw markup            high<span abs>abspos</span>way
//   V1  harness shape         <span>high</span><span abs lang=en>abspos</span><span>way</span>
//   V2  package-default shape high<span abs lang=en class style>abspos</span>way
//   V3  W1 shape              <span>highway</span><span abs>abspos</span>
//   V4  wrapper pair          <span>high</span><span>way</span>
//   V5  plain word            highway
//   V6  the raw WPT file      tools/wpt/css/css-text/hyphens/hyphens-out-of-flow-002.html (all 7 divs)
// Every page gets the ref capture's own sheet (capture-browser-ref.mjs
// canvasFrameCss: embedded Inter, REF_FONT_STACK, line-height 1.25, white
// canvas, body flow-root, margin 0) at the ref render width (358 px), so the
// heights are the ones the ref / composed capture would lay out.
//
// PRE-REGISTERED (PLAN §2 L6, expectations.json lanes.L6-web-tail.step0):
//   V1 = 26, V3 = 46, V5 = 46.  Prediction (MED): V6 boxes 4/5 = 26 and V0 = 26 → branch R.
// DECISION (printed at the end, exit code = the branch):
//   V3 != 46                          → STOP (premise false; W1 not built; enter W1 in plan-build REVERTED)  exit 3
//   V6 boxes 4/5 = 26 or V0 = 26      → R  (W1 in the runtime, as built)                                     exit 0
//   V6 = V0 = 46, V2 = 46, V1 = 26    → H  (seam-2 renderText INSTEAD of the runtime files)                  exit 10
//   V6 = 46, V2 = 26                  → H2 (bisect V2 → V0; land W1 as built)                                exit 11
//   anything else                     → UNCLASSIFIED (re-plan; nothing lands for W1)                         exit 4
//
// Usage (ORCHESTRATOR WINDOW — Chromium; ~1 min; no device):
//   node tools/titan/results/wave54-web-tail/step0-probe.mjs > tools/titan/results/wave54-web-tail/step0-probe.out.txt
import puppeteer from 'puppeteer';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
// The ref capture's own frame sheet, width and launch flags — one source, never a copy.
import { canvasFrameCss, REF_RENDER_WIDTH, BROWSER_LAUNCH_ARGS } from '../../capture-browser-ref.mjs';

// This lane directory and the repo root (four levels up).
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../../../..');
// The raw WPT test, exactly as the extractor read it.
const WPT_TEST = path.join(ROOT, 'tools/wpt/css/css-text/hyphens/hyphens-out-of-flow-002.html');

// The test's own div rule, restated (class-scoped so the probe's wrapper
// spans are NOT made absolute by the test's bare `span {}` rule).
const TEST_CSS = `
  div.box { border: solid orange; margin: 5px; width: 6ch; hyphens: auto; }
  span.abs { position: absolute; color: transparent; }
`;
// The six synthetic variants (V0-V5), each the content of one `div.box lang=en`.
const VARIANTS = {
  V0: 'high<span class="abs">abspos</span>way',
  V1: '<span>high</span><span class="abs" lang="en">abspos</span><span>way</span>',
  V2: 'high<span class="abs sc-probe" lang="en" style="position:absolute;color:rgba(0,0,0,0)">abspos</span>way',
  V3: '<span>highway</span><span class="abs">abspos</span>',
  V4: '<span>high</span><span>way</span>',
  V5: 'highway',
};

/** Lay one HTML page out under the ref sheet and return every div.box height. */
async function heights(page, html, url = null) {
  // A file:// URL keeps a raw test's relative resources resolvable; else inline content.
  if (url) await page.goto(url, { waitUntil: 'load', timeout: 30_000 });
  else await page.setContent(html, { waitUntil: 'load' });
  // The same sheet the ref capture injects after load (zero-specificity :where rules).
  await page.addStyleTag({ content: await canvasFrameCss() });
  // Settle fonts and two frames, as renderRefPng does before its screenshot.
  await page.evaluate(() => document.fonts.ready.then(
    () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  // Border-box heights in document order (the raw test's divs carry no class: select all divs).
  return page.evaluate((sel) => [...document.querySelectorAll(sel)]
    .map((d) => Math.round(d.getBoundingClientRect().height * 100) / 100), url ? 'div' : 'div.box');
}

/** A synthetic page holding one variant box. */
const page1 = (inner) => `<!DOCTYPE html><meta charset="utf-8"><style>${TEST_CSS}</style>`
  + `<div class="box" lang="en">${inner}</div>`;

const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS });
const out = {};
try {
  const page = await browser.newPage();
  // The ref renders at the 358-px viewport inside the 16-px image frame.
  await page.setViewport({ width: REF_RENDER_WIDTH, height: 600, deviceScaleFactor: 1 });
  for (const [k, inner] of Object.entries(VARIANTS)) out[k] = (await heights(page, page1(inner)))[0];
  // V6: the raw WPT page itself (seven boxes).
  out.V6 = await heights(page, null, 'file://' + encodeURI(WPT_TEST));
} finally {
  await browser.close();
}

// Read-out: one line per variant, then the pre-registered checks and the branch.
for (const [k, v] of Object.entries(out)) console.log(`${k.padEnd(4)} ${JSON.stringify(v)}`);
// The raw test's only non-div is the <p>, so V6[0..6] are boxes 1..7 and boxes 4/5 are V6[3], V6[4].
const box45 = Array.isArray(out.V6) ? [out.V6[3], out.V6[4]] : [];
console.log(`pre-registered: V1=${out.V1} (26) V3=${out.V3} (46) V5=${out.V5} (46); V6 boxes 4/5=${JSON.stringify(box45)}`);
let branch; let code;
if (out.V3 !== 46) { branch = 'STOP (V3 != 46: the W1 premise is false — W1 is not landed; enter it in plan-build.py REVERTED, run step0)'; code = 3; }
else if (box45.every((h) => h === 26) || out.V0 === 26) { branch = 'R (Chromium-on-macOS splits the word itself: land W1 in the runtime as built)'; code = 0; }
else if (out.V6.every((h) => h === 46) && out.V0 === 46 && out.V2 === 46 && out.V1 === 26) { branch = 'H (harness <span> wrappers: land seam-2 renderText INSTEAD of the W1 runtime files)'; code = 10; }
else if (out.V6.every((h) => h === 46) && out.V2 === 26) { branch = 'H2 (an attribute/inline style of the member: bisect V2 -> V0; land W1 as built)'; code = 11; }
else { branch = 'UNCLASSIFIED (no pre-registered row matches: W1 does not land; re-plan)'; code = 4; }
console.log(`BRANCH ${branch}`);
process.exitCode = code;
