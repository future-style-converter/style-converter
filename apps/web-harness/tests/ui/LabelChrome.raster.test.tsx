// @vitest-environment node
/// <reference path="../pngjs.d.ts" />
//
// LabelChrome.raster.test.tsx — the COMPOSITED-BYTE pin for the harness label
// chrome (wave 51 PR (A); docs/DYNAMIC_CAPTURE.md "Harness label chrome").
// The contract is the byte over the #1A1A2E ground, (174,174,180) on all
// three: the natives at alpha 179/255, web at CSS alpha 0.706 — Chromium
// rounds the CSS alpha to 8 bits and its CPU-raster src-over composites byte
// 179 to (173,173,179), so `0.70196` (= 179/255) rendered the SAME byte as
// the old `0.7` while the fill STRING pin (LabelChrome.style.test.tsx) stayed
// green (the wave-51 web skeptic). This file pushes the REAL gallery markup
// (CaptureGallery → CaptureCanvas → LabelChrome, renderToStaticMarkup) through
// headless Chrome with the capture script's raster flags and reads the PNG:
// per canvas (a) the crop is the 390 px frame, (b) pixel (8,6) is EXACTLY
// (174,174,180), (c) the 16 px pad band (rows 0..15) is ink exactly on
// P = layoutBlockLabel(name, 390) at (8,6) and pure ground elsewhere — origin,
// crispEdges, frame truncation (100 glyphs → 62, last ink column 378) and the
// byte in one sweep.
//
// CI installs with PUPPETEER_SKIP_DOWNLOAD=1 (no Chromium): the suite SKIPS
// there with a printed reason and runs on every machine that captures (whose
// PNGs tools/visual/label-chrome-tripwire.test.mjs compares ×3);
// LABEL_CHROME_RASTER_REQUIRED=1 turns a missing browser into a failure.
//
// MUTATION RECORD (executed 2026-09-22, sources restored sha256-exact; the
// fix-pass numbers also stand in tools/titan/results/wave51-A/skeptic-web.md,
// "Re-verify" item 4). BLOCK_LABEL_FILL `0.70196` and `0.7` → 3 of 4 RED with
// identical bytes: (8,6) = [173,173,179], 117 / 1116 band mismatches (= |P|:
// every glyph pixel one LSB dark, nowhere else). LabelChrome.tsx origin
// (24,22) → 3 of 4 RED: (8,6) ground [26,26,46], P absent from the band (the
// stray ink at row 22 lies BELOW it — caught by absence). Polish pass, under
// the typed `headless: true`: `0.70196` → identical to the above (same paint
// path as the untyped `'new'`); `0.7079` (just past the byte-180 window) →
// (8,6) = [175,175,181], 117 / 1116; origin (24,22) → as above.
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { existsSync } from 'node:fs';
import puppeteer, { type Browser, type Page } from 'puppeteer';
import { PNG } from 'pngjs';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
import {
  BLOCK_LABEL_ORIGIN_X,
  BLOCK_LABEL_ORIGIN_Y,
  layoutBlockLabel,
} from '@style-converter/web/renderer/BlockFontLabel';

const INK: readonly [number, number, number] = [174, 174, 180]; // the natives' composited ink byte — the contract
const GROUND: readonly [number, number, number] = [26, 26, 46];  // the capture ground #1A1A2E as bytes
const FRAME_WIDTH = 390;                                          // CANVAS_WIDTH_PX without `?width=`
const BAND_ROWS = 16;                                             // rows 0..15: the label (rows 6..12) and nothing else

/** Minimal decoded v2 document (flat list; composition via `slot`). */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** A childless, textless leaf — the labelled case. */
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}

/**
 * The page shell: index.html's universal `box-sizing: border-box` reset (so
 * the DOM under test lays out as the capture boot does — the canvas's own 390
 * crop does NOT depend on it, canvasStyle sets boxSizing inline) and the
 * capture-mode ground. Fonts / WPT overrides are moot for a textless leaf.
 */
