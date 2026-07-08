#!/usr/bin/env node
// Tier 9 — CSS → IR adapter.
//
// Reads tools/visual/real-pages/<slug>.css, parses out class definitions,
// emits tools/visual/real-pages/<slug>.ir.json in the Style-Converter input
// format. Then we can pipe each ir.json into ./gradlew :converter:run --args="convert"
// to validate the converter handles real CSS.
//
// Round-43 fix (per Tier 9 investigator): the previous adapter dropped every
// rule whose selector wasn't `^\.([\w-]+)$`. That silently discarded every
// `:root { --custom-property: value }` block, which was the dominant source
// of "stripe at 30% / MDN at 37%" failures — every var() reference in the
// extracted components became unresolvable, and each platform's default
// fallback diverged from the others. Fix: do a two-phase parse — first
// extract :root/html/body var() definitions into a global dictionary, then
// substitute resolved values into each property. Components no longer carry
// orphaned var() refs to deleted definitions.

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const REAL_DIR = 'tools/visual/real-pages';

/**
 * Strip C-style comments. Done up-front so neither `extractRootVars` nor
 * `extractRules` have to think about commented-out brace pairs.
 *
 * Round 56: exported (along with extractRootVars / resolveVars / isVisible /
 * extractRules) so tools/visual/css-to-ir.test.mjs can unit-test the pure logic
 * without touching the filesystem. The main script body is gated behind
 * an `import.meta.url === ...` check at the bottom so `import { … } from
 * './css-to-ir.mjs'` doesn't trigger a real conversion.
 */
export function stripComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, '');
}

/**
 * Phase 1 — extract custom-property definitions from "global scope" selectors.
 *
 * Real-world CSS puts theme tokens in several places:
 *   :root { --primary: #3b82f6 }            — most common (Stripe HDS, MDN)
 *   html { --foo: ... }                     — Tailwind preflight
 *   body { --foo: ... }                     — some legacy stylesheets
 *   :root, html { --foo: ... }              — defensive double-up
 *
 * We treat all of these as "global vars" and merge into one dictionary. We
 * deliberately do NOT process theme-scoped vars like `.dark { --bg: #000 }`
 * — those require theme-selection logic that's out of scope for the fixture
 * harness. Same for `@media (prefers-color-scheme)` overrides.
 */
export function extractRootVars(css) {
  const vars = {};
  // Match a "global" selector list followed by a brace body. The selector
  // list permits :root / html / body / *, optionally combined with commas.
  //
  // We deliberately do NOT anchor on `^` or `}` — minified real-world CSS
  // (Stripe's served sheet, for example) inlines `:root { ... }` directly
  // after `@media (...) { .x { ... } }` so the previous anchored regex
  // missed it. Instead we use a lookbehind that lets `:root`/`html`/`body`
  // start anywhere not in the middle of an identifier (so `.NotRoot {…}`
  // doesn't match). The `(?<![\w-:])` excludes mid-identifier matches.
  const re = /(?<![\w-:])((?::root|html|body|\*)(?:\s*,\s*(?::root|html|body|\*))*)\s*\{([^{}]*)\}/g;
  let m;
  while ((m = re.exec(css)) !== null) {
    const body = m[2];
    for (const decl of body.split(';')) {
      const idx = decl.indexOf(':');
      if (idx === -1) continue;
      const k = decl.slice(0, idx).trim();
      const v = decl.slice(idx + 1).trim();
      // Only custom properties (--foo), not regular declarations on :root.
      // Last-write-wins matches CSS cascade for same-specificity rules.
      if (k.startsWith('--') && v) {
        vars[k] = v.replace(/\s*!important$/, '');
      }
    }
  }
  return vars;
}

/**
 * Recursively resolve var(--name) and var(--name, fallback) references in a
 * single value string against the var dictionary. Bails at depth 10 to
 * defend against pathological self-referential definitions
 * (`--a: var(--a)`).
 *
 * Behaviour matches CSS spec semantics:
 *   - var(--defined)              → resolved value
 *   - var(--undefined, fallback)  → fallback (resolved recursively)
 *   - var(--undefined)            → original (unresolved) string
 *     (the renderer downstream gets a chance to handle this, and our
 *     surface is honest that no resolution was possible)
 */
