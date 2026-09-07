#!/usr/bin/env node
//
// tools/visual/gen-applier-worklist.mjs — generate the applier-campaign
// worklist: ONE machine-readable row per IR property fusing every coverage
// axis the repo records, so campaign waves work down a checklist instead of
// re-deriving state.
//
// Fused sources (each with a different altitude + failure mode):
//   1. The IR catalogue — converter/src/main/kotlin/app/irmodels/properties
//      (same enumeration rules as coverage-audit.mjs: *Property.kt minus
//      Serializer/ValueTypes/IRProperty; category = first dir segment).
//   2. The converged verification tracker (91 passing / 419 blocked-platform
//      / 37 exhausted / 3 failing, per-row root-cause notes + min SSIM).
//      NOT in the working tree — the docs/reports/ prune (PR #8) deleted it —
//      but fully recoverable from the prune commit's parent, so we read it
//      via `git show` at a PINNED ref and re-verify its counts every run.
//   3. The REAL-applier floor per platform — a dedicated <Name>Applier.<ext>
//      exists under the platform engine root (same rule as coverage-audit;
//      under-counts grouped appliers by design — it is a floor, not truth).
//   4. Per-property fixture presence under fixtures/properties/ via the
//      PascalCase→kebab rule (verified round-trip clean over all 550 names;
//      ZIndex→z-index is the only consecutive-capitals case) + the 5 known
//      cross-category placements.
//   5. The wont-fix taxonomy: whole categories whose appliers are intentional
//      no-ops because no mobile analogue exists (pinned by each runtime's
//      Phase-10 facade Registration files).
//
// Buckets (precedence order):
//   failing-real-divergence  tracker `failing` — real cross-platform bugs
//   verified                 tracker `passing` — SSIM ≥0.95 every variant
//   wontfix-no-analogue      category is facade-no-op everywhere by design
//   exhausted                tracker `exhausted` — no meaningful visual test
//   blocked-capability       tracker `blocked-platform` — needs a harness
//                            tier (animation state, scroll, tables, …) or a
//                            runtime capability (svg subtree, markup
//                            consumers) the platforms don't have yet
//
// Honesty caveat carried in the output: the tracker's `passing` evidence is
// NOT re-runnable from committed artifacts — fixtures/properties/** has zero
// committed baselines (only fixtures/visual-test.json is baselined), so
// `verified` means "the converged audit campaign measured it", not "CI
// re-proves it today". Recommitting per-property baselines is campaign work.
//
// Usage: node tools/visual/gen-applier-worklist.mjs [--json-only]
// Output: tools/visual/applier-worklist.json + a human summary on stdout.

