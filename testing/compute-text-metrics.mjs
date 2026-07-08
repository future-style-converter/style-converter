#!/usr/bin/env node
//
// compute-text-metrics.mjs — B-EXT spec Section 7 step 11.
//
// Walks testing/probes/ for hi-res probe captures (per-platform), runs
// the B8/B9/B10 metric helpers across every fixture, aggregates the
// per-platform results into the spec-defined manifest shape, writes
// testing/text-metrics.json, and inlines the result into the existing
// testing/report/manifest.json under the new top-level
// `perPlatformProbes` key.
//
// Inputs (file naming): testing/probes/<platform>__<componentName>.png
//   platform ∈ { iOS, Android, web }
//   componentName ∈ { B8_*, B9_*, B10_* } per spec Section 2.
//
// Outputs:
//   testing/text-metrics.json — standalone JSON, full per-fixture detail
//   testing/report/manifest.json (updated in place) — gains
//     `perPlatformProbes` field; manifestVersion bumped 2 → 3 (Section 6).
//
// Backward compatibility (Section 8 q8): `perPlatformProbes` is null when
// no probes ran. v2 readers ignore unknown top-level keys; v3 readers
// detect probe data in one field check. Existing pairs.* shape is unchanged.

import { readFileSync, writeFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  computeBaselineY,
  classifyAaStrategy,
  computeGlyphSpacing,
  readPng,
} from './compare-screenshots-text-metrics.mjs';
import { TEXT_PROBE_CLASSIFIER_VERSION } from './classify-text-probe.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Args ──────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const probesDir = resolve(getArg('--probes') ?? resolve(__dirname, 'probes'));
const manifestPath = resolve(getArg('--manifest') ?? resolve(__dirname, 'report/manifest.json'));
const outPath = resolve(getArg('--out') ?? resolve(__dirname, 'text-metrics.json'));

function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

// ── Probe discovery ───────────────────────────────────────────────────────
// File-naming convention: <platform>__<componentName>.png. The double-underscore
// separator matches the iOS__/Android__/web__ pattern compare-screenshots
// already uses for screenshot/baseline files. Anything that doesn't match
// is silently skipped (might be a leftover from a different driver).
function discoverProbes(dir) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const fname of readdirSync(dir)) {
    if (!fname.endsWith('.png')) continue;
    const m = fname.match(/^(iOS|Android|web)__(.+)\.png$/);
    if (!m) continue;
    const [, platform, componentName] = m;
    const p = join(dir, fname);
    if (!statSync(p).isFile()) continue;
    out.push({ platform, componentName, path: p });
  }
  return out;
}

// ── Per-component classification ──────────────────────────────────────────
// Family detection from componentName: B8_*, B9_*, B10_*. Anything else
// is reported as a warning but doesn't abort the run.
function familyOf(componentName) {
  if (componentName.startsWith('B10_')) return 'b10'; // longer prefix first
  if (componentName.startsWith('B8_'))  return 'b8';
  if (componentName.startsWith('B9_'))  return 'b9';
  return null;
}

// font-size extractor: B-EXT B8/B10 fixtures embed the size in the
// component-name suffix (e.g. "B8_Serif_AVATAR_24"). Returns the number
// or null when the suffix isn't a font-size.
function fontSizePxFromName(name) {
  const m = name.match(/_(\d+)$/);
  if (!m) return null;
  const n = Number(m[1]);
  // Reasonable font-size band; reject random trailing numbers like
  // "_2024" (release year suffixes) that aren't font sizes.
  return n >= 8 && n <= 96 ? n : null;
}

// ── Aggregation ───────────────────────────────────────────────────────────
// Builds the spec Section 3 shape:
//   B8Result  = { perPlatform: Platforms<number|null>, maxBaselineDeltaPx, divergentFixtureCount }
//   B9Result  = { perPlatform: Platforms<string>, agreement }
//   B10Result = { perPlatform: Platforms<{mean,stddev,count,meanEm}|null>, maxMeanDeltaPx, maxStddevDeltaPx }
function emptyB8() { return { perPlatform: { iOS: {}, Android: {}, web: {} }, maxBaselineDeltaPx: 0, divergentFixtureCount: 0 }; }
function emptyB9() { return { perPlatform: { iOS: {}, Android: {}, web: {} }, agreement: 'agree' }; }
function emptyB10() { return { perPlatform: { iOS: {}, Android: {}, web: {} }, maxMeanDeltaPx: 0, maxStddevDeltaPx: 0 }; }

