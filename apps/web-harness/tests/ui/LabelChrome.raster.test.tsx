// @vitest-environment node
//
// LabelChrome.raster.test.tsx — the COMPOSITED-BYTE pin for the harness
// label chrome (wave 51 PR (A) fix lane; docs/DYNAMIC_CAPTURE.md "Harness
// label chrome"). The contract is byte identity ×3: over the #1A1A2E ground
// every glyph pixel reads (174,174,180) on Android (BlockLabel.COLOR, alpha
// 179) and iOS (HarnessLabelChromeRasterTests.ink). LabelChrome.test.tsx
// pins the fill STRING, and the wave-51 web skeptic showed why that is not
// enough: `rgba(237,237,237,0.70196)` (= 179/255) rendered the SAME
// (173,173,179) as the old `0.7` in the capture pipeline's Chromium, so the
// string moved while the byte never did. This file closes that gap by
// pushing the REAL gallery markup (CaptureGallery → CaptureCanvas →
// LabelChrome, renderToStaticMarkup) through headless Chrome with the
// capture script's raster-relevant launch flags and reading the PNG back.
//
// What it asserts, per canvas, on the real bytes: (a) the crop is the 390 px
// frame; (b) pixel (8,6) — the first ink of every label — is EXACTLY the
// natives' (174,174,180); (c) the whole 16 px pad band (rows 0..15 × cols
// 0..389) is ink exactly on the derived glyph set P = layoutBlockLabel(name,
// 390) placed at (8,6), and pure ground everywhere else — which pins origin,
// crispEdges (no AA neighbours), truncation against the FRAME width (the
// 100-glyph name keeps 62, last ink column 378) and the byte, in one sweep.
//
// CI: the web-runtime job installs with PUPPETEER_SKIP_DOWNLOAD=1 (no
// Chromium), so the suite SKIPS there with a printed reason and runs on every
// machine that captures — the same machines whose PNGs the tripwire
// (tools/visual/label-chrome-tripwire.test.mjs) compares ×3. Set
// LABEL_CHROME_RASTER_REQUIRED=1 to turn a missing browser into a failure.
//
// MUTATION RECORD (executed 2026-09-22 on this tree, sources restored
// byte-exact afterwards — sha256 checked before and after each one):
//   M1. BLOCK_LABEL_FILL re-spelt `0.70196` (the builder lane's value) →
//       3 of 4 RED: "(8,6) is EXACTLY (174,174,180)" got [173,173,179];
//       band sweep 117 mismatches on Test_Comp and 1116 on the 100-glyph
//       name — every ink pixel of P, each [173,173,179] want [174,174,180]
//       (= |P|: the byte is one LSB dark on EVERY glyph pixel, nowhere
//       else). LabelChrome.test.tsx's string pin red on the spelling;
//       runtimes/web BlockFontLabel.test.ts fill pin red.
//   M2. BLOCK_LABEL_FILL back to `0.7` → RED with bytes IDENTICAL to M1
//       ([173,173,179], 117 / 1116) — the defect the skeptic measured (the
//       179/255 spelling was vacuous), now caught by a test.
//   M3. LabelChrome.tsx origin (24,22) instead of (8,6) → 3 of 4 RED:
//       (8,6) is ground [26,26,46]; band sweep 117 / 1116 — P missing
//       (the stray ink at row 22 lies below the band, so the sweep and the
//       (8,6) pin catch the move by ABSENCE, not by the stray). The
//       harness structural pin ('left:8px') red on the same run.
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

/** The natives' composited ink byte over the ground — the contract value. */
const INK: readonly [number, number, number] = [174, 174, 180];
/** The capture ground, #1A1A2E, as bytes. */
const GROUND: readonly [number, number, number] = [26, 26, 46];
/** The default capture frame width — CANVAS_WIDTH_PX without `?width=`. */
const FRAME_WIDTH = 390;
/** The pad band: rows 0..15 hold the label (rows 6..12) and nothing else. */
const BAND_ROWS = 16;

/** Minimal decoded v2 document (flat list; composition via `slot`). */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** A childless, textless leaf — the labelled case. */
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}

/**
 * The page shell the gallery markup needs to lay out as the capture does:
 * index.html's universal `box-sizing: border-box` reset is load-bearing
 * (without it `width: 390px` + 16 px padding crops to 422 px) and the
 * `capture-mode` body/root ground matches the real boot. Fonts, WPT
 * overrides and the app chrome are irrelevant to a textless leaf.
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

/**
 * Sweep the pad band: every pixel must be INK exactly where P says and GROUND
 * exactly everywhere else. Returns the mismatches (empty = pass) so the
 * failure message names coordinates and bytes instead of a bare count.
 */
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

// Chromium presence — puppeteer's resolver (v25 returns a promise). CI skips
// the download, so a missing browser is a loud SKIP, not a silent pass; an
// explicit env flag turns it into a failure for machines that must raster.
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
    // The raster-relevant subset of capture-screenshots.mjs's launch flags:
    // `headless: 'new'` (the modern paint path), `--disable-gpu` (CPU raster
    // — the blend that lands alpha byte 179 one LSB dark, the whole point)
    // and the pinned sRGB profile; the remaining flags there are focus /
    // throttling hygiene with no effect on bytes.
    browser = await puppeteer.launch({
      headless: 'new',
      args: [
        '--disable-gpu',
        '--force-color-profile=srgb',
        '--disable-background-timer-throttling',
        '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows',
        '--no-default-browser-check',
        '--no-first-run',
        '--disable-features=Translate,MediaRouter,OptimizationHints',
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

  afterAll(async () => {
    await browser?.close();
  });

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