function shell(markup: string): string {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { background: #1a1a2e; }
    #root { width: 390px; min-width: 390px; max-width: 390px; background: #1a1a2e; }
  </style></head><body class="capture-mode"><div id="root">${markup}</div></body></html>`;
}

/** Read one pixel's RGB from a decoded PNG. */
function px(png: PNG, x: number, y: number): [number, number, number] {
  const i = (y * png.width + x) * 4;
  return [png.data[i], png.data[i + 1], png.data[i + 2]];
}

/** The expected glyph pixel set for a label at the shared origin, as "x,y" keys. */
function expectedInk(name: string): Set<string> {
  // The same transform the chrome applies: `_` → space, then layoutBlockLabel
  // normalises (upper-case, unknown → '-') and truncates against the frame.
  const layout = layoutBlockLabel(name.replace(/_/g, ' '), FRAME_WIDTH);
  return new Set(layout.rects.map((r) => `${BLOCK_LABEL_ORIGIN_X + r.x},${BLOCK_LABEL_ORIGIN_Y + r.y}`));
}

/** Sweep the pad band: INK exactly on P, GROUND everywhere else. Returns the
 *  mismatches (empty = pass) so a failure names pixels and bytes, not a count. */
function bandMismatches(png: PNG, ink: Set<string>): string[] {
  const out: string[] = [];
  for (let y = 0; y < BAND_ROWS; y++) {
    for (let x = 0; x < png.width; x++) {
      const want = ink.has(`${x},${y}`) ? INK : GROUND;
      const got = px(png, x, y);
      if (got[0] !== want[0] || got[1] !== want[1] || got[2] !== want[2]) {
        out.push(`(${x},${y}) got [${got}] want [${want}]`);
      }
    }
  }
  return out;
}

// Chromium presence (puppeteer 25's resolver returns a promise). CI skips the
// download → loud SKIP, never a silent pass; the env flag makes it a failure.
const chromePath = await Promise.resolve(puppeteer.executablePath());
const chromeAvailable = typeof chromePath === 'string' && existsSync(chromePath);
if (!chromeAvailable) {
  const msg = `[LabelChrome.raster] Chromium not found at ${chromePath} — composited-byte pin SKIPPED (PUPPETEER_SKIP_DOWNLOAD build?)`;
  if (process.env.LABEL_CHROME_RASTER_REQUIRED === '1') throw new Error(msg);
  console.warn(msg);
}

describe.skipIf(!chromeAvailable)('LabelChrome — composited byte parity through headless Chrome', () => {
  let browser: Browser;
  let page: Page;
  // One document, two canvases: the plain leaf and a 100-glyph name that
  // exercises frame-width truncation (62 glyphs kept at 390).
  const LONG_NAME = 'A'.repeat(100);
  const pngs = new Map<string, PNG>();

  beforeAll(async () => {
    // capture-screenshots.mjs's launch flags, raster-relevant subset.
    // `headless: true` is puppeteer 25's typed spelling (`boolean | 'shell'`)
    // of modern headless: ChromeLauncher.js:203 maps every non-'shell' truthy
    // value to `--headless=new`, the flag the script's untyped `'new'` yields
    // — same paint path, byte for byte (mutation record). `--disable-gpu`
    // forces CPU raster (the blend under test), the sRGB profile pins the
    // colour space; the rest is focus / throttling hygiene with no byte effect.
    browser = await puppeteer.launch({
      headless: true,
      args: [
        '--disable-gpu',
        '--force-color-profile=srgb',
        '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows', '--no-default-browser-check',
        '--no-first-run', '--disable-features=Translate,MediaRouter,OptimizationHints',
      ],
    });
    page = await browser.newPage();
    // The capture viewport: 390 wide, deviceScaleFactor 1 → CSS px == PNG px.
    await page.setViewport({ width: FRAME_WIDTH, height: 844, deviceScaleFactor: 1 });
    // The REAL gallery markup — CaptureCanvas mounts LabelChrome as the last
    // child; nothing here is a replica of the DOM under test.
    const markup = renderToStaticMarkup(
      <CaptureGallery document={doc([comp(), comp({ id: 'long', name: LONG_NAME })])} />,
    );
    await page.setContent(shell(markup));
    // Crop each canvas exactly as capture-screenshots.mjs does: the element's
    // own bounding rect, so PNG (0,0) is the canvas's padding-box origin.
    for (const handle of await page.$$('[data-capture-canvas]')) {
      const id = await handle.evaluate((el) => el.getAttribute('data-capture-id'));
      const clip = await handle.evaluate((el) => {
        const r = el.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height };
      });
      const buf = await page.screenshot({ clip });
      pngs.set(String(id), PNG.sync.read(Buffer.from(buf)));
    }
  }, 60_000);

  afterAll(async () => { await browser?.close(); }); // never leak a Chromium

  it('crops to the 390 px frame with the band inside it', () => {
    // Two canvases captured; each is the frame wide and at least pad-band tall.
    expect([...pngs.keys()].sort()).toEqual(['long', 'test-id']);
    for (const png of pngs.values()) {
      expect(png.width).toBe(FRAME_WIDTH);
      expect(png.height).toBeGreaterThanOrEqual(BAND_ROWS);
    }
  });

  it('the first ink pixel (8,6) is EXACTLY the natives\' (174,174,180) — not the 0.7 / 179-alpha (173,173,179)', () => {
    // 'T' row 0 is 11111, so (8,6) is ink on Test_Comp; 'A' row 0 is 01110,
    // so on the long name the first ink is (9,6). Both must be the byte.
    expect(px(pngs.get('test-id')!, 8, 6)).toEqual([...INK]);
    expect(px(pngs.get('long')!, 9, 6)).toEqual([...INK]);
    // A neighbour outside every glyph column is pure ground (crispEdges).
    expect(px(pngs.get('test-id')!, 7, 6)).toEqual([...GROUND]);
  });

  it('the pad band is ink exactly on P and ground everywhere else (origin, crisp, byte)', () => {
    // The derived set P for the plain leaf; a mismatch list keeps the
    // failure readable (first entries name the pixel and both byte triples).
    const plain = bandMismatches(pngs.get('test-id')!, expectedInk('Test_Comp'));
    expect(plain.slice(0, 8), `${plain.length} band mismatches`).toEqual([]);
  });

  it('truncates against the FRAME: the 100-glyph name paints 62 glyphs, last ink column 378, nothing past it', () => {
    const P = expectedInk(LONG_NAME);
    // 62 cells: last cell starts at 8 + 61*6 = 374, 'A' ink spans cols +0..+4 → 378.
    const maxX = Math.max(...[...P].map((k) => Number(k.split(',')[0])));
    expect(maxX).toBe(378);
    // And the raster agrees pixel for pixel across the whole band.
    const long = bandMismatches(pngs.get('long')!, P);
    expect(long.slice(0, 8), `${long.length} band mismatches`).toEqual([]);
  });
});