// Per-pair worst-case delta across iOS/Android/web for a numeric field.
// Mirrors the spec Section 8 q2 recommendation: per-pair max, NOT mean,
// so a 2-platforms-agree-1-differs case still surfaces the differ.
function maxPairwiseDelta(platformValues) {
  const pairs = [
    ['iOS', 'Android'], ['iOS', 'web'], ['Android', 'web'],
  ];
  let worst = 0;
  for (const [p, q] of pairs) {
    const a = platformValues[p];
    const b = platformValues[q];
    if (a == null || b == null) continue;
    const d = Math.abs(a - b);
    if (d > worst) worst = d;
  }
  return worst;
}

// ── Main pipeline ─────────────────────────────────────────────────────────
function main() {
  const probes = discoverProbes(probesDir);
  if (probes.length === 0) {
    // No probe data — write the standalone JSON with null fields so a
    // downstream consumer can distinguish "ran but empty" from "didn't run".
    const stub = {
      classifierVersion: TEXT_PROBE_CLASSIFIER_VERSION,
      generatedAt: new Date().toISOString(),
      probesFound: 0,
      b8_subpixelBaseline: null,
      b9_aaStrategy: null,
      b10_glyphSpacing: null,
    };
    writeFileSync(outPath, JSON.stringify(stub, null, 2));
    inlineIntoManifest(stub);
    console.log(`[compute-text-metrics] no probes in ${probesDir} — wrote stub`);
    return;
  }

  const b8 = emptyB8();
  const b9 = emptyB9();
  const b10 = emptyB10();

  for (const { platform, componentName, path } of probes) {
    const fam = familyOf(componentName);
    if (!fam) {
      console.warn(`[compute-text-metrics] skipping unknown family: ${componentName}`);
      continue;
    }
    let png;
    try {
      png = readPng(path);
    } catch (e) {
      console.warn(`[compute-text-metrics] decode failed: ${path} — ${e.message}`);
      continue;
    }
    if (fam === 'b8') {
      b8.perPlatform[platform][componentName] = computeBaselineY(png);
    } else if (fam === 'b9') {
      b9.perPlatform[platform][componentName] = classifyAaStrategy(png);
    } else if (fam === 'b10') {
      const fontPx = fontSizePxFromName(componentName);
      b10.perPlatform[platform][componentName] = computeGlyphSpacing(png, fontPx);
    }
  }

  // Aggregate: per-fixture worst-pair-delta for B8 + B10. For B9 the
  // agreement is categorical → 'agree' if every platform / fixture matches,
  // 'partial' if some agree, 'differ' if none do.
  const b8FixtureNames = unionFixtureNames(b8.perPlatform);
  for (const fname of b8FixtureNames) {
    const vals = {
      iOS: b8.perPlatform.iOS[fname] ?? null,
      Android: b8.perPlatform.Android[fname] ?? null,
      web: b8.perPlatform.web[fname] ?? null,
    };
    const d = maxPairwiseDelta(vals);
    if (d > b8.maxBaselineDeltaPx) b8.maxBaselineDeltaPx = d;
    if (d > 0.5) b8.divergentFixtureCount += 1; // spec Section 4 / B8: >0.5 = drift
  }

  // B9 agreement aggregation: collect every (platform, fixture) → label,
  // then call it 'agree' when all platforms agreed on every fixture they
  // both observed; 'partial' if some did; 'differ' if none.
  const b9FixtureNames = unionFixtureNames(b9.perPlatform);
  let allAgreed = true, anyAgreed = false;
  for (const fname of b9FixtureNames) {
    const labels = ['iOS', 'Android', 'web']
      .map(p => b9.perPlatform[p][fname])
      .filter(x => x != null && x !== 'unknown');
    if (labels.length < 2) continue; // need ≥2 platforms to have an agreement signal
    const allMatch = labels.every(l => l === labels[0]);
    if (allMatch) anyAgreed = true; else allAgreed = false;
  }
  b9.agreement = allAgreed ? 'agree' : (anyAgreed ? 'partial' : 'differ');

  // B10 aggregation: per-fixture worst-pair delta of mean and stddev.
  const b10FixtureNames = unionFixtureNames(b10.perPlatform);
  for (const fname of b10FixtureNames) {
    const meanVals = {
      iOS: b10.perPlatform.iOS[fname]?.mean ?? null,
      Android: b10.perPlatform.Android[fname]?.mean ?? null,
      web: b10.perPlatform.web[fname]?.mean ?? null,
    };
    const stddevVals = {
      iOS: b10.perPlatform.iOS[fname]?.stddev ?? null,
      Android: b10.perPlatform.Android[fname]?.stddev ?? null,
      web: b10.perPlatform.web[fname]?.stddev ?? null,
    };
    const dM = maxPairwiseDelta(meanVals);
    const dS = maxPairwiseDelta(stddevVals);
    if (dM > b10.maxMeanDeltaPx) b10.maxMeanDeltaPx = dM;
    if (dS > b10.maxStddevDeltaPx) b10.maxStddevDeltaPx = dS;
  }

  const result = {
    classifierVersion: TEXT_PROBE_CLASSIFIER_VERSION,
    generatedAt: new Date().toISOString(),
    probesFound: probes.length,
    b8_subpixelBaseline: b8,
    b9_aaStrategy: b9,
    b10_glyphSpacing: b10,
  };

  writeFileSync(outPath, JSON.stringify(result, null, 2));
  inlineIntoManifest(result);
  console.log(`[compute-text-metrics] processed ${probes.length} probes → ${outPath}`);
  console.log(`  B8 maxBaselineDelta=${b8.maxBaselineDeltaPx.toFixed(2)} px`);
  console.log(`  B9 agreement=${b9.agreement}`);
  console.log(`  B10 maxMean=${b10.maxMeanDeltaPx.toFixed(2)} maxStddev=${b10.maxStddevDeltaPx.toFixed(2)} px`);
}

