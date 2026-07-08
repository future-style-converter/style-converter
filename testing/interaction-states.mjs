#!/usr/bin/env node
// Tier 5 — Interaction state testing.
//
// Drives :hover, :focus, :active, :checked, :disabled, :visited
// programmatically on each Tier-3 component (web side via Puppeteer; iOS
// stub uses XCUITest hooks; Android stub uses Espresso UiObject2 hooks).
//
// Pre-flight contract for the web side (matches testing/TIER5_PLAN.md):
//   1. `cd apps/web-harness && npm run build-fixtures` has produced
//      apps/web-harness/public/fixtures/<Name>.json for every COMPONENTS entry.
//   2. `npm run dev` (or vite preview) is running on WEB_PORT — this script
//      does NOT start vite (test-all.sh owns that lifecycle).
//
// Component DOM contract (see apps/web-harness/src/ui/FixtureCanvas.tsx):
//   - `[data-testid="<ComponentName>"]` resolves to a single element
//   - `[data-fixture-ready="1"]` set after first paint (used as a wait gate)

import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Round 74: exported constants + stub functions so testing/interaction-states.test.mjs
// can pin the contract. The puppeteer-bound async functions are NOT exported
// (they require a browser to test). Main() is gated below.
export const STATES = ['hover', 'focus', 'active', 'checked', 'disabled', 'visited'];
export const COMPONENTS = ['Alert', 'Avatar', 'IOSSettingsRow', 'NavHeader', 'Pill',
                    'PrimaryButton', 'StatusBar', 'Tab', 'Toast', 'MaterialCard',
                    'FormInput', 'Tooltip', 'Code', 'ProgressBar', 'Badge'];

export const WEB_PORT = process.env.WEB_PORT || '3000';
export const BASE_URL = `http://localhost:${WEB_PORT}`;
export const OUT_DIR = 'testing/interaction-snapshots';
if (!existsSync(OUT_DIR)) mkdirSync(OUT_DIR, { recursive: true });

// Cache the puppeteer module + a single browser instance across all
// (component × state) pairs. Launching a browser per snapshot was costing
// ~1.5s per fire and serialised across 90 fires (15×6) → 2+ min wasted.
let _puppeteer = null;
let _browser = null;

async function getBrowser() {
  if (_browser) return _browser;
  if (!_puppeteer) {
    try {
      _puppeteer = await import('puppeteer');
    } catch (err) {
      // Re-thrown with context so the caller can record `skipped: true`
      // instead of failing the whole run.
      throw new Error('puppeteer-not-installed: ' + err.message);
    }
  }
  // Round 75: bump protocolTimeout from puppeteer's recent ~30s default
  // to 5min. Headless Chrome's RAF/paint throttling can stall CDP
  // round-trips well past the default during rapid per-state captures
  // (90 in a row), causing "Runtime.callFunctionOn timed out" partway
  // through. 5min is generous enough for any single fixture's render
  // even on a cold machine without affecting fast-path timing.
  _browser = await _puppeteer.default.launch({ headless: true, protocolTimeout: 5 * 60 * 1000 });
  return _browser;
}

async function closeBrowser() {
  if (_browser) {
    try { await _browser.close(); } catch { /* best-effort */ }
    _browser = null;
  }
}

/**
 * Verify the vite dev server is up on WEB_PORT before we start spawning
 * browsers. A targeted error here saves 90 ECONNREFUSED stack traces and
 * makes the "you forgot to run `npm run dev`" failure mode obvious.
 */
async function preflightVite() {
  try {
    const res = await fetch(BASE_URL + '/');
    if (!res.ok) {
      throw new Error(`vite responded ${res.status}`);
    }
  } catch (err) {
    throw new Error(
      `vite dev server not reachable at ${BASE_URL} (${err.message}). ` +
      `Run \`cd apps/web-harness && npm run dev\` in another terminal first.`
    );
  }
}

