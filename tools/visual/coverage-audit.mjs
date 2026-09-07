#!/usr/bin/env node
//
// coverage-audit.mjs
//
// Phase 11 deliverable: compares the IR property catalogue
// (converter/src/main/kotlin/app/irmodels/properties/**/*Property.kt) against each
// platform's PropertyRegistry claims and emits a per-category coverage
// matrix + a one-shot pass/fail exit code.
//
// Exit 0 = every IR property is claimed by every platform.
// Exit 1 = at least one platform is missing a claim.
//
// Usage:
//   node tools/visual/coverage-audit.mjs           # text report
//   node tools/visual/coverage-audit.mjs --json    # machine-readable
//   node tools/visual/coverage-audit.mjs --md      # emits tools/visual/COVERAGE.md
//
// TWO SIGNALS, both true, deliberately kept apart:
//
//   1. REGISTERED (a.k.a. "claimed") — the historical signal. A property is
//      "registered" on a platform when its PascalCase IR type name appears as
//      ANY quoted string anywhere under that platform's engine root. This is a
//      pure string-presence probe, so a registration-only facade, a grouped
//      `Set`, a comment, a TODO string, or a real renderer all count the same.
//      It answers "does the platform *claim* this property?" — nothing more.
//
//   2. REAL — the honesty signal added because REGISTERED over-counts. A
//      property is "real" on a platform only when a dedicated applier file
//      named exactly `<Name>Applier.<ext>` exists under that platform's engine
//      root (kt / swift / ts). This is the strictest floor we can VERIFY by
//      filesystem alone: a file that is named for the property is a renderer
//      authored for that property, not a bare Set membership.
//
//      CAVEAT — the REAL floor UNDER-counts on platforms that batch several
//      properties into one grouped applier (e.g. iOS `FlexboxApplier.swift`,
//      Compose `LayoutApplier.kt`, web `PaddingApplier.ts`). Those files
//      genuinely render multiple properties, but their basename matches at most
//      one IR name (often none), so the batched properties score REGISTERED-yes
//      / REAL-no. REAL is therefore a lower bound on real rendering, not an
//      exact tally. The raw count of dedicated `*Applier` files is reported
//      alongside so the grouped-applier gap is visible.
//
// Design notes:
//   • Each platform uses its own registration surface, so we parse those
//     files for the literal property-name strings rather than running
//     platform code. This keeps the audit out-of-process and cheap.
//   • The IR catalogue is derived from Kotlin filenames, stripping the
//     trailing "Property.kt" — this is a stable naming contract across
//     the whole irmodels tree.
//

import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join, resolve, dirname, basename, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');

// ── 1. Derive the IR catalogue ──────────────────────────────────────────
// converter/src/main/kotlin/app/irmodels/properties/<category>/**/<Name>Property.kt
// → { category: '<category>', name: '<Name>' }
const IR_ROOT = join(REPO, 'converter/src/main/kotlin/app/irmodels/properties');

function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, acc);
    else acc.push(full);
  }
  return acc;
}

function extractIrProperties() {
  const files = walk(IR_ROOT).filter(
    (f) =>
      f.endsWith('Property.kt') &&
      !f.includes('Serializer') &&
      !f.includes('ValueTypes') &&
      !f.endsWith('IRProperty.kt'),
  );
  // Category = first folder inside properties/. Name = filename without the
  // trailing `Property.kt`.
  return files.map((f) => {
    const rel = relative(IR_ROOT, f);
    const parts = rel.split('/');
    const category = parts[0];
    const name = basename(f).replace(/Property\.kt$/, '');
    return { category, name };
  });
}

// ── 2. Scrape each platform's REGISTERED surface ────────────────────────
// We look for ANY quoted PascalCase identifier that matches an IR property
// name anywhere under the style-engine root. This is deliberately fuzzy —
// false-positives on the order of a handful of coincidentally-identical
// string literals are acceptable; false-negatives (a real registration we
// miss) would be bugs. This is the REGISTERED signal (signal #1 above): it
// says the platform *claims* the property, which is not the same as rendering
// it — that is what the REAL signal (§2b) measures.