function unionFixtureNames(perPlatform) {
  const set = new Set();
  for (const p of ['iOS', 'Android', 'web']) {
    for (const k of Object.keys(perPlatform[p] ?? {})) set.add(k);
  }
  return [...set];
}

// Inline into the existing manifest.json. The bump from v2 → v3 is
// graceful: v2 readers ignore unknown top-level keys, so the only
// observable change is the version stamp + the new field.
function inlineIntoManifest(probeBlock) {
  if (!existsSync(manifestPath)) {
    // No manifest yet (compare-screenshots.mjs hasn't run). Spec Section
    // 7 step 11 calls for inlining; without a target file we just skip.
    // The standalone text-metrics.json still exists.
    console.warn(`[compute-text-metrics] manifest not found, skipping inline: ${manifestPath}`);
    return;
  }
  let manifest;
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch (e) {
    console.warn(`[compute-text-metrics] manifest parse failed (${e.message}); leaving alone`);
    return;
  }
  manifest.manifestVersion = 3;
  manifest.perPlatformProbes = {
    b8_subpixelBaseline: probeBlock.b8_subpixelBaseline ?? null,
    b9_aaStrategy: probeBlock.b9_aaStrategy ?? null,
    b10_glyphSpacing: probeBlock.b10_glyphSpacing ?? null,
  };
  // Three new threshold entries (spec Section 6) — all info-only; no CI
  // gating. Keeps the existing thresholds block backwards-compatible.
  manifest.thresholds = {
    ...(manifest.thresholds ?? {}),
    b8BaselineDeltaPx: 1.0,
    b10MeanDeltaPx: 1.5,
    b10StddevDeltaPx: 1.5,
  };
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  // Re-render the HTML report so the typography section reflects the
  // newly-inlined probe data. Done synchronously here (not via a
  // post-hook) so a single `node compute-text-metrics.mjs` invocation
  // leaves both manifest.json AND index.html consistent — otherwise the
  // HTML would silently lag the JSON until the next compare-screenshots
  // run. Best-effort: a re-render failure logs but doesn't throw because
  // the JSON is the canonical record.
  rerenderReport(manifestPath, manifest).catch(e =>
    console.warn(`[compute-text-metrics] HTML re-render skipped: ${e.message}`));
}

async function rerenderReport(manifestPath, manifest) {
  // Resolve the HTML file path next to the manifest (testing/report/).
  // The renderHTML signature requires the same `rows` + `opts` shape
  // compare-screenshots.mjs uses; we reconstruct opts from manifest fields.
  const reportDir = dirname(manifestPath);
  const htmlPath = join(reportDir, 'index.html');
  const { renderHTML } = await import('./compare-screenshots-html.mjs');
  // The manifest's `rows` array is the source of truth for the table;
  // fall back to empty when the manifest's row shape ever changes shape.
  const rows = Array.isArray(manifest.rows) ? manifest.rows : [];
  const html = renderHTML(rows, {
    useBaseline: false,
    ssimThreshold: manifest.thresholds?.ssim ?? 0.95,
    pixelThreshold: manifest.thresholds?.pixel ?? 2,
    regressionCount: 0,
    inputLabel: manifest.inputLabel ?? '',
    perPlatformProbes: manifest.perPlatformProbes ?? null,
  });
  writeFileSync(htmlPath, html);
}

main();