async function snapshotWeb(component, state) {
  let page;
  try {
    const browser = await getBrowser();
    page = await browser.newPage();

    const url = `${BASE_URL}/?fixture=${component}`;
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 10_000 });
    // Accept 200-399 (covers 304 Not Modified, which vite returns for cached
    // assets after the first request — these are healthy responses, not
    // failures). Only treat 400+ as errors.
    const status = resp ? resp.status() : 0;
    if (!resp || status >= 400) {
      return { error: `goto-failed:${status || 'no-response'}`, url };
    }

    // Wait for the FixtureCanvas to flag itself as ready. Without this the
    // race between React's first paint and our state event made about 1 in
    // 5 captures show a 0×0 box. 5s is generous; a healthy render is <100ms.
    try {
      await page.waitForSelector('[data-fixture-ready="1"]', { timeout: 5_000 });
    } catch {
      return { error: 'fixture-ready-timeout', url };
    }

    const selector = '[data-testid="' + component + '"]';
    const el = await page.$(selector);
    if (!el) { return { error: 'element-not-found', url }; }

    // Fire the state event. Most are stable post-event (focus stays focused,
    // checked stays checked); :active needs the mouse held DOWN through the
    // screenshot, hence the bespoke branch.
    let activeReleased = null;
    switch (state) {
      case 'hover':
        await el.hover();
        break;
      case 'focus':
        await el.focus();
        break;
      case 'active': {
        // Real :active capture: mouse-down at the element centre, screenshot,
        // mouse-up afterwards. node.click() (the previous impl) released
        // :active before the screenshot fired so we were always capturing
        // the post-click state.
        const box = await el.boundingBox();
        if (!box) { return { error: 'no-bounding-box', url }; }
        const cx = box.x + box.width / 2;
        const cy = box.y + box.height / 2;
        await page.mouse.move(cx, cy);
        await page.mouse.down();
        // Yield a tick so the browser commits the :active style before we capture.
        await new Promise(r => setTimeout(r, 16));
        activeReleased = page.mouse.up();
        break;
      }
      case 'checked':
        await el.evaluate((node) => { if ('checked' in node) node.checked = true; });
        break;
      case 'disabled':
        await el.evaluate((node) => { if ('disabled' in node) node.disabled = true; });
        break;
      case 'visited':
        // No programmatic API; relies on browser history. Captures default
        // state — flagged in the report so it's not mistaken for a real
        // visited-state snapshot.
        break;
    }

    const path = `${OUT_DIR}/web__${component}__${state}.png`;
    const box = await el.boundingBox();
    if (!box || box.width === 0 || box.height === 0) {
      if (activeReleased) await activeReleased;
      return { error: 'zero-bounding-box', url };
    }
    await page.screenshot({ path, clip: box });

    // Release the mouse if we held it down for :active.
    if (activeReleased) await activeReleased;

    const result = { path, url };
    if (state === 'visited') result.note = 'no programmatic visited API; baseline state captured';
    return result;
  } catch (err) {
    if (err.message?.startsWith('puppeteer-not-installed')) {
      return { skipped: true, reason: 'puppeteer not installed' };
    }
    return { error: err.message };
  } finally {
    if (page) {
      try { await page.close(); } catch { /* best-effort */ }
    }
  }
}

export function snapshotiOS(component, state) {
  // Stub — would shell out to XCUITest:
  //   xcodebuild test -scheme InteractionStates -only-testing:InteractionStatesTests/<component>__<state>
  return { skipped: true, reason: 'iOS XCUITest harness not yet wired (TIER5 task #2)' };
}

export function snapshotAndroid(component, state) {
  // Stub — would shell out to Espresso UiAutomator
  return { skipped: true, reason: 'Android Espresso harness not yet wired (TIER5 task #3)' };
}

async function main() {
  // Vite server check up-front: if the dev server isn't running, fail fast
  // with a useful message instead of generating 90 ECONNREFUSED snapshots.
  try {
    await preflightVite();
  } catch (err) {
    console.error(`✗ ${err.message}`);
    process.exit(2);
  }

  const report = { generated: new Date().toISOString(), components: {} };
  let okCount = 0, errCount = 0, skipCount = 0;

  for (const comp of COMPONENTS) {
    report.components[comp] = {};
    for (const state of STATES) {
      process.stdout.write(`${comp} :${state}… `);
      const web = await snapshotWeb(comp, state);
      report.components[comp][state] = {
        web,
        iOS: snapshotiOS(comp, state),
        Android: snapshotAndroid(comp, state),
      };
      if (web.path)        { okCount++;  console.log('✓'); }
      else if (web.skipped){ skipCount++;console.log('–'); }
      else                 { errCount++; console.log(`✗ ${web.error || 'unknown'}`); }
    }
  }

  await closeBrowser();

  writeFileSync('testing/interaction-report.json', JSON.stringify(report, null, 2));
  console.log(
    `\n✓ ${okCount} captured · ${errCount} failed · ${skipCount} skipped ` +
    `(${COMPONENTS.length} components × ${STATES.length} states web side)`
  );

  // Non-zero exit when nothing was captured — keeps CI honest.
  if (okCount === 0) process.exit(1);
}

// Top-level try/finally: a thrown error mid-loop still closes the browser
// so we don't leak headless Chrome processes.
//
// Round 74: gated behind isMainScript so importing the module from
// testing/interaction-states.test.mjs doesn't try to launch puppeteer.
const isMainScript = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMainScript) main().catch(async (err) => {
  await closeBrowser();
  console.error('Fatal:', err);
  process.exit(1);
});
