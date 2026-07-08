#!/usr/bin/env node
// Tier 11 — Accessibility audit (WCAG 2.1 AA).
//
// Phase 11a: web color-contrast slice only.
//
// Honest scope (see testing/TIER11_A11Y.md and the round-41 scoper report):
// the Style-Converter IR has no semantic-HTML / a11y model — every component
// renders as <div>. So most aXe rules are inapplicable on this DOM:
//
//   * button-name / link-name / image-alt — no <button>/<a>/<img> exist
//   * focus-order-semantics / keyboard / aria-required-attr — no interactive
//     elements
//   * region / landmark-one-main / document-title — page-level, not
//     per-component actionable
//
// What IS meaningful: `color-contrast`. Placeholder text vs background
// luminance is real, the IR's PlaceholderContent does Rec.601 contrast pick,
// and the FixtureCanvas pipeline (Phase 5a) gives us per-fixture scoped DOMs
// to score. So this script:
//   1. Pre-flights vite, fails fast if down (mirrors Phase 5a contract)
//   2. Iterates each fixture under examples/properties/components/
//   3. Hits ?fixture=<Name>, injects axe-core, runs ONLY color-contrast
//   4. Writes per-fixture pass/fail + per-violation node (selector, contrast
//      ratio observed, threshold expected) to testing/a11y-report.json
//   5. Records other criteria as `blocked-IR` with the exact reason instead
//      of pretending we audited them
//
// iOS XCUITest + Android Espresso accessibility harnesses are still stubs
// (TIER11 rows 9, 10) — they need a per-fixture native target to wrap, which
// is a 1-day Phase 5b/5c follow-up, separate from this Phase 11a slice.

import { writeFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Round 72: exported pure functions/constants so testing/a11y-audit.test.mjs
// can pin the contract. The main script body is gated behind an
// `import.meta.url === ...` check so importing this module doesn't
// trigger a real audit run.
export const WEB_PORT = process.env.WEB_PORT || '3000';
export const BASE_URL = `http://localhost:${WEB_PORT}`;
export const COMPONENTS_DIR = 'examples/properties/components';
export const REPORT_PATH = 'testing/a11y-report.json';
// Pin axe-core version for reproducibility. CDN URL — no npm install needed.
// If we move to local bundling we'd add `axe-core` to testing/web/package.json
// and import from node_modules.
export const AXE_CDN = 'https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.8.3/axe.min.js';
// The single rule this Phase 11a actually scores. Other WCAG rules are
// recorded as `blocked-IR` to keep the report honest about what was tested
// vs what's a real measurement.
export const AUDITED_RULE = 'color-contrast';

/**
 * Pure transform: take a raw aXe-core run result and return our normalized
 * per-fixture report shape. Extracted from the auditWebFixture body in
 * round 72 so testing/a11y-audit.test.mjs can pin the contract.
 *
 * Input shape (axe.run() output):
 *   { violations: [{ id, nodes: [{ target, impact, failureSummary,
 *                                   any: [{ data: { contrastRatio, ... } }],
 *                                   all: [...] }] }],
 *     passes: [{ id }],
 *     inapplicable: [{ id }],
 *     incomplete: [{ id }] }
 *
 * Output shape:
 *   { url, rule, passed, violationCount, inapplicable, incomplete, failures: [...] }
 */
export function shapeAxeResult(axeResult, url, rule = AUDITED_RULE) {
  const failures = [];
  for (const v of axeResult.violations || []) {
    for (const node of v.nodes || []) {
      // Pull contrast ratio + threshold out of the structured `data` aXe
      // attaches to color-contrast nodes. Falls back to the failureSummary
      // string if data isn't there (shouldn't happen for color-contrast,
      // but stays defensive).
      const data = node.any?.[0]?.data || node.all?.[0]?.data || {};
      failures.push({
        selector: (node.target || []).join(' '),
        contrastRatio: data.contrastRatio ?? null,
        expectedContrastRatio: data.expectedContrastRatio ?? null,
        fgColor: data.fgColor ?? null,
        bgColor: data.bgColor ?? null,
        fontSize: data.fontSize ?? null,
        fontWeight: data.fontWeight ?? null,
        impact: node.impact,
        summary: node.failureSummary,
      });
    }
  }
  return {
    url,
    rule,
    passed: (axeResult.passes || []).some((p) => p.id === rule),
    violationCount: (axeResult.violations || []).reduce((n, v) => n + (v.nodes?.length || 0), 0),
    inapplicable: (axeResult.inapplicable || []).some((r) => r.id === rule),
    incomplete: (axeResult.incomplete || []).some((r) => r.id === rule),
    failures,
  };
}

let _puppeteer = null;
let _browser = null;

async function getBrowser() {
  if (_browser) return _browser;
  if (!_puppeteer) {
    try { _puppeteer = await import('puppeteer'); }
    catch (err) { throw new Error('puppeteer-not-installed: ' + err.message); }
  }
  // Round 75: bump protocolTimeout (see interaction-states.mjs comment).
  _browser = await _puppeteer.default.launch({ headless: true, protocolTimeout: 5 * 60 * 1000 });
  return _browser;
}
async function closeBrowser() {
  if (_browser) {
    try { await _browser.close(); } catch { /* best-effort */ }
    _browser = null;
  }
}

async function preflightVite() {
  try {
    const res = await fetch(BASE_URL + '/');
    if (!res.ok) throw new Error(`vite responded ${res.status}`);
  } catch (err) {
    throw new Error(
      `vite dev server not reachable at ${BASE_URL} (${err.message}). ` +
      `Run \`cd testing/web && npm run dev\` in another terminal first.`
    );
  }
}

async function auditWebFixture(name) {
  let page;
  try {
    const browser = await getBrowser();
    page = await browser.newPage();

    const url = `${BASE_URL}/?fixture=${name}`;
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 10_000 });
    const status = resp ? resp.status() : 0;
    if (!resp || status >= 400) {
      return { error: `goto-failed:${status || 'no-response'}`, url };
    }

    // Wait for the FixtureCanvas to flag itself rendered. Without this aXe
    // sometimes runs against a 0×0 box and misses the placeholder text we
    // actually want to audit for contrast.
    try {
      await page.waitForSelector('[data-fixture-ready="1"]', { timeout: 5_000 });
    } catch {
      return { error: 'fixture-ready-timeout', url };
    }

    // Inject axe-core from CDN. addScriptTag waits for the script to load
    // and evaluate before resolving, so window.axe is guaranteed defined
    // by the time the next evaluate() runs.
    await page.addScriptTag({ url: AXE_CDN });

    // Run aXe with ONLY the color-contrast rule. Filtering at the source
    // (instead of post-hoc) keeps the scan fast and avoids the IR-noise
    // problem (button-name on divs, region on a single-component page, etc.).
    const result = await page.evaluate((ruleId) =>
      window.axe.run(document, { runOnly: { type: 'rule', values: [ruleId] } }),
      AUDITED_RULE
    );

    // Each violation can have multiple `nodes` — one per offending element.
    // The per-node flattening + per-fixture report shape lives in the pure
    // shapeAxeResult() function above so testing/a11y-audit.test.mjs can
    // pin the contract.
    return shapeAxeResult(result, url, AUDITED_RULE);
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

// Other WCAG criteria are recorded as `blocked-IR` rather than scored. This
// keeps the report honest about what's a real measurement vs what's missing
// pre-requisite IR work. See testing/TIER11_A11Y.md for the full per-criterion
// breakdown.
export const BLOCKED_CRITERIA = [
  { id: 'screen-reader-output',    aXeRule: '(multiple)',         reason: 'IR has no IRRole/IRAriaLabel — every component renders as <div>; aXe cannot score what isn\'t marked up' },
  { id: 'focus-order',             aXeRule: 'focus-order-semantics', reason: 'No interactive elements emitted (no <button>/<a>/<input> from IR); focus order is meaningless' },
  { id: 'keyboard-nav',            aXeRule: 'keyboard',           reason: 'No interactive elements; nothing to keyboard-navigate' },
  { id: 'aria-labels',             aXeRule: 'aria-required-attr', reason: 'IR has no IRAriaLabel model' },
  { id: 'semantic-landmarks',      aXeRule: 'region',             reason: 'IR has no IRRole; per-fixture single-component pages have no landmarks by design' },
  { id: 'skip-to-content',         aXeRule: 'skip-link',          reason: 'IR has no IRRole=navigation; not applicable to per-fixture pages' },
  { id: 'form-labeling',           aXeRule: 'label',              reason: 'IR has no IRLabel/IRAriaLabel; FormInput renders as a styled <div> with no <input> child' },
];

export function iOSStub() {
  return { skipped: true, reason: 'XCUIAccessibility audit harness not yet wired (TIER11 row 9, ~8-12h)' };
}
export function androidStub() {
  return { skipped: true, reason: 'Espresso AccessibilityChecks harness not yet wired (TIER11 row 10, ~6-10h)' };
}

async function main() {
  if (!existsSync(COMPONENTS_DIR)) {
    console.error(`No components dir at ${COMPONENTS_DIR}`);
    process.exit(1);
  }
  try { await preflightVite(); }
  catch (err) { console.error(`✗ ${err.message}`); process.exit(2); }

  const fixtures = readdirSync(COMPONENTS_DIR).filter((f) => f.endsWith('.json'));
  const report = {
    generated: new Date().toISOString(),
    audited: { rule: AUDITED_RULE, scope: 'web (Phase 11a)' },
    blocked: BLOCKED_CRITERIA,
    components: {},
  };
  let okCount = 0, failCount = 0, errCount = 0;

  for (const f of fixtures) {
    const name = f.replace(/\.json$/, '');
    process.stdout.write(`auditing ${name}… `);
    const web = await auditWebFixture(name);
    report.components[name] = {
      web,
      iOS: iOSStub(),
      Android: androidStub(),
    };
    if (web.error || web.skipped) {
      errCount++;
      console.log(`✗ ${web.error || web.reason}`);
    } else if (web.passed && web.violationCount === 0) {
      okCount++;
      console.log(`✓ contrast clean`);
    } else if (web.violationCount > 0) {
      failCount++;
      console.log(`⚠ ${web.violationCount} contrast failure(s)`);
    } else {
      // inapplicable (no text in fixture) — neutral, not a failure
      okCount++;
      console.log(`– no text to score (inapplicable)`);
    }
  }

  await closeBrowser();
  writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2));

  const totalScored = okCount + failCount;
  console.log(
    `\n✓ wrote ${REPORT_PATH}\n` +
    `  ${totalScored}/${fixtures.length} fixtures scored on color-contrast · ` +
    `${okCount} clean · ${failCount} failing · ${errCount} errored\n` +
    `  Other WCAG criteria recorded as blocked-IR (see report.blocked)`
  );

  if (errCount > 0) process.exit(1);
}

// Round 72: gate main() so importing this module from
// testing/a11y-audit.test.mjs doesn't trigger a real audit run.
const isMainScript = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMainScript) {
  main().catch(async (err) => {
    await closeBrowser();
    console.error('Fatal:', err);
    process.exit(1);
  });
}