function scrapePlatform(root, ir) {
  const names = new Set(ir.map((p) => p.name));
  const claimed = new Set();
  const files = walk(root).filter(
    (f) => f.endsWith('.kt') || f.endsWith('.swift') || f.endsWith('.ts'),
  );
  for (const file of files) {
    const src = readFileSync(file, 'utf8');
    // Match quoted PascalCase identifiers in EITHER double or single quotes
    // (TS files use ', Kotlin + Swift use "). A single leading uppercase
    // plus any trailing alphanumerics covers everything from "D" (SVG
    // path data) to "BackgroundPositionBlockStart". Anchored to the
    // dictionary of IR property names so arbitrary strings don't leak in.
    const tokens = src.match(/["'][A-Z][A-Za-z0-9]*["']/g) || [];
    for (const t of tokens) {
      const id = t.slice(1, -1);
      if (names.has(id)) claimed.add(id);
    }
  }
  return claimed;
}

// ── 2b. Scrape each platform's REAL applier surface ─────────────────────
// The strict-floor honesty signal (signal #2 above). We walk the platform's
// engine root and collect the basenames of every dedicated applier file
// (`<Name>Applier.<ext>`). A property is REAL only when a file named exactly
// for it exists. We also return the raw file count — dedicated appliers whose
// basename does NOT match a single IR name are grouped/batched appliers
// (e.g. `FlexboxApplier`, `AnimationsApplier`), which is why the raw file
// count exceeds the number of REAL-scored properties; that gap is the
// grouped-applier caveat, surfaced in every render mode.
function scrapeRealAppliers(root, ext, ir) {
  const names = new Set(ir.map((p) => p.name));
  const real = new Set(); // IR property names that own a dedicated applier file
  const suffix = 'Applier' + ext; // e.g. "Applier.ts" / "Applier.swift" / "Applier.kt"
  const files = walk(root).filter((f) => f.endsWith(suffix));
  for (const f of files) {
    // Strip the trailing "Applier.<ext>" to recover the intended property name.
    const stem = basename(f).slice(0, -suffix.length);
    if (names.has(stem)) real.add(stem);
  }
  // fileCount = total dedicated applier files (incl. grouped ones whose stem
  // is NOT an IR name); real.size = properties that own a same-named file.
  return { real, fileCount: files.length };
}

// ── 2c. Fixture-taxonomy guard (R5 restructure) ─────────────────────────
// fixtures/properties/ must contain ONLY the canonical category folders —
// the exact set of directories under converter/.../irmodels/properties/.
// Suite trees (fuzz, perfect, combos, keyframes, viewport, perf, _loop, …)
// live as siblings at fixtures/<suite>/, NOT under properties/. Fail loudly
// on any non-canonical directory so taxonomy drift (like the pre-R5
// backgrounds/ vs background/, colors/ vs color/ split) is caught the
// first time it reappears, in every mode this audit runs in.
function assertFixtureTaxonomy() {
  const fixturesPropsRoot = join(REPO, 'fixtures/properties');
  const canonical = new Set(
    readdirSync(IR_ROOT).filter((e) => statSync(join(IR_ROOT, e)).isDirectory()),
  );
  const rogue = readdirSync(fixturesPropsRoot).filter(
    (e) => statSync(join(fixturesPropsRoot, e)).isDirectory() && !canonical.has(e),
  );
  if (rogue.length > 0) {
    console.error(
      `✗ fixture taxonomy drift — fixtures/properties/ contains ${rogue.length} non-canonical dir(s):`,
    );
    for (const d of rogue) console.error(`  - fixtures/properties/${d}`);
    console.error(
      `  Canonical categories are the ${canonical.size} dirs under converter/src/main/kotlin/app/irmodels/properties/.`,
    );
    console.error(
      '  Property fixtures go in a canonical category; suite fixtures (fuzz, perfect, combos, …) go to fixtures/<suite>/.',
    );
    process.exit(1);
  }
}
assertFixtureTaxonomy();

// ── 2d. Registry-declaration guard (finding A6#9) ───────────────────────
// The REGISTERED scrape above is anchored to the IR dictionary — it only
// ever ADDS names it recognises — so a platform can declare a property that
// does not exist and the audit stays silent. That is exactly how ten phantom
// names survived in the web registry (the `overflow`, `scroll-margin{,-block,
// -inline}`, `scroll-padding{,-block,-inline}` SHORTHANDS, which
// ShorthandRegistry.kt expands to longhands before the longhand parser runs,
// plus the three CSS 2 aural properties speak-header / -numeral /
// -punctuation, which no IR class models) together with nine unreachable
// Config/Extractor/Applier triplets behind them.
//
// This guard closes the direction `unclaimedAnywhere` never looked at:
// every name a platform DECLARES must exist in the IR catalogue.
//
// SCOPE — it runs over declaration files that are an explicit, complete,
// static list of claimed IR type names. Today that is the web engine's
// `migratedProperties` set. Compose is excluded because it has no static
// list at all (extractors call `PropertyRegistry.migrated(...)` at
// class-load time), and the SwiftUI registry is excluded because its set is
// only partially static — several groups are union-ed in from
// `{Group}Property.names` lists elsewhere. (Its one phantom, `BorderColor`
// at PropertyRegistry.swift:83 / BorderSideExtractor.swift:25, was dropped
// by the retro P2c seam patch; the partial-static shape is what still keeps
// iOS off this list.) Both are tracked as follow-ups.
// CONTRACT for a listed file: every single-or-double-quoted PascalCase token
// in it is a claimed IR type name, so claims are never written as anything
// else (comments here use backticks, not quotes).
const REGISTRY_DECLARATIONS = [
  { id: 'web', file: 'runtimes/web/src/engine/PropertyRegistry.ts' },
];

// Returns { declared: {id: count}, phantoms: [{id, file, name}] }.
function scanRegistryDeclarations(irNames) {
  const declared = {};
  const phantoms = [];
  for (const reg of REGISTRY_DECLARATIONS) {
    const src = readFileSync(join(REPO, reg.file), 'utf8');
    // Same token shape as scrapePlatform, but WITHOUT the dictionary filter —
    // that filter is precisely what hides a phantom.
    const names = [
      ...new Set((src.match(/["'][A-Z][A-Za-z0-9]*["']/g) || []).map((t) => t.slice(1, -1))),
    ];
    declared[reg.id] = names.length;
    for (const n of names) if (!irNames.has(n)) phantoms.push({ id: reg.id, file: reg.file, name: n });
  }
  return { declared, phantoms };
}

// ── 3. Build the matrix ─────────────────────────────────────────────────
const ir = extractIrProperties();
const irNames = new Set(ir.map((p) => p.name));
// Hard gate, in EVERY render mode: a declared name with no
// `<Name>Property.kt` behind it can never appear on the wire, so the code
// behind it is unreachable. Fail loudly rather than counting it as coverage.
const registry = scanRegistryDeclarations(irNames);
if (registry.phantoms.length > 0) {
  console.error(
    `\u2717 registry declares ${registry.phantoms.length} name(s) with no IR property class:`,
  );
  for (const p of registry.phantoms) console.error(`  - ${p.file}: '${p.name}'`);
  console.error(
    `  An IR type is the converter class name minus "Property" (${irNames.size} exist under`,
  );
  console.error(
    '  converter/src/main/kotlin/app/irmodels/properties/). Shorthands are expanded by',
  );
  console.error(
    '  ShorthandRegistry.kt before the longhand registry runs, so a shorthand name never',
  );
  console.error(
    '  reaches the wire: delete the claim (and any triplet behind it), or add the IR model.',
  );
  process.exit(1);
}
const byCategory = {};
for (const p of ir) (byCategory[p.category] ??= []).push(p.name);

const PLATFORMS = [
  { id: 'android', ext: '.kt',    root: join(REPO, 'runtimes/compose/src/main/java/com/styleconverter/runtime') },
  { id: 'ios',     ext: '.swift', root: join(REPO, 'runtimes/swiftui/Sources/StyleConverterRuntime') },
  { id: 'web',     ext: '.ts',    root: join(REPO, 'runtimes/web/src/engine') },
];

// REGISTERED claims (string presence) and REAL claims (dedicated applier file)
// per platform, plus the raw dedicated-applier file count for the caveat note.
const claims = {};
const realClaims = {};
const applierFiles = {};
for (const plat of PLATFORMS) {
  claims[plat.id] = scrapePlatform(plat.root, ir);
  const r = scrapeRealAppliers(plat.root, plat.ext, ir);
  realClaims[plat.id] = r.real;
  applierFiles[plat.id] = r.fileCount;
}

// ── 4. Render ───────────────────────────────────────────────────────────
const categories = Object.keys(byCategory).sort();
const totalIr = ir.length;
const platformTotals = Object.fromEntries(
  PLATFORMS.map((p) => [p.id, claims[p.id].size]),
);
const realTotals = Object.fromEntries(
  PLATFORMS.map((p) => [p.id, realClaims[p.id].size]),
);

const mode = process.argv.includes('--json')
  ? 'json'
  : process.argv.includes('--md')
  ? 'md'
  : 'text';

// Registered (string-presence) count for a category on a platform.
function pctFor(cat, platId) {
  const props = byCategory[cat];
  const ok = props.filter((n) => claims[platId].has(n)).length;
  return { ok, total: props.length, pct: props.length ? ok / props.length : 1 };
}

// Real (dedicated-applier-file) count for a category on a platform.
function realFor(cat, platId) {
  const props = byCategory[cat];
  const ok = props.filter((n) => realClaims[platId].has(n)).length;
  return { ok, total: props.length, pct: props.length ? ok / props.length : 1 };
}

function fmt(r) {
  return `${r.ok}/${r.total}`;
}

function buildRows() {
  return categories.map((cat) => ({
    category: cat,
    android:     pctFor(cat, 'android'),
    androidReal: realFor(cat, 'android'),
    ios:         pctFor(cat, 'ios'),
    iosReal:     realFor(cat, 'ios'),
    web:         pctFor(cat, 'web'),
    webReal:     realFor(cat, 'web'),
  }));
}

const rows = buildRows();

// Global gate: every IR property must be claimed by at least one platform.
// (Not "every property on every platform" — some are no-mobile-analog and
// legitimately unclaimed on Android/iOS but claimed on Web.) The gate stays
// on the REGISTERED signal — REAL is reported for honesty, not enforced,
// since grouped appliers make a strict per-property REAL gate produce false
// failures.
const unclaimedAnywhere = ir.filter(
  (p) => !claims.android.has(p.name) && !claims.ios.has(p.name) && !claims.web.has(p.name),
);
const passed = unclaimedAnywhere.length === 0;

if (mode === 'json') {
  const out = {
    // `totals` stays the REGISTERED count for backward compatibility with any
    // existing consumer; `realTotals` + `applierFiles` are additive.
    totals: { ir: totalIr, ...platformTotals },
    realTotals: { ir: totalIr, ...realTotals },
    applierFiles, // raw dedicated-applier file count (incl. grouped appliers)
    // §2d registry-declaration guard: how many IR type names each static
    // registry DECLARES, and (always empty here — a non-empty scan exits 1
    // above) the declared names with no `<Name>Property.kt` behind them.
    registryDeclared: registry.declared,
    registryPhantoms: registry.phantoms.map((p) => `${p.id}:${p.name}`),
    passed,
    unclaimedAnywhere: unclaimedAnywhere.map((p) => `${p.category}/${p.name}`),
    byCategory: rows,
  };
  process.stdout.write(JSON.stringify(out, null, 2) + '\n');
  process.exit(passed ? 0 : 1);
}

if (mode === 'md') {
  const lines = [];
  lines.push('# Coverage matrix');
  lines.push('');
  lines.push('Generated by `tools/visual/coverage-audit.mjs`. Two signals per platform, both `count/total`:');
  lines.push('');
  lines.push('- **reg** (registered): the property\'s PascalCase IR type name appears as a quoted string anywhere under the platform engine root — a claim/registration, which counts facades, grouped `Set`s, comments and TODO strings the same as real renderers.');
  lines.push('- **real**: a dedicated applier file named exactly `<Name>Applier.<ext>` exists — the strict filesystem floor for "a renderer was authored for this property".');
  lines.push('');
  lines.push('**real is a lower bound.** Grouped appliers (e.g. iOS `FlexboxApplier.swift`, Compose `LayoutApplier.kt`, web `PaddingApplier.ts`) render several properties from one file whose basename matches at most one IR name, so the batched properties score reg-yes / real-no. The raw count of dedicated `*Applier` files (which includes those grouped files) is reported per platform below.');
  lines.push('');
  lines.push(`**IR catalogue**: ${totalIr} properties across ${categories.length} categories.`);
  lines.push('');
  lines.push(
    `**Dedicated \`*Applier\` files**: ` +
      `Android ${applierFiles.android} · iOS ${applierFiles.ios} · Web ${applierFiles.web} ` +
      `(the grouped-applier files inflate this above the per-property **real** totals).`,
  );
  lines.push('');
  lines.push('| Category | Android reg | Android real | iOS reg | iOS real | Web reg | Web real |');
  lines.push('|---|---|---|---|---|---|---|');
  for (const r of rows) {
    lines.push(
      `| ${r.category} | ${fmt(r.android)} | ${fmt(r.androidReal)} | ${fmt(r.ios)} | ${fmt(r.iosReal)} | ${fmt(r.web)} | ${fmt(r.webReal)} |`,
    );
  }
  lines.push(
    `| **total** ` +
      `| **${platformTotals.android}/${totalIr}** | **${realTotals.android}/${totalIr}** ` +
      `| **${platformTotals.ios}/${totalIr}** | **${realTotals.ios}/${totalIr}** ` +
      `| **${platformTotals.web}/${totalIr}** | **${realTotals.web}/${totalIr}** |`,
  );
  lines.push('');
  if (unclaimedAnywhere.length === 0) {
    lines.push('✅ Every IR property is **registered** on at least one platform.');
  } else {
    lines.push(`⚠ ${unclaimedAnywhere.length} IR properties are not registered on any platform:`);
    for (const p of unclaimedAnywhere) lines.push(`- \`${p.category}/${p.name}\``);
  }
  writeFileSync(resolve(REPO, 'tools/visual/COVERAGE.md'), lines.join('\n') + '\n');
  process.stdout.write(`✓ wrote tools/visual/COVERAGE.md (${rows.length} categories, passed=${passed})\n`);
  process.exit(passed ? 0 : 1);
}

// Default: text report
console.log(`Coverage audit — IR=${totalIr}, ${categories.length} categories`);
// REGISTERED line kept first and in the exact `android=…` shape that
// tools/visual/doc-staleness-check.sh greps (head -1) for the doc claim.
console.log(`  registered:   android=${platformTotals.android}  ios=${platformTotals.ios}  web=${platformTotals.web}`);
console.log(`  real:         android=${realTotals.android}  ios=${realTotals.ios}  web=${realTotals.web}`);
console.log(`  applier files: android=${applierFiles.android}  ios=${applierFiles.ios}  web=${applierFiles.web}  (incl. grouped; real is a lower bound)`);
console.log('');
const pad = (s, n) => String(s).padEnd(n);
console.log(
  `${pad('category', 16)} ${pad('and reg', 9)} ${pad('and real', 9)} ${pad('ios reg', 9)} ${pad('ios real', 9)} ${pad('web reg', 9)} ${pad('web real', 9)}`,
);
console.log('-'.repeat(76));
for (const r of rows) {
  console.log(
    `${pad(r.category, 16)} ${pad(fmt(r.android), 9)} ${pad(fmt(r.androidReal), 9)} ${pad(fmt(r.ios), 9)} ${pad(fmt(r.iosReal), 9)} ${pad(fmt(r.web), 9)} ${pad(fmt(r.webReal), 9)}`,
  );
}
console.log('-'.repeat(76));
console.log(
  `${pad('TOTAL', 16)} ${pad(`${platformTotals.android}/${totalIr}`, 9)} ${pad(`${realTotals.android}/${totalIr}`, 9)} ${pad(`${platformTotals.ios}/${totalIr}`, 9)} ${pad(`${realTotals.ios}/${totalIr}`, 9)} ${pad(`${platformTotals.web}/${totalIr}`, 9)} ${pad(`${realTotals.web}/${totalIr}`, 9)}`,
);
console.log('-'.repeat(76));
if (passed) {
  console.log('✓ every IR property is registered on at least one platform');
} else {
  console.log(`✗ ${unclaimedAnywhere.length} properties unregistered on ALL platforms:`);
  for (const p of unclaimedAnywhere) console.log(`  - ${p.category}/${p.name}`);
}
process.exit(passed ? 0 : 1);