export function resolveVars(value, vars, depth = 0) {
  if (depth > 10) return value;
  // Match `var(--name)` or `var(--name, fallback)`. Fallback can itself
  // contain commas (e.g. `var(--c, rgb(0, 0, 0))`), so we use a non-greedy
  // match up to the *innermost* close paren — naive regex; multi-arg
  // calc()-inside-var-fallback won't parse cleanly, but that's far rarer
  // than the basic pattern this targets.
  const re = /var\(\s*(--[\w-]+)\s*(?:,\s*([^)]*))?\s*\)/g;
  return value.replace(re, (full, name, fallback) => {
    if (vars[name] !== undefined) {
      return resolveVars(vars[name], vars, depth + 1);
    }
    if (fallback !== undefined) {
      return resolveVars(fallback.trim(), vars, depth + 1);
    }
    return full;
  });
}

/**
 * Phase 9d (round 45) — visibility classifier.
 *
 * Tier 9 Phase 9b found that ~half of CNN's 96 deep-failure components have
 * no visible-rendering properties at all (just `display: block` + `margin: 0`,
 * etc.). With nothing to actually render, each platform's empty-default
 * (transparent, white, dark canvas) leaks through and the SSIM diverges
 * even though dimensions match within 2 px. These components don't test
 * the renderer — they test which platform's empty-default the test harness
 * happens to be capturing against.
 *
 * Filter them out at IR-extraction time. A component is "visible" if it
 * declares at least one property that produces real pixel output. The set
 * is intentionally liberal — when in doubt, include (the cost of including
 * a marginally-visible fixture is only 3 extra SSIM comparisons; the cost
 * of EXCLUDING a real test is silently lower coverage).
 *
 * Properties that produce visible output (non-exhaustive but covers the
 * common cases in our corpus):
 */
export const VISIBLE_PROPS = new Set([
  // Backgrounds + colors
  'background', 'background-color', 'background-image',
  'color',
  // Borders that draw something (we accept all border-* declarations
  // even though `border-width: 0` is technically invisible — keeps the
  // logic simple, the false-positive rate low)
  'border', 'border-color', 'border-image', 'border-style', 'border-width',
  'border-top', 'border-right', 'border-bottom', 'border-left',
  'border-top-color', 'border-top-style', 'border-top-width',
  'border-right-color', 'border-right-style', 'border-right-width',
  'border-bottom-color', 'border-bottom-style', 'border-bottom-width',
  'border-left-color', 'border-left-style', 'border-left-width',
  'border-radius',
  // Outlines (drawn outside the box)
  'outline', 'outline-color', 'outline-style', 'outline-width',
  // Shadows
  'box-shadow', 'text-shadow',
  // Effects (modifiers — only visible if there's underlying content)
  // Round 51 refinement: removed `opacity` from this set. CNN's IR has
  // `header__navigation-separator` declaring `opacity: 1; border: none;
  // height: 2px; transition: ...` — opacity:1 is the default no-op value
  // (every element has opacity:1 implicitly). Treating it as a visibility
  // marker passed an "invisible" component through the filter, where iOS
  // + web rendered placeholder text (the component name) and Android
  // rendered nothing → SSIM 0.61 across pairs. opacity is a modifier, not
  // a visibility marker — keep mask/clip-path/filter for their visible
  // SVG-style carve-out semantics. (transform/rotate/scale/translate
  // similarly only modify already-visible content; kept for now because
  // 3D/2D transforms can reveal previously-hidden faces, edge case.)
  'filter', 'backdrop-filter', 'mix-blend-mode', 'isolation',
  'mask', 'mask-image', 'clip-path',
  // Transforms (can reveal/move content)
  'transform', 'rotate', 'scale', 'translate',
  // Text content (some components carry their own text via a `text` prop)
  'text', 'content',
  // SVG-specific paints
  'fill', 'stroke',
]);

export function isVisible(props) {
  for (const k of Object.keys(props)) {
    if (!VISIBLE_PROPS.has(k)) continue;
    // opacity:0 explicitly hides — count as invisible.
    if (k === 'opacity' && parseFloat(props[k]) === 0) continue;
    // background:none / border:none / outline:none — count as invisible
    // (these are explicit hides). Any other value counts as visible.
    if (typeof props[k] === 'string' && props[k].trim() === 'none') continue;
    return true;
  }
  return false;
}

/**
 * Phase 2 — extract single-class rules. Same as before, but each property
 * value is var-resolved against the root-vars dictionary before being
 * written into the IR.
 */
