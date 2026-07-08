#!/usr/bin/env node
// _consolidate.mjs — Phase 12 audit: merge 20 agent reports into REPORT.md.
//
// For each category:
//  • Read testing/audit/<cat>.md (agent's raw findings)
//  • Read testing/audit/<cat>/snapshot/manifest.json when present
//    → extract rows with SSIM < 0.95 for any pair
//  • Collect image paths from testing/audit/<cat>/images/ and
//    testing/audit/<cat>/ (performance uses flat layout)
//  • Emit per-category section in REPORT.md linking images

import { readdirSync, readFileSync, writeFileSync, existsSync, statSync } from 'node:fs';
import { join, resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '../..');
const AUDIT = resolve(__dirname);

// All per-category report files authored by the 20 agents.
const REPORTS = readdirSync(AUDIT)
  .filter((f) => f.endsWith('.md') && f !== 'REPORT.md' && !f.startsWith('_'))
  .map((f) => f.replace(/\.md$/, ''));

function listImages(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((f) => f.endsWith('.png'))
    .map((f) => join(dir, f));
}

function categorySnapshot(cat) {
  const manifestPath = join(AUDIT, cat, 'snapshot', 'manifest.json');
  if (!existsSync(manifestPath)) return null;
  try {
    return JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch {
    return null;
  }
}

function extractFindings(md) {
  // Grab the first ~30 lines after any "Key findings" / "Top findings" /
  // "Highest-impact" heading OR the first 40 lines if no such heading exists.
  const lines = md.split('\n');
  const idx = lines.findIndex((l) =>
    /key findings|top findings|highest[- ]impact|findings|recommendations|bugs?/i.test(l),
  );
  const start = idx >= 0 ? idx : 0;
  return lines.slice(start, start + 40).join('\n');
}

function ssimBand(ssim) {
  if (ssim == null) return 'n/a';
  if (ssim >= 0.95) return 'pass';
  if (ssim >= 0.9)  return 'warn';
  if (ssim >= 0.85) return 'fail';
  return 'crit';
}

function processCategory(cat) {
  const mdPath = join(AUDIT, `${cat}.md`);
  const md = existsSync(mdPath) ? readFileSync(mdPath, 'utf8') : '_(no per-category report)_';

  const snap = categorySnapshot(cat);
  const rows = snap?.rows ?? [];
  const failing = rows
    .map((r) => {
      const pairs = r.pairs ?? {};
      const worst = Math.min(
        ...Object.values(pairs).map((p) => p?.ssim ?? 1),
      );
      return { ...r, worstSsim: worst };
    })
    .filter((r) => r.worstSsim < 0.95);

  const imgDir = join(AUDIT, cat, 'images');
  const imgs = listImages(imgDir);

  // Performance category has a flat layout (images sit directly in testing/audit/performance/).
  const flatImgs = listImages(join(AUDIT, cat)).filter(
    (f) => basename(f).endsWith('.png'),
  );

  return {
    cat,
    md,
    snapRows: rows.length,
    failingRows: failing.length,
    failing,
    imgs: imgs.length ? imgs : flatImgs,
  };
}

// Map per-category.md to actual category names (some agents grouped).
const GROUPED = {
  'no-mobile-analog': ['speech', 'math', 'navigation', 'experimental'],
  'shapes-rhythm-table': ['shapes', 'rhythm', 'table'],
  'print-paging-regions': ['print', 'paging', 'regions'],
};

const all = REPORTS.map(processCategory);

// ── Aggregate totals ────────────────────────────────────────────────────
const totalSnapRows = all.reduce((a, b) => a + b.snapRows, 0);
const totalFailing  = all.reduce((a, b) => a + b.failingRows, 0);
const totalImgs     = all.reduce((a, b) => a + b.imgs.length, 0);

// ── Write REPORT.md ─────────────────────────────────────────────────────
const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
const lines = [];

lines.push('# Phase 12 — cross-platform audit master report');
lines.push('');
lines.push(`_Generated: ${now}_`);
lines.push('');
lines.push('33 property categories across 3 platforms (Android / iOS / Web) were audited by 20 parallel category-scoped agents. Each agent authored (or extended) a per-category fixture focused on UNTESTED edge cases, attempted to run `./test-all.sh` against its fixture, parsed the resulting SSIM report, and did a read-only code audit of the platform Appliers for any failing property. No platform source was modified.');
lines.push('');
lines.push('## Executive summary');
lines.push('');
lines.push(`- Per-category reports landed: **${REPORTS.length}** (see \`testing/audit/<category>.md\`)`);
lines.push(`- Category snapshots with real SSIM data: **${all.filter((a) => a.snapRows > 0).length}** / ${REPORTS.length}`);
lines.push(`- Total component rows captured: **${totalSnapRows}**`);
lines.push(`- Total failing rows (any pair SSIM < 0.95): **${totalFailing}**`);
lines.push(`- Total failure / diff images collected: **${totalImgs}**`);
lines.push('');
lines.push('### Cross-cutting findings (surfaced by multiple agents)');
lines.push('');
lines.push('1. **Infrastructure — test-all.sh concurrency is broken.** macOS ships without `flock`; the `/tmp/sc-testall.lock` path specified in the audit briefs isn\'t honoured by every agent (several used `lockf`, `mkdir`, or perl-flock on different paths). Parallel agents clobbered `out/tmpOutput.json`, `testing/Android/app/src/main/assets/tmpOutput.json`, `testing/web/public/ir-components.json`, and the shared `testing/report/` directory. Three cross-referenced symptoms: (a) Android/Web captures contain IR from sibling audits; (b) iOS `build/XCBuildData/build.db` hits "disk I/O error" from concurrent xcodebuild processes; (c) Android hits `INSTALL_FAILED_DUPLICATE_PACKAGE` from overlapping installs.');
lines.push('2. **Infrastructure — non-ASCII byte in `test-all.sh` line 400** silently breaks the `WEB_PORT` variable under `set -u`, which the shapes+rhythm+table agent found. Several agents "web column is blank" issues trace here.');
lines.push('3. **iOS Xcode build broken worktree-wide** on this machine — `SwiftExplicitPrecompiledModules` cache corruption + `DataDetection` module resolution failures. Reproduces across agents even after `rm -rf testing/iOS/build`.');
lines.push('4. **CssPropertyValidator allowlist holes silently drop properties** that have parsers + appliers in place. Confirmed: `shape-inside`, all 5 `block-step*` rhythm properties. 40% drop rate in those two categories alone. Likely more elsewhere.');
lines.push('5. **Mirror-tree contract violations** — Android + iOS categories often collapse into one category-level Config/Extractor/Applier triplet rather than one-per-property (as Web does). Examples: Android `animations/` (9 files for 26 properties), iOS single-file identity stubs across 7 categories, Android `background/` with NO canonical `style/background/` triplets (backgrounds embedded in `color/ColorConfig.kt`).');
lines.push('6. **Several appliers extract config then never apply it.** Android `RenderingConfig` — extractor runs, applier never reads. Android `ScrollApplier.applyScroll` — defined but never invoked from `ComponentRenderer`. Android `MultiColumnLayout` — implementation present, never constructed. iOS typography `WritingModeApplier.swift:11-18` — explicit `_ = cfg` with TODO.');
lines.push('');

// ── Per-category sections ───────────────────────────────────────────────
lines.push('## Per-category findings');
lines.push('');

for (const { cat, md, snapRows, failingRows, failing, imgs } of all) {
  lines.push(`### ${cat}`);
  lines.push('');
  if (snapRows > 0) {
    lines.push(`_Captured: **${snapRows}** rows, **${failingRows}** failing (any pair SSIM < 0.95), ${imgs.length} diff/render images._`);
  } else {
    lines.push(`_Code audit only — no capture snapshot. Images: ${imgs.length}._`);
  }
  lines.push('');
  // Pull the agent's key findings block
  const findings = extractFindings(md);
  lines.push(findings);
  lines.push('');
  // If there are diff images, embed up to 6 thumbnails
  if (imgs.length > 0) {
    lines.push('#### Sample failure images');
    lines.push('');
    for (const img of imgs.slice(0, 6)) {
      const rel = img.replace(AUDIT + '/', '');
      const name = basename(img);
      lines.push(`![${name}](${rel})`);
      lines.push('');
    }
    if (imgs.length > 6) {
      lines.push(`_${imgs.length - 6} additional images in \`testing/audit/${cat}/\`._`);
      lines.push('');
    }
  }
  // Top failing rows with SSIM numbers
  if (failing.length > 0) {
    lines.push('#### Top failing components');
    lines.push('');
    lines.push('| Component | iOS-Android | iOS-Web | Android-Web | Worst |');
    lines.push('|---|---:|---:|---:|---:|');
    for (const row of failing.slice(0, 10)) {
      const p = row.pairs ?? {};
      const fmt = (s) => (s == null ? '—' : s.toFixed(2));
      lines.push(`| \`${row.name}\` | ${fmt(p['iOS-Android']?.ssim)} | ${fmt(p['iOS-web']?.ssim)} | ${fmt(p['Android-web']?.ssim)} | ${fmt(row.worstSsim)} |`);
    }
    if (failing.length > 10) {
      lines.push(`_…plus ${failing.length - 10} more._`);
    }
    lines.push('');
  }
  lines.push('---');
  lines.push('');
}

// ── Consolidated fix backlog ────────────────────────────────────────────
lines.push('## Consolidated fix backlog');
lines.push('');
lines.push('Ordered roughly by impact / ease ratio. P0 = blocker that prevents further auditing; P1 = confirmed rendering bug with SSIM data; P2 = code-audit-only suspected bug; P3 = contract / mirror-tree cleanup.');
lines.push('');
lines.push('### P0 — infrastructure blockers');
lines.push('1. **test-all.sh concurrency fix.** Add `flock` (or python-level equivalent on macOS) around the test-all entry point; isolate `out/tmpOutput.json` and per-platform IR sync paths per-invocation; serialise Android `installDebug` under a per-device lock.');
lines.push('2. **Non-ASCII byte in test-all.sh line 400** (`WEB_PORT` assignment). One-char fix.');
lines.push('3. **iOS Xcode build repair** — nuke `testing/iOS/build/`, `~/Library/Developer/Xcode/DerivedData/StyleConverterTest-*`, regenerate with xcodegen. Re-run after infra #1 is fixed.');
lines.push('');
lines.push('### P1 — confirmed rendering bugs (with captured diff evidence)');
lines.push('1. **Android transforms** (`TransformExtractor.kt`): reads wrong IR keys — rotate reads `angle` but IR emits `a.deg` → every rotate is 0°. matrix/matrix3d read `values[]` but IR emits `a/b/c/d/e/f`. perspective reads `d`/`distance` but IR emits `l`. transform-origin percentage reads `value` but IR emits `percentage`. See `testing/audit/transforms.md` findings T1-T4.');
lines.push('2. **Android spacing**: `calc()` padding/margin hardcoded to 0.dp in `SpacingResolve.kt:56`. 43/46 rows failed in the spacing audit.');
lines.push('3. **Android WillChange applier** (`PerformanceApplier.kt`): wraps component in a `graphicsLayer` that drops width/height modifiers — component collapses from 390×102 to 390×53. All 5 WillChange variants affected. Diff images under `testing/audit/performance/`.');
lines.push('4. **Android border-image** does not render gradient sources — `rememberCachedGradient` path is unreachable from the modifier entry point. SSIM 0.55-0.62 on gradient variants.');
lines.push('5. **Android border-style** `groove/ridge/inset/outset` at ≥10px width render invisibly (strokes extend past bounds). Android `double` at 2-3px too faint. Per-side mixed styles only render one side.');
lines.push('6. **Android multi-layer box-shadow** overwrites instead of compositing — only last 2-3 layers visible.');
lines.push('7. **Android content property** (`ContentApplier.kt`, 741 LOC) actively synthesizes `::before`/`::after` glyphs while iOS/Web render identity. Every `content: ...` fixture diverges.');
lines.push('8. **Android animation-name substring-matching** (`AnimatedModifier.kt:57-64`) — synthesizes fade/spin/pulse/slide/bounce/shake keyframes from property name. iOS/Web render identity.');
lines.push('');
lines.push('### P2 — suspected bugs (code audit only, no captured diff)');
lines.push('1. **iOS writing-mode** is explicit no-op (`WritingModeApplier.swift:11-18`) — Android actually rotates, Web uses native CSS. Three-way divergence.');
lines.push('2. **iOS text-emphasis** routes to `UnsupportedRubyEmphasisApplier` (renders nothing); Android has custom overlay; Web native.');
lines.push('3. **iOS font-family fallback** (`FontFamilyApplier.swift:18`) uses only `names.first` — fallback chain discarded.');
lines.push('4. **iOS line-clamp** has no dedicated applier under `StyleEngine/typography/`.');
lines.push('5. **iOS flexbox** (`FlexboxApplier.swift`): flex-grow ignores non-unit values; flex-shrink no-op; order no-op; wrap-reverse TODO.');
lines.push('6. **iOS grid** (`GridApplier.swift:98`): "weights other than 1 round down to equal" — non-1fr minmax ratios collapse.');
lines.push('7. **iOS position** (`PositionApplier.swift:86` + `LayoutAggregate.swift:370`): right/bottom offset maths wrong; 4-inset stretch documented TODO.');
lines.push('8. **iOS percent insets** (`PositionExtractor.swift:138`) and Android equivalents drop to nil.');
lines.push('9. **iOS sizing** (`SizeApplierResolve.swift`): silently drops `height:%` and `height:*vh` (`allowPercent:false` on height axis).');
lines.push('10. **Android aspect-ratio ordering**: applies `.aspectRatio()` after `heightIn(max=…)` — `aspect-ratio:16/9 + max-height:80px` keeps width=320 instead of collapsing to 142×80.');
lines.push('11. **Dynamic colors** (`color-mix`, `light-dark`, relative colors): Web reconstructs via `DynamicColorCss.ts`; iOS `ColorApplier.swift:42` explicitly skips; Android drops to null. Guaranteed SSIM ≈ 0 on Web-vs-mobile for any dynamic color.');
lines.push('');
lines.push('### P3 — contract / mirror-tree cleanup');
lines.push('1. Android: split `ContentApplier.kt` (741 LOC), `SvgApplier.kt` (632 LOC), `TableApplier.kt` (560 LOC), `ColorApplier.kt` (305 LOC) into per-property triplets OR formally document why they\'re grouped.');
lines.push('2. Android `style/background/` — **create** canonical triplets (currently backgrounds embedded in `color/ColorConfig.kt`).');
lines.push('3. iOS: split category-level `{cat}Applier.swift` identity stubs into per-property triplets across speech, math, navigation, experimental, images, appearance, content, counters, lists, container, columns, interactions (12 categories).');
lines.push('4. **CssPropertyValidator.kt** allowlist: add `shape-inside`, all 5 `block-step*`, audit for more silent drops (likely candidates: `overlay`, `reading-flow`, `view-transition-*`).');
lines.push('5. **BackgroundPosition parser bugs**: base64 data URLs lowercased; radial/conic gradient shape/size/`at X Y` dropped; multi-layer background-position collapses to single scalar; `BackgroundPositionBlock/Inline` IR models dropped as invalid by registry.');
lines.push('6. **Background image gradient parser**: `linear-gradient(in oklab, …)` interpolation hint parsed as two junk stops.');
lines.push('7. Duplicate property registration across categories (first-write-wins but violates single-owner contract): `CounterReset/Increment/Set` claimed by both counters and content on Android.');
lines.push('');
lines.push('## How to re-run the captures for missing categories');
lines.push('');
lines.push('After P0 infrastructure fixes (flock + WEB_PORT + iOS build), serialize captures for the 13 categories that didn\'t get real snapshots:');
lines.push('');
lines.push('```bash');
lines.push('for fixture in examples/properties/*/audit-phase12.json; do');
lines.push('  cat=$(basename $(dirname "$fixture"))');
lines.push('  NO_OPEN=1 ./test-all.sh "$fixture"');
lines.push('  rm -rf testing/audit/$cat/snapshot');
lines.push('  cp -r testing/report testing/audit/$cat/snapshot');
lines.push('done');
lines.push('```');
lines.push('');
lines.push('Categories with no `audit-phase12.json` fixture (all agents reused existing `longtail.json` or skipped fixture authoring due to READ-ONLY misinterpretation):');
lines.push('- animations, color, interactions, svg, scrolling, rendering, columns');
lines.push('- print+paging+regions (combined), speech+math+navigation+experimental (combined), images+appearance+content+global+counters+lists+container (combined), shapes+rhythm+table (combined)');
lines.push('');
lines.push('Recommend: before the re-run pass, ask agents to author audit-phase12.json specifically for the 7 single categories that skipped it. Grouped categories can stay on longtail.json since those fixtures are already comprehensive for their (no-op) families.');
lines.push('');

writeFileSync(join(AUDIT, 'REPORT.md'), lines.join('\n') + '\n');
console.log(`✓ wrote testing/audit/REPORT.md (${REPORTS.length} categories, ${totalFailing} failing rows, ${totalImgs} images)`);