import { readdirSync, readFileSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, relative, basename, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const IR_ROOT = join(REPO, 'converter/src/main/kotlin/app/irmodels/properties');
const FIXTURES = join(REPO, 'fixtures/properties');
const OUT_PATH = join(REPO, 'tools/visual/applier-worklist.json');

// The prune commit that deleted docs/reports/; its PARENT still carries the
// converged tracker. Pinned — regeneration is deterministic in any clone
// with full history.
const TRACKER_REF = '1e0234f6^';
const TRACKER_PATH = 'docs/reports/TIER1_VARIANT_DEPTH.md';

// Whole categories that are intentional no-op facades on every platform
// (no mobile analogue) — see each runtime's <Category>Registration file.
const WONTFIX_CATEGORIES = new Set([
  'speech', 'regions', 'print', 'paging', 'math', 'navigation',
  'experimental', 'rhythm',
]);

// The 5 fixtures that live in a different category dir than their property
// (survey-verified; the kebab name is globally unique so this is safe).
const CROSS_CATEGORY_FIXTURES = {
  AspectRatio: 'sizing/aspect-ratio.json',
  Color: 'typography/color.json',
  Quotes: 'typography/quotes.json',
  Isolation: 'color/isolation.json',
  ScrollTimeline: 'animations/scroll-timeline.json',
};

// ── helpers ──────────────────────────────────────────────────────────────────

function walk(dir) {
  const out = [];
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

/** PascalCase → kebab-case, the fixture/CSS naming rule. A new word starts at
 *  an uppercase preceded by a lowercase/digit, OR an uppercase followed by
 *  uppercase-then-lowercase (the ZIndex → z-index rule). */
function kebab(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .toLowerCase();
}

// ── 1. catalogue ─────────────────────────────────────────────────────────────

const catalogue = walk(IR_ROOT)
  .filter((f) =>
    f.endsWith('Property.kt') &&
    !f.includes('Serializer') &&
    !f.includes('ValueTypes') &&
    !f.endsWith('IRProperty.kt'))
  .map((f) => ({
    category: relative(IR_ROOT, f).split('/')[0],
    name: basename(f).replace(/Property\.kt$/, ''),
  }));
if (catalogue.length !== 550) {
  console.error(`FATAL: catalogue enumerates ${catalogue.length} properties, expected 550 — enumeration rules drifted vs coverage-audit.mjs`);
  process.exit(1);
}

// ── 2. tracker (recovered from git history, counts re-verified) ─────────────

const trackerMd = execFileSync('git', ['show', `${TRACKER_REF}:${TRACKER_PATH}`],
  { cwd: REPO, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
const tracker = new Map();
for (const line of trackerMd.split('\n')) {
  // Row: | # | category | property | parser path | target | status | actual | min SSIM | notes |
  const m = line.match(/^\|\s*\d+\s*\|([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)\|\s*(passing|blocked-platform|exhausted|failing)\s*\|([^|]+)\|([^|]+)\|(.*)\|\s*$/);
  if (!m) continue;
  const [, cat, prop, , target, status, actual, minSsim, notes] = m;
  tracker.set(prop.trim(), {
    status,
    category: cat.trim(),
    targetVariants: Number(target.trim()) || null,
    actualVariants: Number(actual.trim()) || null,
    minSsim: Number(minSsim.trim()) || null,
    notes: notes.trim(),
  });
}
const statusCounts = {};
for (const t of tracker.values()) statusCounts[t.status] = (statusCounts[t.status] ?? 0) + 1;
const expect = { passing: 91, 'blocked-platform': 419, exhausted: 37, failing: 3 };
for (const [k, v] of Object.entries(expect)) {
  if (statusCounts[k] !== v) {
    console.error(`FATAL: recovered tracker has ${statusCounts[k]} '${k}' rows, expected ${v} — wrong ref or parse drift`);
    process.exit(1);
  }
}

// ── 3. real-applier floor per platform ───────────────────────────────────────

const ENGINE_ROOTS = {
  android: [join(REPO, 'runtimes/compose/src/main/java/com/styleconverter/runtime'), '.kt'],
  ios: [join(REPO, 'runtimes/swiftui/Sources/StyleConverterRuntime'), '.swift'],
  web: [join(REPO, 'runtimes/web/src/engine'), '.ts'],
};
const names = new Set(catalogue.map((p) => p.name));
const realByPlatform = {};
for (const [platform, [root, ext]] of Object.entries(ENGINE_ROOTS)) {
  const suffix = 'Applier' + ext;
  realByPlatform[platform] = new Set(
    walk(root)
      .filter((f) => f.endsWith(suffix))
      .map((f) => basename(f).slice(0, -suffix.length))
      .filter((stem) => names.has(stem)),
  );
}

// ── 4. fixture presence ──────────────────────────────────────────────────────

function fixtureFor(p) {
  const exact = join(FIXTURES, p.category, `${kebab(p.name)}.json`);
  if (existsSync(exact)) return { fixture: 'exact', fixturePath: relative(REPO, exact) };
  const cross = CROSS_CATEGORY_FIXTURES[p.name];
  if (cross && existsSync(join(FIXTURES, cross))) {
    return { fixture: 'cross-category', fixturePath: `fixtures/properties/${cross}` };
  }
  return { fixture: 'none', fixturePath: null };
}

// ── 5. fuse + bucket ─────────────────────────────────────────────────────────

const properties = catalogue.map((p) => {
  const t = tracker.get(p.name) ?? null;
  const bucket =
    t?.status === 'failing' ? 'failing-real-divergence' :
    t?.status === 'passing' ? 'verified' :
    WONTFIX_CATEGORIES.has(p.category) ? 'wontfix-no-analogue' :
    t?.status === 'exhausted' ? 'exhausted' :
    'blocked-capability';
  return {
    name: p.name,
    category: p.category,
    kebab: kebab(p.name),
    bucket,
    tracker: t ? { status: t.status, targetVariants: t.targetVariants, actualVariants: t.actualVariants, minSsim: t.minSsim, notes: t.notes } : null,
    realApplier: {
      android: realByPlatform.android.has(p.name),
      ios: realByPlatform.ios.has(p.name),
      web: realByPlatform.web.has(p.name),
    },
    ...fixtureFor(p),
  };
}).sort((a, b) => a.category.localeCompare(b.category) || a.name.localeCompare(b.name));

const untracked = properties.filter((p) => !p.tracker);
const byBucket = {};
for (const p of properties) byBucket[p.bucket] = (byBucket[p.bucket] ?? 0) + 1;

const out = {
  _snapshot: true,
  _note: 'Applier-campaign worklist: one row per IR property fusing the recovered verification tracker (91/419/37/3), the live real-applier floor, per-property fixture presence, and the wont-fix taxonomy. Regenerate with node tools/visual/gen-applier-worklist.mjs (needs full git history for the tracker recovery).',
  generatedFrom: {
    trackerRef: TRACKER_REF,
    trackerPath: TRACKER_PATH,
    devSha: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: REPO, encoding: 'utf8' }).trim(),
  },
  honestyCaveats: [
    "verified means the CONVERGED AUDIT CAMPAIGN measured SSIM >= 0.95 on every variant — it is NOT re-runnable from committed artifacts today: fixtures/properties/** has ZERO committed baselines (only fixtures/visual-test.json is baselined). Recommitting per-property baselines is part of the campaign.",
    "EMPIRICAL (wave-0 full re-verification, 2026-07-17 — see tools/visual/reverify-wave0.json for per-property current truth): of the 91 tracker-verified properties re-measured on live devices against the recovered fixtures, only 11 still hold >= 0.95 today; 50 land in the 0.90-0.95 near-miss band (dominated by the deterministic Android glyph-rasterization wall) and 30 are DEEP (< 0.90) real current divergences (worst: layout/Position 0.67, background/BackgroundRepeat 0.66, typography/WordSpacing 0.68). All 3 failing properties confirmed still failing. The tracker's verdicts are HISTORICAL; reverify-wave0.json is the current-truth overlay for this bucket.",
    'realApplier is a FLOOR: grouped appliers (LayoutApplier.kt, FlexboxApplier.swift, ScrollMarginApplier.ts, …) render many properties from one file whose name matches at most one IR property.',
    'wontfix-no-analogue overrides the tracker bucket for whole categories pinned as intentional no-op facades (speech/regions/print/paging/math/navigation/experimental/rhythm).',
    'blocked-capability mixes HARNESS blocks (animation state, scroll, interaction pseudo-classes) with RUNTIME blocks (svg subtree, table/list markup consumers) — the per-row tracker notes distinguish them; wave planning must read the notes.',
  ],
  summary: {
    total: properties.length,
    byBucket,
    fixtures: {
      exact: properties.filter((p) => p.fixture === 'exact').length,
      crossCategory: properties.filter((p) => p.fixture === 'cross-category').length,
      none: properties.filter((p) => p.fixture === 'none').length,
    },
    // Which buckets have a runnable per-property fixture TODAY. Load-bearing
    // for wave planning: a verified/failing property with fixture:none cannot
    // be re-verified without re-authoring its fixture first (the campaign-era
    // fixtures under examples/properties/perfect/ were pruned by the
    // 2026-07-08 hard prune 1e0234f6 (#8) and nothing under fixtures/
    // replaced them — even Scale,
    // one of the 3 real divergences, has no current fixture).
    fixtureByBucket: Object.fromEntries(
      Object.keys(byBucket).sort().map((b) => [b, {
        withFixture: properties.filter((p) => p.bucket === b && p.fixture !== 'none').length,
        withoutFixture: properties.filter((p) => p.bucket === b && p.fixture === 'none').length,
      }]),
    ),
    realApplier: {
      android: properties.filter((p) => p.realApplier.android).length,
      ios: properties.filter((p) => p.realApplier.ios).length,
      web: properties.filter((p) => p.realApplier.web).length,
    },
    untrackedProperties: untracked.map((p) => `${p.category}/${p.name}`),
  },
  properties,
};

writeFileSync(OUT_PATH, JSON.stringify(out, null, 2) + '\n');

if (!process.argv.includes('--json-only')) {
  console.log(`applier-worklist: ${properties.length} properties → ${relative(REPO, OUT_PATH)}`);
  console.log('buckets:', JSON.stringify(byBucket));
  console.log('fixtures:', JSON.stringify(out.summary.fixtures));
  console.log('real appliers:', JSON.stringify(out.summary.realApplier));
  if (untracked.length) console.log(`untracked (in catalogue, missing from tracker): ${out.summary.untrackedProperties.join(', ')}`);
}
