#!/usr/bin/env node
// Pre-convert the 15 Tier-3 component fixtures into per-name IR JSONs that
// the Tier-5 ?fixture=<Name> route can fetch. Output:
//   apps/web-harness/public/fixtures/<Name>.json
//
// Why we pre-convert instead of converting in-browser:
// the JVM converter is the source of truth for CSS → IR (it owns all
// shorthand expansion, value normalisation, and the parser registry).
// Reimplementing any of that in JS would diverge from Android/iOS over
// time. So we shell out once at fixture-build time and ship plain JSON
// from public/.
//
// Run via `npm run build-fixtures` (added to apps/web-harness/package.json).
//
// Idempotent: skips files whose source JSON hasn't changed since the
// last build (mtime comparison). Force a full rebuild with --force.

import { readdirSync, mkdirSync, existsSync, statSync, copyFileSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE       = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT  = resolve(HERE, '../..');
const FIXTURES_SRC = join(REPO_ROOT, 'fixtures/components');
const FIXTURES_OUT = join(HERE, 'public/fixtures');
const TMP_OUT      = join(REPO_ROOT, 'out/tmpOutput.json');

const force = process.argv.includes('--force');

mkdirSync(FIXTURES_OUT, { recursive: true });

const sources = readdirSync(FIXTURES_SRC).filter((f) => f.endsWith('.json'));
let built = 0;
let skipped = 0;
const failures = [];

for (const src of sources) {
  const name = src.replace(/\.json$/, '');
  const srcPath = join(FIXTURES_SRC, src);
  const outPath = join(FIXTURES_OUT, src);

  // Idempotency: skip when the output exists and is newer than the source.
  // Cheap mtime check; the converter is the slow part so this matters when
  // a contributor only touched one fixture.
  if (!force && existsSync(outPath)) {
    const srcMtime = statSync(srcPath).mtimeMs;
    const outMtime = statSync(outPath).mtimeMs;
    if (outMtime >= srcMtime) {
      skipped++;
      continue;
    }
  }

  try {
    // Shell out to the JVM converter the same way test-all.sh does. We use
    // `--quiet` so a clean success leaves no chatter; failures will print
    // gradle's own error stream, which is what we want to see.
    execFileSync(
      './gradlew',
      ['--no-daemon', ':converter:run', `--args=convert --from css --to ir -i ${srcPath} -o out`, '--quiet'],
      { cwd: REPO_ROOT, stdio: ['ignore', 'ignore', 'inherit'] }
    );

    if (!existsSync(TMP_OUT)) {
      throw new Error(`converter ran but ${TMP_OUT} not produced`);
    }

    // Validate the JSON parses and contains a `components` array. This
    // catches converter regressions before they ship to puppeteer.
    const ir = JSON.parse(readFileSync(TMP_OUT, 'utf8'));
    if (!Array.isArray(ir.components)) {
      throw new Error(`converter output for ${name} has no .components array`);
    }
    if (ir.components.length === 0) {
      throw new Error(`converter output for ${name} produced 0 components — likely a CSS-shorthand parse failure (run ./gradlew :converter:run --args="convert ..." manually to see the error)`);
    }

    copyFileSync(TMP_OUT, outPath);
    built++;
    console.log(`  ✓ ${name} (${ir.components.length} component${ir.components.length === 1 ? '' : 's'})`);
  } catch (err) {
    failures.push({ name, error: err.message });
    console.error(`  ✗ ${name}: ${err.message}`);
  }
}

// Always write a manifest so the route handler / puppeteer driver can
// enumerate available fixtures without filesystem access.
const manifest = {
  generated: new Date().toISOString(),
  fixtures: readdirSync(FIXTURES_OUT)
    .filter((f) => f.endsWith('.json') && f !== 'manifest.json')
    .map((f) => f.replace(/\.json$/, '')),
};
writeFileSync(join(FIXTURES_OUT, 'manifest.json'), JSON.stringify(manifest, null, 2));

console.log(`\n${built} built · ${skipped} skipped · ${failures.length} failed · ${manifest.fixtures.length} total available`);

if (failures.length > 0) {
  process.exit(1);
}
