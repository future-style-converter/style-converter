#!/usr/bin/env node
//
// tools/titan/red-square-census.mjs — the RED-SQUARE census, as committed code.
//
// WHAT IT COUNTS. WPT reftests of the form "test passes if there is a filled
// GREEN square" paint a RED square when the feature under test fails. The
// scorer's colour veto (inject-wpt-block.mjs, colorDivergent = whole-canvas
// histogram KL > 0.1) is coverage-weighted, so a total hue swap confined to a
// small area cannot trip it — such cells score SSIM up to 1.0 and are recorded
// as PASSES (wave-49 headline; canonical instance css-view-transitions/
// hit-test-unrelated-element, all three platforms: ssim 1.0000, wptPass true,
// fuzzyMaxChannelDelta 255). This script enumerates every scored cell whose
// CAPTURE carries red ink where the REFERENCE carries none, split by wptPass.
//
// WHY IT IS COMMITTED (retro finding A1#3). The wave-49 PR quoted "179 passing
// red-square cells (web 26 / iOS 67 / Android 86, 102 tests) and 89 failing
// (13/37/39, 53 tests), computed independently, EXACT agreement" — but no
// script or predicate was committed, only the phrase `capRedPct > 0.5` in a
// comment. The retro audit re-ran the census from that phrase on the run the
// note names as its artifact (wave48-final): strict red gives 174 passing
// (23/65/86, 100 tests) and 110 failing (18/45/47, 62 tests); across five red
// definitions passing spans 174–186 and failing 109–113. Neither 179/89 nor
// the 26/67/86 split reproduces there. On wave49-final the same predicate
// gives 179 passing — but split 23/66/90 over 105 tests — and 87 failing
// (15/35/37, 51 tests). So the headline TOTAL is reachable, its decomposition
// is not, and the "89 failing" set the wave-49 fix lanes drew from cannot be
// re-derived. This file fixes the predicate so the next census is a diff, not
// an argument; red-square-census.test.mjs proves it can fail.
//
// Usage:
//   node tools/titan/red-square-census.mjs <runId | runs/<runId> dir> [--json out.json] [--section <sec>]
//
// Exit 0 with a summary on stdout; exit 2 on a missing run dir.

import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
// The ONE canonical stem derivation — the composed capture is named
// `wpt__<section>__<fixtureStem>.png` (split-combined-ir.mjs / composedPngName).
import { safe, fixtureStem } from './safe-name.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');

// ── The predicate (frozen; the test pins every constant) ────────────────────
//
// STRICT red — r > 200 AND g < 80 AND b < 80 per pixel — is the audit's
// "strict" definition (scratchpad/retro/A1/census.py) and the one whose
// numbers this file's banner quotes. It admits the WPT canonical `red`
// (255,0,0) and its anti-aliased fringe down to ~(201,79,79); it rejects
// orange, pink and the dark-red of shadowed edges. `capMinPct` > 0.5 % of the
// canvas is the wave-49 comment's own bar (`capRedPct > 0.5`); the reference
// must carry NO red at all (`refMaxPct` 0) so a test whose ref legitimately
// paints red (a red-on-purpose control) is never counted.
export const RED_SQUARE_PREDICATE = Object.freeze({
  isRed: (r, g, b) => r > 200 && g < 80 && b < 80, // per-pixel strict red
  capMinPct: 0.5,                                   // capture red % must EXCEED this
  refMaxPct: 0,                                     // reference red % must not exceed this
});

/** Percentage of pixels in a decoded PNG satisfying `isRed`. Alpha is ignored
 *  on purpose: captures and refs are opaque white-canvas composites. */
export function redPct(png, isRed = RED_SQUARE_PREDICATE.isRed) {
  const d = png.data;                     // RGBA, row-major (pngjs)
  const n = png.width * png.height;       // pixel count = denominator
  let red = 0;                            // matching-pixel count
  for (let i = 0; i < d.length; i += 4) if (isRed(d[i], d[i + 1], d[i + 2])) red++;
  return n === 0 ? 0 : (100 * red) / n;   // guard a zero-size PNG
}

/** The scorer's own eligibility idiom (BACKLOG "scorer idiom"): a cell counts
 *  only when it carries a numeric ssim and is not score-excluded. */
export function isScored(d) {
  return !!d && typeof d.ssim === 'number' && !d.scoreExcluded;
}

/** Composed capture filename for a manifest test key such as
 *  `css/css-flexbox/abspos/abspos-autopos-htb-ltr.html` — the exact name the
 *  feeders wrote and inject globbed: `wpt__<section>__<stem>.png`. */
export function captureName(testRel) {
  const section = testRel.split('/')[1];                       // css/<section>/...
  return `${safe(`wpt__${section}__${fixtureStem(testRel)}`)}.png`;
}