export function extractRules(css, rootVars) {
  const rules = [];
  // Naive selector { body } match. Single-class selectors only — multi-class,
  // descendant, and pseudo-class selectors are silently skipped (real
  // selector parsing is out of scope; this is "best-effort fixture harvest"
  // not a CSS engine).
  const ruleRe = /([^{}@]+?)\s*\{\s*([^{}]+?)\s*\}/g;
  let m;
  while ((m = ruleRe.exec(css)) !== null) {
    const sel = m[1].trim();
    const body = m[2].trim();
    const classMatch = sel.match(/^\.([\w-]+)$/);
    if (!classMatch) continue;
    const props = {};
    for (const decl of body.split(';')) {
      const idx = decl.indexOf(':');
      if (idx === -1) continue;
      const k = decl.slice(0, idx).trim();
      const v = decl.slice(idx + 1).trim();
      if (!k || !v) continue;
      // Skip --custom-property declarations on .class scopes — they're
      // theme-scoped and we don't model theme selection.
      if (k.startsWith('--')) continue;
      // Strip !important and resolve var()s before storing.
      const cleaned = v.replace(/\s*!important$/, '');
      props[k] = resolveVars(cleaned, rootVars);
    }
    if (Object.keys(props).length === 0) continue;
    rules.push({ name: classMatch[1], props });
  }
  return rules;
}

// ── Main script body ────────────────────────────────────────────────────────
//
// Round 56: gated behind `import.meta.url === ...` so importing this module
// (e.g. from tools/visual/css-to-ir.test.mjs) doesn't trigger a real conversion
// or a writeFileSync to the working tree. Only runs when invoked directly:
//   node tools/visual/css-to-ir.mjs
//
// The dynamic Boolean is needed because `import.meta.url` is a file:// URL
// and `process.argv[1]` is a plain path. fileURLToPath converts so they
// compare reliably regardless of cwd.
const isMainScript = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMainScript) {
  const files = readdirSync(REAL_DIR).filter((f) => f.endsWith('.css'));
  const summary = {};
  for (const file of files) {
  if (file === '_summary.json') continue;
  const slug = file.replace('.css', '');
  const css = stripComments(readFileSync(join(REAL_DIR, file), 'utf8'));

  const rootVars = extractRootVars(css);
  const rules = extractRules(css, rootVars);

  // Count how many post-resolution values still contain var() — these are
  // the var refs we couldn't resolve (theme-scoped, undefined, or in some
  // other dropped block). Surface this in the summary so the failure mode
  // is visible instead of silent.
  let unresolvedVarRefs = 0;
  for (const r of rules) {
    for (const v of Object.values(r.props)) {
      const matches = v.match(/var\(/g);
      if (matches) unresolvedVarRefs += matches.length;
    }
  }

  // Build IR JSON — components keyed by class name. Cap at 50 components
  // to keep test runtime sane (a single test-all.sh per-fixture run scales
  // ~linearly with component count above ~30).
  //
  // Phase 9d: filter out invisible components BEFORE the cap. Otherwise
  // the cap could be reached entirely by no-render fixtures (e.g.
  // CNN where every other rule is just `display: block`), starving the
  // sample of components that actually exercise the renderers.
  const visible = rules.filter((r) => isVisible(r.props));
  const skippedInvisible = rules.length - visible.length;
  const components = {};
  for (const r of visible.slice(0, 50)) {
    components[r.name] = { properties: r.props };
  }
  const out = { components };
  writeFileSync(join(REAL_DIR, `${slug}.ir.json`), JSON.stringify(out, null, 2));
  summary[slug] = {
    rules_extracted: rules.length,
    ir_components: Object.keys(components).length,
    skipped_invisible: skippedInvisible,
    root_vars_extracted: Object.keys(rootVars).length,
    unresolved_var_refs: unresolvedVarRefs,
  };
}

  writeFileSync(join(REAL_DIR, '_ir_summary.json'), JSON.stringify(summary, null, 2));
  console.log('CSS → IR conversion summary:');
  for (const [slug, info] of Object.entries(summary)) {
    console.log(
      `  ${slug}: ${info.rules_extracted} rules → ${info.ir_components} IR ` +
      `(skipped ${info.skipped_invisible} invisible) · ` +
      `${info.root_vars_extracted} root vars · ${info.unresolved_var_refs} unresolved var() refs remaining`
    );
  }
}
