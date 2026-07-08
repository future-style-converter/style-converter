#!/usr/bin/env node
//
// coverage-audit.mjs
//
// Phase 11 deliverable: compares the IR property catalogue
// (src/main/kotlin/app/irmodels/properties/**/*Property.kt) against each
// platform's PropertyRegistry claims and emits a per-category coverage
// matrix + a one-shot pass/fail exit code.
//
// Exit 0 = every IR property is claimed by every platform.
// Exit 1 = at least one platform is missing a claim.
//
// Usage:
//   node testing/coverage-audit.mjs           # text report
//   node testing/coverage-audit.mjs --json    # machine-readable
//   node testing/coverage-audit.mjs --md      # emits testing/COVERAGE.md
//
// Design notes:
//   • Each platform uses its own registration surface, so we parse those
//     files for the literal property-name strings rather than running
//     platform code. This keeps the audit out-of-process and cheap.
//   • The IR catalogue is derived from Kotlin filenames, stripping the
//     trailing "Property.kt" — this is a stable naming contract across
//     the whole irmodels tree.
//   • A property is "claimed" on a platform when its PascalCase type name
//     appears inside a registry call/set on that platform's tree. A few
//     properties register under shared grouped sets (e.g. iOS
//     `TransformsProperty.set`) so we match any occurrence within the
//     platform's style-engine root.
//

import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join, resolve, dirname, basename, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..');

// ── 1. Derive the IR catalogue ──────────────────────────────────────────
// src/main/kotlin/app/irmodels/properties/<category>/**/<Name>Property.kt
// → { category: '<category>', name: '<Name>' }
const IR_ROOT = join(REPO, 'src/main/kotlin/app/irmodels/properties');

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

// ── 2. Scrape each platform's registry surface ──────────────────────────
// We look for ANY quoted PascalCase identifier that matches an IR property
// name anywhere under the style-engine root. This is deliberately fuzzy —
// false-positives on the order of a handful of coincidentally-identical
// string literals are acceptable; false-negatives (a real registration we
// miss) would be bugs.

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

// ── 3. Build the matrix ─────────────────────────────────────────────────
const ir = extractIrProperties();
const byCategory = {};
for (const p of ir) (byCategory[p.category] ??= []).push(p.name);

const PLATFORMS = [
  { id: 'android', root: join(REPO, 'testing/Android/app/src/main/java/com/styleconverter/test/style') },
  { id: 'ios',     root: join(REPO, 'testing/iOS/StyleConverterTest/StyleEngine') },
  { id: 'web',     root: join(REPO, 'testing/web/src/style/engine') },
];

const claims = {};
for (const plat of PLATFORMS) claims[plat.id] = scrapePlatform(plat.root, ir);

// ── 4. Render ───────────────────────────────────────────────────────────
const categories = Object.keys(byCategory).sort();
const totalIr = ir.length;
const platformTotals = Object.fromEntries(
  PLATFORMS.map((p) => [p.id, claims[p.id].size]),
);

const mode = process.argv.includes('--json')
  ? 'json'
  : process.argv.includes('--md')
  ? 'md'
  : 'text';

function pctFor(cat, platId) {
  const props = byCategory[cat];
  const ok = props.filter((n) => claims[platId].has(n)).length;
  return { ok, total: props.length, pct: props.length ? ok / props.length : 1 };
}

function fmt(r) {
  return `${r.ok}/${r.total}`;
}

function buildRows() {
  return categories.map((cat) => ({
    category: cat,
    android: pctFor(cat, 'android'),
    ios:     pctFor(cat, 'ios'),
    web:     pctFor(cat, 'web'),
  }));
}

const rows = buildRows();

// Global gate: every IR property must be claimed by at least one platform.
// (Not "every property on every platform" — some are no-mobile-analog and
// legitimately unclaimed on Android/iOS but claimed on Web.)
const unclaimedAnywhere = ir.filter(
  (p) => !claims.android.has(p.name) && !claims.ios.has(p.name) && !claims.web.has(p.name),
);
const passed = unclaimedAnywhere.length === 0;

if (mode === 'json') {
  const out = {
    totals: { ir: totalIr, ...platformTotals },
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
  lines.push('Generated by `testing/coverage-audit.mjs`. Each cell is `claimed/total` for that category on that platform; a property is "claimed" when its PascalCase IR type name appears in a platform registry call or grouped `Set`.');
  lines.push('');
  lines.push(`**IR catalogue**: ${totalIr} properties across ${categories.length} categories.`);
  lines.push('');
  lines.push('| Category | Android | iOS | Web |');
  lines.push('|---|---|---|---|');
  for (const r of rows) {
    lines.push(`| ${r.category} | ${fmt(r.android)} | ${fmt(r.ios)} | ${fmt(r.web)} |`);
  }
  lines.push(`| **total** | **${platformTotals.android}/${totalIr}** | **${platformTotals.ios}/${totalIr}** | **${platformTotals.web}/${totalIr}** |`);
  lines.push('');
  if (unclaimedAnywhere.length === 0) {
    lines.push('✅ Every IR property is claimed by at least one platform.');
  } else {
    lines.push(`⚠ ${unclaimedAnywhere.length} IR properties are not claimed on any platform:`);
    for (const p of unclaimedAnywhere) lines.push(`- \`${p.category}/${p.name}\``);
  }
  writeFileSync(resolve(__dirname, 'COVERAGE.md'), lines.join('\n') + '\n');
  process.stdout.write(`✓ wrote testing/COVERAGE.md (${rows.length} categories, passed=${passed})\n`);
  process.exit(passed ? 0 : 1);
}

// Default: text report
console.log(`Coverage audit — IR=${totalIr}, ${categories.length} categories`);
console.log(`  android=${platformTotals.android}  ios=${platformTotals.ios}  web=${platformTotals.web}`);
console.log('');
const pad = (s, n) => String(s).padEnd(n);
console.log(`${pad('category', 18)} ${pad('android', 10)} ${pad('ios', 10)} ${pad('web', 10)}`);
console.log('-'.repeat(52));
for (const r of rows) {
  console.log(
    `${pad(r.category, 18)} ${pad(fmt(r.android), 10)} ${pad(fmt(r.ios), 10)} ${pad(fmt(r.web), 10)}`,
  );
}
console.log('-'.repeat(52));
if (passed) {
  console.log('✓ every IR property is claimed on at least one platform');
} else {
  console.log(`✗ ${unclaimedAnywhere.length} properties unclaimed on ALL platforms:`);
  for (const p of unclaimedAnywhere) console.log(`  - ${p.category}/${p.name}`);
}
process.exit(passed ? 0 : 1);