// Platform label → the per-section capture dir section-runner.sh writes.
const PLATFORM_DIRS = Object.freeze({ web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' });

/** Run the census over one run dir (`tools/titan/runs/<runId>`), optionally
 *  restricted to some sections. Returns `{ cells, summary }` where every cell
 *  is `{ section, test, platform, wptPass, ssim, capRedPct, refRedPct, flagged }`
 *  and the summary counts FLAGGED cells split by wptPass, per platform, with
 *  distinct-test counts — the shape the wave-49 note quoted. */
export function censusRun(runDir, { predicate = RED_SQUARE_PREDICATE, sections = null, repoRoot = REPO_ROOT } = {}) {
  const base = join(runDir, 'sections');                                   // section-runner layout
  const cache = new Map();                                                 // path → red% (refs are shared across cells)
  const pct = (p) => {                                                     // memoised decode + measure
    if (!cache.has(p)) cache.set(p, existsSync(p) ? redPct(PNG.sync.read(readFileSync(p)), predicate.isRed) : null);
    return cache.get(p);
  };
  const cells = [];
  for (const sec of readdirSync(base).sort()) {                            // deterministic order
    if (sections && !sections.includes(sec)) continue;                     // optional --section filter
    const mp = join(base, sec, 'manifest.json');
    if (!existsSync(mp)) continue;                                         // a section that never wrote a manifest has no cells
    const results = JSON.parse(readFileSync(mp, 'utf8')).wpt?.results ?? {};
    for (const [test, r] of Object.entries(results)) {
      const br = r.browserRef ?? {};
      if (!br.path) continue;                                              // no ref → nothing to compare red against
      const refPct = pct(resolve(repoRoot, br.path));                      // manifest paths are repo-relative
      for (const [platform, dir] of Object.entries(PLATFORM_DIRS)) {
        const d = br.diffs?.[`${platform}-ref`];
        if (!isScored(d)) continue;                                        // the scorer idiom, verbatim
        const capPct = pct(join(base, sec, dir, captureName(test)));
        // Flagged = capture is red where the ref is not; null pct = missing PNG, never flagged.
        const flagged = capPct !== null && refPct !== null &&
          capPct > predicate.capMinPct && refPct <= predicate.refMaxPct;
        cells.push({ section: sec, test, platform, wptPass: d.wptPass === true, ssim: d.ssim,
          capRedPct: capPct, refRedPct: refPct, flagged });
      }
    }
  }
  return { cells, summary: summarize(cells) };
}

/** Fold flagged cells into the note's shape: { passing: {total, web, ios,
 *  android, tests}, failing: {...}, scoredCells }. */
export function summarize(cells) {
  const bucket = () => ({ total: 0, web: 0, ios: 0, android: 0, tests: new Set() });
  const passing = bucket(), failing = bucket();
  for (const c of cells) {
    if (!c.flagged) continue;                                              // only red-where-ref-is-not cells
    const b = c.wptPass ? passing : failing;                               // split by the scorer's verdict
    b.total++; b[c.platform]++; b.tests.add(c.test);
  }
  const fin = (b) => ({ total: b.total, web: b.web, ios: b.ios, android: b.android, tests: b.tests.size });
  return { scoredCells: cells.length, passing: fin(passing), failing: fin(failing) };
}

// ── CLI ──────────────────────────────────────────────────────────────────────
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const arg = (n) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : null; };
  const target = args.find((a) => !a.startsWith('--') && a !== arg('--json') && a !== arg('--section'));
  if (!target) { console.error('usage: red-square-census.mjs <runId|runDir> [--json out.json] [--section <sec>]'); process.exit(2); }
  // Accept a bare runId (looked up under tools/titan/runs/) or any run dir path.
  const runDir = existsSync(join(target, 'sections')) ? resolve(target) : join(__dirname, 'runs', target);
  if (!existsSync(join(runDir, 'sections'))) { console.error(`red-square-census: no sections/ under ${runDir}`); process.exit(2); }
  const sections = arg('--section') ? [arg('--section')] : null;
  const { cells, summary } = censusRun(runDir, { sections });
  const p = summary.passing, f = summary.failing;                          // one line, the note's shape
  console.log(`red-square census ${runDir}: scored cells ${summary.scoredCells} | PASSING red ${p.total} ` +
    `(web ${p.web} / ios ${p.ios} / android ${p.android}) over ${p.tests} tests | FAILING red ${f.total} ` +
    `(web ${f.web} / ios ${f.ios} / android ${f.android}) over ${f.tests} tests | predicate strict-red ` +
    `r>200&g<80&b<80, cap>${RED_SQUARE_PREDICATE.capMinPct}%, ref<=${RED_SQUARE_PREDICATE.refMaxPct}%`);
  if (arg('--json')) writeFileSync(arg('--json'), JSON.stringify({ runDir, predicate: 'r>200&&g<80&&b<80; cap>0.5%; ref<=0%', summary, cells }, null, 1));
}
