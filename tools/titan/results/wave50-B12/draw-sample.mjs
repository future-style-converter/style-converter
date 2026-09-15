#!/usr/bin/env node
//
// tools/titan/results/wave50-B12/draw-sample.mjs — the committed DRAW.
//
// WHY THIS FILE EXISTS (BACKLOG obligation #3, retro A10#0/A2#1). Wave-49 lane
// I1 hand-verified 78 passing cells, called 27 visibly wrong, and left the
// verdict file in a session scratchpad that no longer exists — so the
// campaign's headline "the pass column is ~35% degenerate" became an
// unverifiable number. The sample MUST therefore be re-derivable from the
// tree: this script is the draw, `handverdicts.json` is its result, and the
// README carries the seed and the method. Re-running it on the same run dir
// reproduces the identical 100 cells.
//
// SAMPLING DESIGN. Simple random sample WITHOUT replacement from the WHOLE
// passing population (every scored cell in every one of the 30 sections whose
// `wptPass === true`), drawn by a partial Fisher-Yates shuffle over a
// deterministically-ordered population using mulberry32 seeded with SEED.
// SRS — not stratification, not a rule over any image metric — is the point:
// obligation #4 needs a defect set "NOT selected by any rule correlated with
// the check's own bars", and SRS is the only draw that is uncorrelated with
// every candidate metric by construction, and the only one whose rate carries
// an honest binomial interval.
//
// Usage: node draw-sample.mjs [--run <runId|dir>] [--n 100] [--seed N] [--json out]

import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { safe, fixtureStem } from '../../safe-name.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..', '..', '..');

// The draw's seed, pinned in the README. Any change re-draws a DIFFERENT
// sample, which is why it is a constant and not a clock read.
export const SEED = 20260914;
// Platform label → the per-section capture dir section-runner.sh writes, and
// the `<platform>-ref` diff key the scorer stamps. Order is part of the
// population ordering, hence part of the draw.
export const PLATFORM_DIRS = Object.freeze({ web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' });

/** mulberry32 — a 32-bit deterministic PRNG. Chosen because it is four lines
 *  of arithmetic with no library and identical output on every Node version,
 *  so the draw is reproducible from this file alone. */
export function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Composed capture filename for a manifest test key — the exact name the
 *  feeders wrote (split-combined-ir.mjs / composedPngName), reusing the ONE
 *  canonical stem derivation rather than re-deriving it. */
export function captureName(testRel) {
  const section = testRel.split('/')[1];
  return `${safe(`wpt__${section}__${fixtureStem(testRel)}`)}.png`;
}

/** The scorer's eligibility idiom, verbatim (BACKLOG "scorer idiom"). */
export function isScored(d) { return !!d && typeof d.ssim === 'number' && !d.scoreExcluded; }

/** Enumerate every PASSING scored cell under a run dir, in a deterministic
 *  order (section asc, then manifest test-key order, then web/ios/android). */
export function population(runDir, { repoRoot = REPO_ROOT } = {}) {
  const base = join(runDir, 'sections');
  const out = [];
  for (const sec of readdirSync(base).sort()) {
    const mp = join(base, sec, 'manifest.json');
    if (!existsSync(mp)) continue;                       // a section with no manifest has no cells
    const results = JSON.parse(readFileSync(mp, 'utf8')).wpt?.results ?? {};
    for (const [test, r] of Object.entries(results)) {
      const br = r.browserRef ?? {};
      if (!br.path) continue;                            // no frozen ref → nothing to look at
      for (const [platform, dir] of Object.entries(PLATFORM_DIRS)) {
        const d = br.diffs?.[`${platform}-ref`];
        if (!isScored(d) || d.wptPass !== true) continue; // PASSING cells only — that is the column under audit
        out.push({
          section: sec, test, platform,
          ssim: d.ssim,
          capture: join(base, sec, dir, captureName(test)),   // repo-relative
          ref: resolve(repoRoot, br.path),                    // manifest ref paths are repo-relative
        });
      }
    }
  }
  return out;
}

/** Partial Fisher-Yates over a copy: the first n entries after n swaps are a
 *  uniform SRS without replacement. Returns the drawn cells in draw order. */
export function drawSample(pop, n, seed) {
  const rnd = mulberry32(seed);
  const idx = pop.map((_, i) => i);
  const k = Math.min(n, idx.length);
  for (let i = 0; i < k; i++) {
    const j = i + Math.floor(rnd() * (idx.length - i));   // uniform over the untouched tail
    [idx[i], idx[j]] = [idx[j], idx[i]];
  }
  return idx.slice(0, k).map((i) => ({ populationIndex: i, ...pop[i] }));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const arg = (n, d) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : d; };
  const runArg = arg('--run', 'wave49-final');
  const runDir = existsSync(join(runArg, 'sections')) ? resolve(runArg) : join(REPO_ROOT, 'tools', 'titan', 'runs', runArg);
  const pop = population(runDir);
  const n = Number(arg('--n', '100')), seed = Number(arg('--seed', String(SEED)));
  const sample = drawSample(pop, n, seed);
  console.error(`population ${pop.length} passing cells; drew ${sample.length} with seed ${seed}`);
  const json = JSON.stringify({ runDir, seed, n, populationSize: pop.length, sample }, null, 1);
  const out = arg('--json', null);
  if (out) writeFileSync(out, json); else console.log(json);
}
