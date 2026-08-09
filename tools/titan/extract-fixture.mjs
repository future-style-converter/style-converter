#!/usr/bin/env node
//
// tools/titan/extract-fixture.mjs — Phase 1 WPT extractor.
//
// Implements TITAN_ARCHITECTURE.md Section 4.2: walk a single WPT test HTML
// (or a list of them), parse inline <style> + linked .css, and emit a
// Style-Converter fixture JSON in the existing IR shape:
//
//     { components: { "<id>": { properties: { … } } }, _wpt: { … } }
//
// The output mirrors fixtures/properties/<category>/<name>.json so the
// existing test-all.sh + compare-screenshots pipeline can ingest it
// unchanged.
//
// Usage:
//   node tools/titan/extract-fixture.mjs <relative-test-path>...
//
//   - Each path is repo-relative under WPT (e.g. "css/css-color/a98rgb-001.html")
//     matching the format in tools/titan/wpt-buckets.json's `buckets.A` array.
//
// Outputs:
//   fixtures/wpt/<spec-section>/<test-stem>.json          (the test)
//   fixtures/wpt/<spec-section>/<test-stem>__ref.json     (the reference)
//
//   <test-stem> is safe-name.mjs's fixtureStem(): the bare basename for
//   top-level tests, and the `<subdir>__…__<basename>` subdir-encoded form
//   for nested tests (wave-21 collision fix — equal basenames in different
//   subdirs used to flatten to one file and silently overwrite each other).
//
// Performance: tens of files per second is enough for Phase-1 smoke (100
// tests). Phase 2 will batch this up into a streaming pass for the full
// 10k-test bucket-A.
//
// Exit codes:
//   0  — wrote one fixture per input, plus the matching __ref.json.
//   1  — input file not found OR ref file referenced by rel="match" missing.
//   2  — IO / parse error.
//
// Hard scope (Section 4.2):
//   - One WPT test → one JSON fixture
//   - Each <body> descendant (top-level + nested up to depth 3) becomes one
//     component "<test-stem>__N". When body-scope CSS rules exist
//     (`body { … }`, `html { … }`, `* { … }`) a synthetic root component
//     "<test-stem>__body" is emitted FIRST carrying those properties.
//     wave-17: when that body-root declares an explicit nonzero ABSOLUTE
//     height, the "__N" components nest under its `children` map instead of
//     stacking below it as siblings (see BODY-HEIGHT SLOTTING at
//     buildComponents for the full decision record).
//   - Inline <style> declarations are flattened into per-component property
//     dicts via a tiny CSS parser (no jsdom dep — stays in node-stdlib).
//     Descendant selectors (`ol li`, `.parent .child`) match against each
//     element's ancestor chain so deeply-scoped rules apply to the right
//     component, not just to top-level body children.
//   - The original WPT relative path is preserved in the top-level _wpt block
//   - Lossy bucket-B markers ride on the per-component _lossy / _lossyReasons
//     fields (Section 4.3); the top-level _wpt.lossy boolean is the
//     dashboard-friendly shortcut
//   - <meta name="fuzzy"> tolerances are parsed and stored under _wpt.fuzzy
//
// Pilot-001 bug-fix bundle (Bugs 1–5):
//   - Bug 1: <p>/<h1..h3>/<noscript>/<script> are no longer pre-filtered;
//     styled `<p class="test">` (the canonical WPT subject) survives to IR.
//   - Bug 2: subtree recursion + descendant-aware selectorMatches catches
//     `ol li`, `div .target` etc. on nested elements.
//   - Bug 3 + 4: a synthetic body-root component captures body/html/*-scoped
//     CSS so containment, body-background, root-font tests have a target.
//   - Bug 5: head-only tags (<title>/<meta>/<link>/<style>/<script>/<base>/
//     <head>) never emit components, even in the no-<body> fallback path.
//
// wave-13 KEYFRAMES-SAMPLER: WPT's time-stable animation tests (negative
// delay + slope-zero easing) are statically sampled — @keyframes are parsed,
// the animated values are interpolated at progress = -delay/duration, and
// the results are BAKED into component properties with a 'sampled-animation'
// lossy note (see the "Static @keyframes sampler" section for the full
// scope boundary).

import { promises as fs } from 'node:fs';
import { resolve, dirname, join, relative, basename, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
// The ONE canonical fixture-stem derivation (wave-21 collision fix): encodes
// a nested test's subdirectory chain into the stem (`flexbox__monolithic-
// overflow-001.tentative`) so two tests with equal basenames in different
// subdirs can never overwrite each other's fixtures/wpt/<section>/ files.
// Top-level tests keep the historical bare basename. See safe-name.mjs.
import { fixtureStem } from './safe-name.mjs';
// wave-25 THE ATTR BAKE (the fourth bake): css-values-5 §7 attr() reads the
// element's OWN attributes, which this extractor already parses — so the
// substitution is statically resolvable HERE and nowhere downstream. Static
// + stdlib-only, so it is a plain top-level import (unlike the puppeteer-
// bearing post-load / bidi bakes, which stay lazily imported in main()).
// The module short-circuits on values without an `attr(` token, so the
// non-attr corpus is byte-identical by construction. See attr-bake.mjs.
import {
  bakeAttr, hasAttrFunction,
  ATTR_BAKED_REASON, ATTR_UNRESOLVED_REASON, ATTR_IACVT_REASON,
} from './attr-bake.mjs';
// wave-27 THE COUNTER-STYLE BAKE (the fifth bake): a list marker is a pure
// function of (resolved list-style-type, <ol start>, item position) and
// css-counter-styles-3 §6 is a closed table, so — like the attr bake and
// unlike the post-load/bidi bakes — it needs no browser and stays a plain
// top-level import. It short-circuits on fixtures with no list at all.
import * as counterBake from './counter-style-bake.mjs';
// wave-36 lane M3 THE GENERATED-CONTENT TEXT BAKE: a pseudo bag's `content`
// declaration reaches the web renderer as an INLINE STYLE on a real <span>,
// and Blink paints nothing for a <string> there (css-content-3 §2.1 element
// replacement is implemented for <image> only) — so every `::before {
// content: "x" }` in the corpus shipped a text-less box. This module
// resolves the <string>/<quote> half of the value into the `_text` field
// PseudoNodeRenderer already reads. Stdlib-only and browser-free like the
// attr and counter-style bakes; see its header for the refusal set and the
// measured population.
import * as generatedContentBake from './generated-content-bake.mjs';
// wave-37 lane W8 THE COUNTER TREE (the seventh bake): the generated-content
// bake's single largest refusal is `counter()` / `counters()` — 3,151 of the
// corpus's 3,695 `content` declarations — because resolving one needs the
// css-lists-3 §4 counter tree, which no per-node hook can own. This module is
// that tree; it rewrites each call into the literal <string> it resolves to,
// leaving the generated-content bake to do what it already does. Stdlib-only
// and browser-free like the attr / counter-style bakes, and it short-circuits
// on any fixture with no `counter` substring at all.
import * as counterTreeBake from './counter-bake.mjs';
// wave-36 THE WIDGET-APPEARANCE BAKE (the sixth bake): the css-ui
// compute-kind-widget family establishes author-origin appearance-disabling
// declarations from a GENERATED script whose declared values are, by
// construction, identical to the UA's — so its only observable effect is
// the widget's switch to fallback rendering, and that switch is statically
// derivable from the script's own text. Stdlib-only like the attr and
// counter-style bakes, and it short-circuits to a no-op ([] rules) on every
// document without the idiom. See widget-appearance-bake.mjs.
import * as widgetAppearanceBake from './widget-appearance-bake.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const OUT_ROOT   = process.env.WPT_FIXTURES_ROOT ?? join(REPO_ROOT, 'fixtures', 'wpt');

// ── Bucket-index lookup (for lossy markers) ──────────────────────────────────
//
// Loading the bucket index up-front avoids re-classifying each input. Lazy
// init so the test suite can run without the index file present.
let _bucketIdx = null;
async function bucketIndex() {
  if (_bucketIdx) return _bucketIdx;
  const p = join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json');
  try {
    const raw = await fs.readFile(p, 'utf8');
    _bucketIdx = JSON.parse(raw);
  } catch {
    // Bucket index optional in unit tests — caller can still extract a fixture,
    // it just won't carry a `lossy` flag.
    _bucketIdx = { buckets: { A: [], B: [], C: [] }, reasons: {} };
  }
  return _bucketIdx;
}

// ── HTML helpers ─────────────────────────────────────────────────────────────
//
// We use string scanning rather than jsdom: a real DOM parser adds ~3 MB of
// install footprint and 100+ ms startup, neither of which we need for the
// well-formed reftest HTML in the WPT corpus. The regexes below are tuned to
// the subset of markup the corpus actually uses.
//
// Each helper is exported for unit testing.

/** Strip C-style and HTML comments so neither <style> nor <body> regexes
 *  trip on commented-out blocks.
 *
 *  Replacement text is deliberately ASYMMETRIC between the two syntaxes:
 *
 *  - HTML comments → '' (empty). A DOM comment node sits BETWEEN text
 *    nodes; the surrounding text nodes render adjacent with nothing in
 *    between (`a<!-- c -->b` paints "ab"), so deleting the comment
 *    reproduces what the browser shows.
 *  - CSS comments → ' ' (single space). Per css-syntax-3 §4.3.2 the
 *    tokenizer treats a comment exactly like whitespace — it TERMINATES
 *    the token before it and separates it from the next. Deleting it
 *    instead GLUES the neighbours into one corrupted token: the WPT
 *    background-color-hsl-001 #p5 value `hsla(120⟨comment⟩75%⟨comment⟩
 *    50%/1.0)` (⟨comment⟩ = a slash-star CSS comment, unspellable inside
 *    this doc block) collapsed to the unparseable `hsla(12075%50%/1.0)`
 *    — 2 corrupt swatches in each of the 4 comment-bearing css-color
 *    tests, failing on every platform. A space preserves the separator
 *    role; CSS never distinguishes one space from many, so no value can
 *    over-separate. */
export function stripComments(html) {
  // HTML comments first (can contain `*/`). Empty replacement — see the
  // DOM text-node-concatenation rationale in the doc comment above.
  let out = html; for (let prev = null; prev !== out; ) { prev = out; out = out.replace(/<!--[\s\S]*?-->/g, ''); }
  // CSS comments inside <style> blocks. Single-SPACE replacement — the
  // css-syntax-3 comment-as-token-separator rule in the doc comment above.
  out = out.replace(/\/\*[\s\S]*?\*\//g, ' ');
  return out;
}

/** Concatenate every <style>…</style> block in document order. Linked
 *  stylesheets via <link rel="stylesheet"> are read separately. */
export function extractInlineStyle(html) {
  const re = /<style\b[^>]*>([\s\S]*?)<\/style>/gi;
  const out = [];
  let m;
  while ((m = re.exec(html)) !== null) out.push(m[1]);
  return out.join('\n');
}

/** Find <link rel="stylesheet" href="..."> tags. Returns hrefs in document
 *  order. Tolerant of attribute order + quoting. */
export function extractLinkedStylesheets(html) {
  const out = [];
  const re = /<link\b[^>]*?rel=["']?stylesheet\b[\s\S]*?>/gi;
  let m;
  while ((m = re.exec(html)) !== null) {
    const tag = m[0];
    const hm = /\bhref=["']([^"']+)["']/i.exec(tag);
    if (hm) out.push(hm[1]);
  }
  // Also catch the rare reverse order: href before rel.
  const re2 = /<link\b[^>]*?href=["']([^"']+)["'][\s\S]*?rel=["']?stylesheet\b[^>]*>/gi;
  while ((m = re2.exec(html)) !== null) {
    if (!out.includes(m[1])) out.push(m[1]);
  }
  return out;
}

// ── @import resolution (wave-38 lane N4) ─────────────────────────────────────
//
// THE HOLE. parseCss skips every at-rule it does not explicitly carve out
// (see its banner: wave-37 lane W8 opened @layer and @scope), and nothing
// upstream ever FETCHED an `@import`ed sheet either. So an imported
// stylesheet's rules simply did not exist for the extractor. MEASURED on the
// wave35 web map, css-cascade/import-conditional-001 and -002: the green
// override lives in `support/test-green.css`, the local sheet's fallback is
// `div { background: red }`, and both tests therefore shipped the FAIL red
// square. Corpus-wide, 73 bucket-A tests carry an `@import` in their inline
// <style>.
//
// THE OVER-APPLICATION GUARD, which is the whole difficulty. Two of the four
// `@import`s in those two tests are DELIBERATE TRAPS:
//
//     @import "support/test-red.css" (max-width: 1px), nonsense;
//     @import "support/test-red.css" supports(foo: bar);
//
// A resolver that inlines every `@import` it finds paints the tests RED —
// strictly worse than not resolving them at all. So the gate here is
// deliberately one-directional: **an `@import` is inlined only when its
// condition can be PROVEN to match.** Unprovable is treated exactly like
// false, and both mean "leave the rule alone", which is the byte-identical
// status quo. Nothing in this section can turn a currently-passing cell into
// a failing one by GUESSING a condition true.
//
// WHAT COUNTS AS PROVEN:
//   * no condition at all                     → true;
//   * a media-query list, evaluated against the reference viewport, where at
//     least one query is provably a match (mediaQueryListMatches);
//   * `supports(<decl>)` where the declaration is a custom property (always
//     supported) or sits in the closed SUPPORTS_PROVABLE table below.
// Anything else — `layer` / `layer()` (cascade-layer semantics this flat
// model does not carry), a `url()` we cannot read off disk, an unparsed
// prelude — declines.
//
// WHY CHROMIUM SEMANTICS AND NOT THE CONVERTER'S. `supports()` asks what the
// RENDERING ENGINE supports, and the thing we are scored against is the
// Chromium browser reference. Answering from the converter's own property
// catalogue would make the fixture disagree with the ref whenever the two
// differ. Since a stdlib-only extractor cannot interrogate Chromium, the
// table is a hand-audited closed list that grows one measured entry at a
// time — never a heuristic.

// The viewport a media query is evaluated against. THIS IS THE REF'S
// viewport, not the 390×600 canvas: capture-browser-ref.mjs renders the WPT
// page at REF_RENDER_WIDTH × REF_RENDER_MIN_HEIGHT (390−2·16 by 600−2·16) and
// only pads back to the canvas in IMAGE space, so `@media` sees 358×568.
// Duplicated rather than imported because capture-browser-ref.mjs imports
// extractRefHref from THIS module — importing back would close a cycle. The
// drift is closed by a unit pin in extract-fixture.test.mjs that imports both
// modules and asserts equality.
export const MQ_VIEWPORT_WIDTH_PX = 358;
export const MQ_VIEWPORT_HEIGHT_PX = 568;

// css-values-4 §5.2 absolute length units, in px. `em`/`rem` in a media
// query resolve against the INITIAL font size (MQ-4 §1.3: the initial value,
// never the root element's used value), which is Chromium's 16px default.
const MQ_ABSOLUTE_UNITS_PX = { px: 1, in: 96, cm: 96 / 2.54, mm: 96 / 25.4, q: 96 / 101.6, pt: 96 / 72, pc: 16 };
const MQ_INITIAL_FONT_PX = 16;

/** A media-query `<length>` in px, or null when the token is not one we can
 *  evaluate (a calc(), a viewport unit, an unknown unit). Exported for tests. */
export function mqLengthPx(token) {
  const m = /^([+-]?(?:\d+\.?\d*|\.\d+))(px|in|cm|mm|q|pt|pc|em|rem)?$/i.exec(String(token).trim());
  if (!m) return null;
  const n = Number.parseFloat(m[1]);
  const unit = (m[2] ?? '').toLowerCase();
  // A unitless value is only a valid <length> when it is zero (css-values-4
  // §5.2); anything else is a syntax error, so refuse rather than assume px.
  if (!unit) return n === 0 ? 0 : null;
  if (unit === 'em' || unit === 'rem') return n * MQ_INITIAL_FONT_PX;
  return n * MQ_ABSOLUTE_UNITS_PX[unit];
}

/** Evaluate ONE parenthesised media feature. Returns true/false when the
 *  feature is one we model against the ref viewport, or null when it is not
 *  (which the caller treats as "unprovable"). Exported for tests. */
export function mqFeature(text) {
  const body = text.trim().replace(/^\(|\)$/g, '').trim();
  const colon = body.indexOf(':');
  const name = (colon === -1 ? body : body.slice(0, colon)).trim().toLowerCase();
  const value = colon === -1 ? null : body.slice(colon + 1).trim();
  // Boolean context — `(width)` is true when the value is non-zero. Only
  // answered for the two dimensions we actually know.
  if (value === null) {
    if (name === 'width') return MQ_VIEWPORT_WIDTH_PX !== 0;
    if (name === 'height') return MQ_VIEWPORT_HEIGHT_PX !== 0;
    return null;
  }
  // `orientation` is decidable from the same two numbers.
  if (name === 'orientation') {
    const want = value.toLowerCase();
    const isPortrait = MQ_VIEWPORT_HEIGHT_PX >= MQ_VIEWPORT_WIDTH_PX;
    if (want === 'portrait') return isPortrait;
    if (want === 'landscape') return !isPortrait;
    return null;                                   // unknown keyword
  }
  // The range features. `min-`/`max-` are inclusive bounds (MQ-4 §2.4.1).
  const dim = /^(?:min-|max-)?width$/.test(name) ? MQ_VIEWPORT_WIDTH_PX
    : /^(?:min-|max-)?height$/.test(name) ? MQ_VIEWPORT_HEIGHT_PX
      : null;
  if (dim === null) return null;                   // some other feature — unprovable
  const px = mqLengthPx(value);
  if (px === null) return null;
  if (name.startsWith('min-')) return dim >= px;
  if (name.startsWith('max-')) return dim <= px;
  return dim === px;                               // plain `(width: 358px)`
}

/** Split a string on a top-level separator regex, ignoring anything inside
 *  parens or quotes. Used for the media-query list's commas and one query's
 *  `and` chain, both of which can legally contain parenthesised text. */
function splitPreludeTopLevel(src, isSeparatorAt) {
  const out = [];
  let depth = 0, quote = null, start = 0, i = 0;
  while (i < src.length) {
    const ch = src[i];
    if (quote) { if (ch === quote && src[i - 1] !== '\\') quote = null; i++; continue; }
    if (ch === '"' || ch === "'") { quote = ch; i++; continue; }
    if (ch === '(') { depth++; i++; continue; }
    if (ch === ')') { depth--; i++; continue; }
    if (depth === 0) {
      const len = isSeparatorAt(src, i);
      if (len) { out.push(src.slice(start, i)); i += len; start = i; continue; }
    }
    i++;
  }
  out.push(src.slice(start));
  return out;
}

/** Evaluate ONE media query (`screen and (min-width: 1px)`, `not print`,
 *  `nonsense`). true / false / null(unprovable). Exported for tests. */
export function mqQuery(query) {
  let q = query.trim().toLowerCase();
  if (!q) return null;
  let negated = false;
  if (/^not\b/.test(q)) { negated = true; q = q.slice(3).trim(); }
  else if (/^only\b/.test(q)) { q = q.slice(4).trim(); }   // `only` is a legacy no-op
  const terms = splitPreludeTopLevel(q, (s, i) => (/^\sand\s/.test(s.slice(i)) ? 5 : 0))
    .map((t) => t.trim()).filter(Boolean);
  if (!terms.length) return null;
  let result = true;
  for (const term of terms) {
    let v;
    if (term.startsWith('(')) {
      v = mqFeature(term);
    } else if (/^[a-z-]+$/.test(term)) {
      // A media TYPE. The ref renders in a screen-media headless browser, so
      // `all` and `screen` match and every other type — `print`, `speech`,
      // and the `nonsense` the corpus uses as a deliberate non-match — does
      // not (MQ-4 §2.1: an unknown media type never matches).
      v = term === 'all' || term === 'screen';
    } else {
      v = null;                                    // range syntax etc — unprovable
    }
    if (v === null) return null;                   // one unknown poisons the query
    if (v === false) result = false;               // keep scanning: a later
    //                                                unknown must still poison
  }
  return negated ? !result : result;
}

/** Does a media-query LIST match? A list matches when ANY query does
 *  (MQ-4 §2.1). Only a PROVEN match returns true. Exported for tests. */
export function mediaQueryListMatches(list) {
  const queries = splitPreludeTopLevel(String(list), (s, i) => (s[i] === ',' ? 1 : 0));
  return queries.some((q) => mqQuery(q) === true);
}

// The closed `supports()` table (see the banner's WHY CHROMIUM SEMANTICS
// note). One entry per property, listing only values whose support in
// Chromium is not in question. `display` is here because
// css-cascade/import-conditional-002 asks `supports(display: block)`; the
// list is the css-display-3 §2 keyword set every engine has shipped for
// years. Growing this table is a deliberate, per-value act — never a regex.
const SUPPORTS_PROVABLE = new Map([
  ['display', new Set([
    'block', 'inline', 'inline-block', 'flow-root', 'none', 'contents',
    'flex', 'inline-flex', 'grid', 'inline-grid', 'list-item',
    'table', 'inline-table', 'table-row', 'table-cell',
  ])],
]);

/** Can we PROVE a `supports()` condition true? Anything else — including a
 *  condition that is merely probably false, like `supports(foo: bar)` — gets
 *  the same answer as "no", because both mean "do not inline". Exported for
 *  tests. */
export function supportsConditionProvable(condition) {
  const c = String(condition).trim();
  // Only a bare `(<decl>)` is answered. `not`/`and`/`or` combinators and
  // `selector()` / `font-tech()` functions decline.
  if (!/^\([^()]*\)$/.test(c)) return false;
  const body = c.slice(1, -1).trim();
  const colon = body.indexOf(':');
  if (colon === -1) return false;
  const prop = body.slice(0, colon).trim().toLowerCase();
  const value = body.slice(colon + 1).trim().toLowerCase();
  // A custom property declaration is supported by definition (css-variables-1
  // §2: any `--*` name with any token stream is valid).
  if (prop.startsWith('--')) return value.length > 0;
  const values = SUPPORTS_PROVABLE.get(prop);
  return values ? values.has(value) : false;
}

/** Find the end of an at-rule STATEMENT: the index just past the first `;`
 *  that is not inside a string or parens. Returns -1 when the statement is
 *  unterminated (a truncated sheet) — the caller then leaves it alone. */
function statementEnd(src, from) {
  let depth = 0, quote = null;
  for (let i = from; i < src.length; i++) {
    const ch = src[i];
    if (quote) { if (ch === quote && src[i - 1] !== '\\') quote = null; continue; }
    if (ch === '"' || ch === "'") { quote = ch; continue; }
    if (ch === '(') depth++;
    else if (ch === ')') depth--;
    else if (ch === '{') return -1;                // a block, not a statement
    else if (ch === ';' && depth === 0) return i + 1;
  }
  return -1;
}

/** Split an `@import` prelude into `{ href, rest }`. `href` is null when the
 *  target is not a plain string / `url()` we can read off disk (a data: URI,
 *  a remote sheet, a var()). Exported for tests. */
export function parseImportPrelude(prelude) {
  const p = prelude.trim();
  let m = /^url\(\s*(?:"([^"]*)"|'([^']*)'|([^)"'\s]*))\s*\)/i.exec(p);
  if (!m) m = /^(?:"([^"]*)"|'([^']*)')/.exec(p);
  if (!m) return { href: null, rest: p };
  const href = m[1] ?? m[2] ?? m[3] ?? '';
  return { href, rest: p.slice(m[0].length).trim() };
}

/** Is the condition tail of an `@import` prelude PROVEN to match? Handles
 *  `supports(<cond>) <media-query-list>` in either legal order-of-appearance
 *  (supports comes first per css-cascade-5 §3.1). Exported for tests. */
export function importConditionMatches(rest) {
  let tail = rest.trim();
  if (!tail) return true;                          // unconditional @import
  // Cascade layers change WHICH layer the imported rules land in, which this
  // flat model cannot express — decline rather than import them unlayered.
  if (/^layer\b/i.test(tail)) return false;
  if (/^supports\(/i.test(tail)) {
    // Find the matching ')' of supports( … ) with a depth walk — the
    // condition itself contains parens, so a lazy regex would stop early.
    let depth = 0, end = -1;
    for (let i = tail.indexOf('('); i < tail.length; i++) {
      if (tail[i] === '(') depth++;
      else if (tail[i] === ')') { depth--; if (depth === 0) { end = i; break; } }
    }
    if (end === -1) return false;                  // unbalanced — decline
    if (!supportsConditionProvable(tail.slice(tail.indexOf('('), end + 1))) return false;
    tail = tail.slice(end + 1).trim();
    if (!tail) return true;                        // supports-only, proven
  }
  return mediaQueryListMatches(tail);
}

/** Rewrite every RELATIVE `url()` payload in an imported sheet so it resolves
 *  against `baseDir` instead of the sheet's own directory.
 *
 *  This is what keeps the import faithful rather than merely present: URLs in
 *  an imported sheet resolve against the IMPORTED sheet's URL (css-values-4
 *  §4.5), while everything downstream in this extractor (the support-asset
 *  inliner, resolveFontFaces) resolves against the importing DOCUMENT's dir.
 *  Without this rewrite, `@import "support/MetricsTestFont.css"` — whose face
 *  says `src: url(cap-x-height.ttf)` — would have the extractor look for the
 *  font one directory too high and silently deliver no face at all. Absolute
 *  (`/…`), data:, http(s): and fragment payloads already resolve identically
 *  from either base and are left untouched. */
function rebaseUrls(css, sheetDir, baseDir) {
  return css.replace(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^)"'\s]+))\s*\)/gi, (whole, dq, sq, bare) => {
    const payload = dq ?? sq ?? bare ?? '';
    if (!payload || /^(?:data:|https?:|\/\/|#)/i.test(payload) || payload.startsWith('/')) return whole;
    const abs = resolve(sheetDir, payload);
    // POSIX separators: the value ends up in CSS text, not a filesystem call.
    const rel = relative(baseDir, abs).split(sep).join('/');
    return `url("${rel}")`;
  });
}

/**
 * Is this `@import` href SERVER-ROOT relative (`/fonts/ahem.css`)?
 *
 * Those are declined, and the reason is the REF CONTRACT rather than CSS.
 * capture-browser-ref.mjs renders the browser reference over `file://`, where
 * a leading `/` resolves against the filesystem root — `/fonts/ahem.css` 404s
 * and the reference renders WITHOUT the sheet. All 19 corpus tests that use
 * this idiom (`@import "/fonts/ahem.css";`, the css-text letter-spacing and
 * css-align families) have refs that import the SAME sheet and therefore lose
 * the Ahem face too, so honouring it on the fixture side alone manufactures a
 * divergence instead of removing one.
 *
 * MEASURED on those 19 tests, resolving them from disk: 18 SSIM down, 1 up,
 * 2 passing cells lost (css-text letter-spacing-211 0.9868→0.9530, -212
 * 0.9669→0.9487) and none gained. Declining them keeps the 10 cells the
 * RELATIVE imports win (which resolve identically on both sides) with nothing
 * traded away.
 *
 * WHY THIS IS NOT THE SAME CALL AS THE `/images/…` ASSET INLINER, which DOES
 * resolve server-root paths (wave-38 lane N4, the sparse-checkout half): the
 * image-set refs paint their expected result with `background-color: lime`
 * rather than by loading the same unreachable asset, so resolving it there
 * makes the capture MATCH the reference. Here it makes it differ. The rule in
 * both places is the same one — model what the Chromium reference actually
 * renders — and it lands on opposite answers because the two ref families are
 * written differently.
 *
 * If the ref capture ever serves the corpus over HTTP, delete this branch and
 * re-measure: the decline exists only for the file:// contract.
 */
function isServerRootHref(href) {
  return href.startsWith('/');
}

/** Recursion cap. WPT's deepest import chain is 1; 4 leaves room for a
 *  legitimately nested sheet while bounding a pathological one. */
const MAX_IMPORT_DEPTH = 4;

/**
 * Inline every PROVABLY-matching `@import` in one sheet, recursively.
 *
 * @param css      sheet text, already comment-stripped
 * @param sheetDir directory the sheet itself lives in (import hrefs resolve
 *                 against this)
 * @param baseDir  directory downstream url() resolution uses (the importing
 *                 DOCUMENT's dir) — see rebaseUrls
 * @param seen     absolute paths already inlined on this branch (cycle guard)
 * @param depth    recursion depth
 * @returns `{ css, inlined, declined }`
 *
 * A declined `@import` is left in the text VERBATIM, which is byte-identical
 * to the pre-wave-38 behaviour: parseCss skips it, scanFontFaces does not
 * match it, and the sampler never sees it.
 */
export async function resolveImports(css, sheetDir, baseDir, seen = new Set(), depth = 0) {
  let inlined = 0;
  let declined = 0;
  if (depth >= MAX_IMPORT_DEPTH || !/@import/i.test(css)) return { css, inlined, declined };
  let out = '';
  let cursor = 0;
  const re = /@import\b/gi;
  let m;
  while ((m = re.exec(css)) !== null) {
    // css-syntax-3 §3.1: `@import` is only valid before any style rule. A
    // `{` earlier in the sheet means we are past that point and the rule is
    // invalid — a browser drops it, so we must not honour it either.
    if (css.slice(0, m.index).includes('{')) break;
    const end = statementEnd(css, m.index + m[0].length);
    if (end === -1) break;                         // unterminated — leave as is
    const statement = css.slice(m.index, end);
    const prelude = css.slice(m.index + m[0].length, end - 1);
    out += css.slice(cursor, m.index);
    cursor = end;
    const { href, rest } = parseImportPrelude(prelude);
    if (!href || /^(?:https?:)?\/\//i.test(href) || /^data:/i.test(href)
      || isServerRootHref(href) || !importConditionMatches(rest)) {
      out += statement;                            // declined — verbatim
      declined++;
      re.lastIndex = end;
      continue;
    }
    const abs = resolve(sheetDir, href);
    if (seen.has(abs)) { out += statement; declined++; re.lastIndex = end; continue; }
    let text;
    try {
      text = stripComments(await fs.readFile(abs, 'utf8'));
    } catch {
      out += statement;                            // not on disk — verbatim
      declined++;
      re.lastIndex = end;
      continue;
    }
    const nested = await resolveImports(
      text, dirname(abs), baseDir, new Set([...seen, abs]), depth + 1,
    );
    inlined += 1 + nested.inlined;
    declined += nested.declined;
    // The imported rules take the @import's PLACE in the cascade
    // (css-cascade-5 §3.1), which is what makes import-conditional-001's
    // green sheet beat the red one imported above it.
    out += `\n${rebaseUrls(nested.css, dirname(abs), baseDir)}\n`;
    re.lastIndex = end;
  }
  out += css.slice(cursor);
  return { css: out, inlined, declined };
}

/** Extract <meta name="fuzzy"> content, parsed into the
 *  `{ maxDifference: { min, max }, totalPixels: { min, max } }` shape
 *  the WPT spec defines. Returns null when absent or unparseable.
 *
 *  Spec format (https://web-platform-tests.org/writing-tests/reftests.html#fuzzy-matching):
 *    content = "<maxDifference>;<totalPixels>"
 *  where each side is either "N" or "M-N"; values are inclusive.
 */
export function extractFuzzy(html) {
  const re = /<meta\b[^>]*?name=["']?fuzzy["']?[^>]*?content=["']([^"']+)["']/i;
  const m = re.exec(html);
  if (!m) return null;
  const raw = m[1];
  // Permit "maxDiff=10;totalPixels=20" key=value form too — some WPT tests
  // use it for clarity.
  const parts = raw.split(';').map((s) => s.trim()).filter(Boolean);
  if (parts.length === 0) return null;
  // Each part is "N" or "min-max" or "key=N" or "key=min-max"
  const parseRange = (s) => {
    // Strip optional "key=" prefix.
    const eq = s.indexOf('=');
    const v = eq >= 0 ? s.slice(eq + 1).trim() : s.trim();
    const dash = v.indexOf('-');
    if (dash < 0) {
      const n = Number(v);
      return Number.isFinite(n) ? { min: n, max: n } : null;
    }
    const min = Number(v.slice(0, dash));
    const max = Number(v.slice(dash + 1));
    if (!Number.isFinite(min) || !Number.isFinite(max)) return null;
    return { min, max };
  };
  const maxDifference = parseRange(parts[0]);
  const totalPixels   = parts.length >= 2 ? parseRange(parts[1]) : null;
  if (!maxDifference || !totalPixels) return null;
  return { maxDifference, totalPixels };
}

/** Extract the rel="match" href. Tolerates either attribute order. */
export function extractRefHref(html) {
  const re1 = /<link\b[^>]*?rel=["']?match["']?[^>]*?href=["']([^"']+)["']/i;
  const re2 = /<link\b[^>]*?href=["']([^"']+)["'][^>]*?rel=["']?match["']?/i;
  return (re1.exec(html)?.[1]) || (re2.exec(html)?.[1]) || null;
}

// ── Tiny CSS parser ──────────────────────────────────────────────────────────
//
// Just enough to handle the WPT reftest subset:
//   - selector { property: value; ... }
//   - one selector per rule (no comma lists with mixed targets — but we DO
//     split selector lists and emit a rule per element)
//   - skip @-rules entirely (they're bucket-B/C signals; the bucketer
//     already screens those). Exception carved out elsewhere: @keyframes
//     blocks are re-scanned by parseKeyframes() (wave-13 sampler section)
//     — this parser still skips them so rule extraction stays unchanged.
//
// wave-37 lane W8 — TWO CARVE-OUTS FROM "skip @-rules entirely".
// `@layer` and `@scope` are GROUPING at-rules: their bodies are ordinary
// style rules that DO apply to the document, so skipping the body threw the
// declarations away outright. MEASURED on the wave36-final depth-48 gate:
// 9 css-cascade cells (revert-layer-001/002/003/005/007/009/012/015,
// layer-media-toggle) and 7 more (@scope) fail with the capture showing only
// the caption line because the one rule that paints the green square lives
// inside `@layer { … }`. Both are now DESCENDED INTO:
//   • `@layer <names>;` registers layer order (first mention wins);
//     `@layer [<name>] { … }` tags every rule inside with `layerName`,
//     resolved to a numeric `layerIdx` (declaration order) at the end.
//   • `@scope (<start>) [to (<end>)] { … }` rewrites each inner selector:
//     `:scope` → the start selector, otherwise `<start> <sel>` (descendant).
//     The `to (<end>)` scoping LIMIT is not modelled — it can only ever make
//     a rule apply too WIDELY, never drop one that should paint, and every
//     corpus test that uses it is shadow-DOM bound anyway.
// Every OTHER at-rule (@media, @supports, @font-face, @keyframes, …) keeps
// the byte-identical skip, so no non-layer/non-scope fixture moves.
//
// Returns: Array<{ selector, props, layerName?, layerIdx?, important? }>
//   `layerName`/`layerIdx` appear ONLY on rules that came from a `@layer`
//   block and `important` ONLY when the rule carries `!important`, so a
//   sheet with neither produces the exact historical object shape.
export function parseCss(css) {
  const rules = [];
  // Layer names in DECLARATION order — the css-cascade-5 §6.4.1 layer order.
  // A name's position is fixed by its FIRST appearance, whether that is a
  // `@layer a, b;` statement or the first `@layer a { … }` block, which is
  // exactly what layer-media-toggle.html depends on (`@layer foo, bar;`
  // declared before the blocks makes `bar`'s green beat `foo`'s red).
  const layerOrder = [];
  const registerLayer = (name) => {
    if (!layerOrder.includes(name)) layerOrder.push(name);
    return name;
  };
  // Anonymous `@layer { … }` blocks are each their OWN layer (§6.4.1) and
  // can never be re-opened by name, so a monotonic counter is a faithful
  // identity for them. revert-layer-007's four anonymous layers are the
  // reason this must not collapse to a single bucket.
  let anonSeq = 0;
  scanRules(css, { layerPath: [], scopeStart: null, inScope: false });
  // Second pass: names → numeric ranks. Deferred because a layer's index is
  // only final once the whole sheet has been seen.
  for (const r of rules) {
    if (r.layerName != null) r.layerIdx = layerOrder.indexOf(r.layerName);
  }
  return rules;

  /** Rewrite one inner selector for the enclosing `@scope` prelude.
   *  Returns null when the rule cannot be expressed statically (a `:scope`
   *  reference under an IMPLICIT `@scope { … }`, whose root is the owning
   *  <style>'s parent element — a DOM fact this sheet-only parser cannot
   *  see, so we decline rather than mis-target it). */
  function applyScope(sel, ctx) {
    if (!ctx.inScope) return sel;
    if (/:scope\b/.test(sel)) {
      if (!ctx.scopeStart) return null;
      // `:scope::before` → `#before_test > main::before`; `:scope span` →
      // `#before_test > main span`. Substitution keeps combinators intact.
      return sel.replace(/:scope\b/g, ctx.scopeStart);
    }
    // No `:scope` → the rule is implicitly scoped to descendants of the root
    // (css-cascade-6 §3.1). An implicit `@scope { … }` has no static root, so
    // the rule is unwrapped unprefixed — the closest honest approximation.
    return ctx.scopeStart ? `${ctx.scopeStart} ${sel}` : sel;
  }

  function scanRules(src, ctx) {
    let i = 0;
    const n = src.length;
    while (i < n) {
      // Skip whitespace.
      while (i < n && /\s/.test(src[i])) i++;
      if (i >= n) break;
      if (src[i] === '@') {
        // Read the at-keyword so `@layer`/`@scope` can be told apart from
        // the at-rules that stay skipped.
        let nameEnd = i + 1;
        while (nameEnd < n && /[-\w]/.test(src[nameEnd])) nameEnd++;
        const atName = src.slice(i + 1, nameEnd).toLowerCase();
        // Prelude runs to the block's `{` or the statement's `;`.
        let j = nameEnd;
        while (j < n && src[j] !== '{' && src[j] !== ';') j++;
        const prelude = src.slice(nameEnd, j).trim();
        if (j >= n) break;
        if (src[j] === ';') {
          // Statement at-rule. `@layer a, b;` is the ONE that carries
          // meaning here: it fixes layer order without opening a block.
          if (atName === 'layer' && prelude) {
            for (const nm of prelude.split(',')) {
              const t = nm.trim();
              if (t) registerLayer([...ctx.layerPath, t].join('.'));
            }
          }
          i = j + 1;
          continue;
        }
        // Block at-rule — find the matching '}'.
        const bodyStart = j + 1;
        let depth = 1;
        let k = bodyStart;
        while (k < n && depth > 0) {
          const ch = src[k];
          if (ch === '{') depth++;
          else if (ch === '}') depth--;
          k++;
        }
        const body = src.slice(bodyStart, depth === 0 ? k - 1 : k);
        if (atName === 'layer') {
          // `@layer name { … }` re-opens (or opens) a named layer; a bare
          // `@layer { … }` opens a fresh anonymous one. Nested layers
          // qualify with a dot, matching the spec's `outer.inner` naming.
          const leaf = prelude || `#anon${anonSeq++}`;
          const path = [...ctx.layerPath, leaf];
          registerLayer(path.join('.'));
          scanRules(body, { ...ctx, layerPath: path });
        } else if (atName === 'scope') {
          // `(<start>)` is the first parenthesised group of the prelude; a
          // bare `@scope { … }` (implicit root) leaves scopeStart null.
          const m = /^\(([^)]*)\)/.exec(prelude);
          scanRules(body, { ...ctx, inScope: true, scopeStart: m ? m[1].trim() : null });
        }
        // Every other at-rule keeps the historical wholesale skip.
        i = k;
        continue;
      }
      // Read selector list up to '{'.
      const selStart = i;
      while (i < n && src[i] !== '{') i++;
      if (i >= n) break;
      const selectorList = src.slice(selStart, i).trim();
      i++; // consume '{'
      // Read body up to matching '}'.
      const bodyStart = i;
      let depth = 1;
      while (i < n && depth > 0) {
        const ch = src[i];
        if (ch === '{') depth++;
        else if (ch === '}') depth--;
        i++;
      }
      const body = src.slice(bodyStart, i - 1);
      // Parse declarations.
      const props = {};
      // Which of them carried `!important`. Recorded (not just stripped)
      // because css-cascade-5 §6.4.4 REVERSES layer order for important
      // declarations — revert-layer-005/012 turn entirely on that.
      const important = {};
      for (const decl of body.split(';')) {
        const colon = decl.indexOf(':');
        if (colon < 0) continue;
        const k = decl.slice(0, colon).trim();
        let v = decl.slice(colon + 1).trim();
        if (!k || !v) continue;
        // Strip !important — IR has no concept of it; the rendered value wins.
        const bang = /\s*!important\s*$/i.test(v);
        v = v.replace(/\s*!important\s*$/i, '').trim();
        // Skip custom properties (the bucketer flagged them as B already).
        if (k.startsWith('--')) continue;
        props[k] = v;
        if (bang) important[k] = true;
      }
      if (Object.keys(props).length === 0) continue;
      const hasImportant = Object.keys(important).length > 0;
      const layerName = ctx.layerPath.length ? ctx.layerPath.join('.') : null;
      // One rule per selector in the comma-list, so each element maps cleanly
      // to its own component.
      for (const sel of selectorList.split(',')) {
        const s = sel.trim();
        if (!s) continue;
        const scoped = applyScope(s, ctx);
        if (scoped === null) continue;
        const rule = { selector: scoped, props };
        if (layerName != null) rule.layerName = layerName;
        if (hasImportant) rule.important = important;
        rules.push(rule);
      }
    }
  }
}

/** The number of `@layer`s a parsed sheet declared. Used as the rank of the
 *  IMPLICIT OUTER LAYER: css-cascade-5 §6.4.1 puts unlayered author styles
 *  AFTER every explicit layer in declaration order, which is precisely what
 *  makes them win normal declarations and lose important ones. */
export function layerCountOf(rules) {
  let max = -1;
  for (const r of rules) if (typeof r.layerIdx === 'number' && r.layerIdx > max) max = r.layerIdx;
  return max + 1;
}

/** css-cascade-5 §7.3 `revert-layer`, and §5 `all`. */
const REVERT_LAYER = 'revert-layer';

/**
 * Resolve one element's declarations through the LAYERED cascade.
 *
 * `groups` are the declaration bags that matched, in document order, each
 * `{ rank, props, important?, inline? }`:
 *   • rank — the layer's declaration index; the implicit outer layer is
 *     `layerCount`, the style attribute `layerCount + 1`.
 *   • inline — style-attribute bag. Per the §6.4.4 sort order the style
 *     attribute is compared BEFORE layers, so it beats every layer at the
 *     same importance (in both directions).
 *
 * Sort order per property (highest wins): important > normal; within each,
 * inline > layered; within layered, LATER layer for normal declarations and
 * EARLIER layer for important ones; ties broken by document order.
 *
 * `revert-layer` then re-runs the same resolution over only the declarations
 * from layers declared EARLIER than the winner's — recursively, which is
 * what makes revert-layer-007's four-deep chain land back on the green in
 * the first layer. `all: revert-layer` applies that to every property that
 * has any declaration at all.
 *
 * @returns a plain `{ prop: value }` bag — no revert-layer keywords survive.
 */
export function resolveLayeredCascade(groups, layerCount) {
  const cands = [];
  let order = 0;
  for (const g of groups) {
    for (const [name, value] of Object.entries(g.props)) {
      cands.push({
        name, value, rank: g.rank, order: order++,
        important: !!(g.important && g.important[name]),
        inline: !!g.inline,
      });
    }
  }
  const names = new Set(cands.filter((c) => c.name !== 'all').map((c) => c.name));
  // `all: revert-layer` is a revert-layer declaration for EVERY property the
  // element has a declaration for (revert-layer-003 reverts width, height and
  // background-color in one line).
  for (const c of cands.filter((c) => c.name === 'all' && c.value === REVERT_LAYER)) {
    for (const name of names) {
      cands.push({ ...c, name, order: c.order });
    }
  }
  const better = (a, b) => {
    if (a.important !== b.important) return a.important;
    if (a.inline !== b.inline) return a.inline;
    if (a.rank !== b.rank) return a.important ? a.rank < b.rank : a.rank > b.rank;
    return a.order > b.order;
  };
  const resolveOne = (name, maxRank, depth) => {
    // A pathological revert-layer cycle cannot happen (maxRank strictly
    // decreases) but the guard keeps a malformed sheet from spinning.
    if (depth > 32) return undefined;
    let best = null;
    for (const c of cands) {
      if (c.name !== name || c.rank >= maxRank) continue;
      if (best === null || better(c, best)) best = c;
    }
    if (best === null) return undefined;
    if (best.value === REVERT_LAYER) return resolveOne(name, best.rank, depth + 1);
    return best.value;
  };
  const out = {};
  for (const name of names) {
    const v = resolveOne(name, layerCount + 2, 0);
    if (v !== undefined) out[name] = v;
  }
  return out;
}

/** True when a declaration bag needs the layered resolver — i.e. it carries a
 *  `revert-layer` value. Rules that merely SIT in a layer also need it, but
 *  that is tested on the rule itself; this covers the style attribute. */
function hasRevertLayer(props) {
  for (const v of Object.values(props)) if (v === REVERT_LAYER) return true;
  return false;
}

// ── Body element walker ──────────────────────────────────────────────────────
//
// We need top-level <body> children to map to components. The test corpus
// uses well-formed HTML so a regex-based walker that respects nested tags
// works fine — we don't need to handle exotic SGML constructs.
//
// Returns: Array<{ tag, attrs: { id?, class? }, raw: string }>
//   `raw` is the full original markup of the element (for nested-content
//   debugging).
// Walk the *direct* children of an arbitrary HTML fragment. Shared by
// extractBodyChildren (top-level), extractBodyTree (recursive flat),
// and extractBodyTreeNested (recursive nested). Returns
// `{ tag, attrs, raw, innerHtml, ownText }` per element. `ownText` holds
// the concatenated text nodes that are direct children of the element
// (text nodes nested inside descendant ELEMENTS are NOT included — those
// live on whatever descendant component owns them, per the per-element
// `_text` contract). Self-closing/void elements have empty innerHtml + ''.
//
// wave-21 B-RC2 (body-level bare text): `opts.collectText` additionally
// emits the RAW text runs between elements as `{ text: '…' }` items,
// interleaved in document order with the element items. Default off, so
// every legacy caller keeps the byte-identical element-only shape. The
// runs are raw (whitespace preserved) — the consumer decides collapse vs
// preserve per the resolved white-space, mirroring scanOwnText.
//
// wave-26 lane WWS (inter-sibling whitespace): each ELEMENT item may also
// carry `wsAfter: true` — "at least one ASCII whitespace character stood
// between this element's end tag and whatever markup came next at this
// level". The walker used to discard that fact (the `while (/\s/) i++`
// skip below), which is why the flat component wire cannot tell
// `<div></div> <div></div>` (one collapsed space advance in the browser)
// from `<div></div><div></div>` (flush). Emitted ONLY when whitespace was
// really there, so no consumer can ever manufacture a space the source
// did not have. Absent = "no whitespace" — the conservative default.
function walkChildren(html, opts = {}) {
  const out = [];
  let i = 0;
  // Index into `out` of the most recently pushed ELEMENT item (text items
  // from collectText mode never claim it) — the item a whitespace run
  // found here belongs to. -1 until the first element is pushed: leading
  // whitespace precedes every element and separates nothing.
  let lastElemIdx = -1;
  // Stamp `wsAfter` on that element when `run` holds any of the five
  // CSS Text §4.1 collapsible characters. Same [ \t\n\r\f] class (never
  // JS `\s`) the own-text collapse uses, so U+00A0 / U+3000 — which are
  // NOT collapsible whitespace and carry their own advance as real text —
  // can never be mistaken for a separator.
  const markWs = (run) => {
    if (lastElemIdx >= 0 && /[ \t\n\r\f]/.test(run)) out[lastElemIdx].wsAfter = true;
  };
  const n = html.length;
  while (i < n) {
    // wave-21 B-RC2: in collectText mode capture the WHOLE text run —
    // including leading whitespace — before the element scan below eats
    // it. One item per contiguous run; consecutive runs split only by
    // comments merge later in the consumer (they render adjacent).
    if (opts.collectText && html[i] !== '<') {
      const next = html.indexOf('<', i);
      const end = next < 0 ? n : next;
      // wave-26 WWS: the run is about to become its own text item, but it
      // ALSO separated the previous element from the next one — record
      // that before the item push (which never moves `lastElemIdx`).
      markWs(html.slice(i, end));
      out.push({ text: html.slice(i, end) });
      i = end;
      continue;
    }
    // Skip text whitespace.
    // wave-26 WWS: remember where the skip started so the discarded run
    // can still answer "did whitespace separate these two siblings?".
    const wsStart = i;
    while (i < n && /\s/.test(html[i])) i++;
    markWs(html.slice(wsStart, i));
    if (i >= n) break;
    // If not '<' it's stray text; skip to next '<' or end. Text nodes
    // never become components — only elements do (unless collectText).
    if (html[i] !== '<') {
      const next = html.indexOf('<', i);
      const end = next < 0 ? n : next;
      // Stray non-whitespace text also marks, when the run carries ANY
      // collapsible whitespace. DELIBERATE APPROXIMATION, not the strict
      // source-derived rule the rest of this lane follows — say so plainly:
      //   * `</b> and <b>` — the leading run was already consumed (and
      //     marked) by the whitespace skip above, so this call is redundant
      //     but harmless; `</b>foo <b>` is the case it really carries, and
      //     there the trailing space genuinely abuts the next element.
      //   * `</b>foo bar<b>` — NO whitespace touches either tag, yet the
      //     internal space still marks. That is an OVER-report against a
      //     literal reading of the marker ("source whitespace separated
      //     these two"). It is kept because the wire DROPS the stray text
      //     entirely: the ref paints `foo bar` between the boxes, so a
      //     one-space floor is strictly closer than flush, never further.
      // Bounded, not assumed: measured across 155 extractable WPT reftests
      // (12 sections), loose-vs-strict marker counts were IDENTICAL — 485
      // vs 485, zero over-reports — so this branch's approximation does not
      // fire anywhere in the live corpus. Tighten to a trailing-whitespace
      // test (`/[ \t\n\r\f]$/`) if a future fixture ever exercises it.
      markWs(html.slice(i, end));
      i = end;
      continue;
    }
    // Skip comments / DOCTYPE / processing instructions defensively (they
    // were stripped earlier but let's not assume).
    if (html.startsWith('<!', i) || html.startsWith('<?', i)) {
      const close = html.indexOf('>', i);
      i = close < 0 ? n : close + 1;
      continue;
    }
    // Closing tag at this level — the parent's recursion already paired it.
    // Treat as end of fragment.
    if (html.startsWith('</', i)) break;
    // Self-closing or void element handling: collect tag name + attrs.
    const tagOpenEnd = html.indexOf('>', i);
    if (tagOpenEnd < 0) break;
    const tagOpen = html.slice(i, tagOpenEnd + 1);
    const tagName = (/^<\s*([A-Za-z][A-Za-z0-9-]*)\s*/i.exec(tagOpen)?.[1] || '').toLowerCase();
    if (!tagName) { i = tagOpenEnd + 1; continue; }
    // Void elements per HTML5 spec — no closing tag, no body.
    const VOID = new Set([
      'area','base','br','col','embed','hr','img','input',
      'link','meta','param','source','track','wbr',
    ]);
    const selfClose = tagOpen.endsWith('/>') || VOID.has(tagName);
    let elementEnd, innerStart, innerEnd;
    if (selfClose) {
      elementEnd = tagOpenEnd + 1;
      innerStart = elementEnd;
      innerEnd = elementEnd;
    } else {
      // Bug 2b (auto-closing tags): HTML5 lets certain element types omit
      // their end tag — the next sibling opener (or a parent close) acts
      // as an implicit close. WPT relies on this heavily: e.g.
      // selectors/child-indexed-no-parent.html writes nine consecutive
      // `<p id="…">Should be green` lines without a single `</p>`. The
      // previous nested-scan returned empty innerHtml for every `<p>` and
      // dropped the text. We now look for the FIRST implicit-close trigger
      // (an opener of any tag in AUTO_CLOSE_TRIGGERS[tagName]) and, if it
      // appears before the real `</tagName>` closer, use its index as the
      // implicit element-end. See HTML Living Standard §13.2.6.4.
      const triggers = AUTO_CLOSE_TRIGGERS[tagName];
      let implicitClose = -1;
      if (triggers) {
        // Build one combined regex of `<(triggerA|triggerB|…)\b` so we walk
        // the haystack once. We deliberately match opener tags only — a
        // closer like `</td>` doesn't implicitly close a sibling `<td>`,
        // it explicitly closes whatever was open.
        const trigRe = new RegExp(
          `<(?:${[...triggers].join('|')})\\b`, 'gi',
        );
        trigRe.lastIndex = tagOpenEnd + 1;
        const t = trigRe.exec(html);
        if (t) implicitClose = t.index;
      }
      // Find matching `</tagName>` accounting for nested same-name tags.
      const openRe = new RegExp(`<${tagName}\\b`, 'gi');
      const closeRe = new RegExp(`</${tagName}\\s*>`, 'gi');
      openRe.lastIndex = tagOpenEnd + 1;
      closeRe.lastIndex = tagOpenEnd + 1;
      let depth = 1;
      let cursor = tagOpenEnd + 1;
      let lastCloseStart = -1;
      while (depth > 0 && cursor < n) {
        openRe.lastIndex = cursor;
        closeRe.lastIndex = cursor;
        const o = openRe.exec(html);
        const c = closeRe.exec(html);
        if (!c) break; // missing close — give up gracefully
        if (o && o.index < c.index) {
          depth++;
          cursor = o.index + o[0].length;
        } else {
          depth--;
          lastCloseStart = c.index;
          cursor = c.index + c[0].length;
        }
      }
      // Bug 2b: if an implicit-close trigger appears before any real close
      // (or the real close is the parent's, which never showed up at this
      // depth=1 frame), prefer the implicit position. lastCloseStart === -1
      // means we never found a real `</tagName>` at all — for auto-closing
      // tags, treat that as "element runs to the next implicit trigger or
      // end-of-fragment". This handles the trailing-`<p id="i">` case in
      // WPT's child-indexed-no-parent.html where the last paragraph has
      // neither a `</p>` closer nor any sibling block element after it,
      // so its body is everything up to EOF.
      if (triggers && implicitClose >= 0 &&
          (lastCloseStart < 0 || implicitClose < lastCloseStart)) {
        elementEnd = implicitClose;
        innerStart = tagOpenEnd + 1;
        innerEnd = implicitClose;
      } else if (triggers && lastCloseStart < 0) {
        // Auto-closing tag with no real closer and no implicit trigger —
        // body extends to end-of-fragment.
        elementEnd = n;
        innerStart = tagOpenEnd + 1;
        innerEnd = n;
      } else if (lastCloseStart < 0 &&
                 adoptsUnclosedBody(tagOpen, html, tagOpenEnd + 1, n)) {
        // wave-33 lane N (N1 WRAPPER FLATTENING) — the LOAD-BEARING
        // unclosed-wrapper adoption. Same end-of-fragment body the
        // auto-closing branch above grants, but for a tag whose end tag is
        // NOT spec-omittable; see adoptsUnclosedBody for the gate and for
        // why it is deliberately narrower than the raw HTML5 rule.
        elementEnd = n;
        innerStart = tagOpenEnd + 1;
        innerEnd = n;
      } else {
        elementEnd = cursor;
        innerStart = tagOpenEnd + 1;
        innerEnd = lastCloseStart >= 0 ? lastCloseStart : cursor;
      }
    }
    // Parse attrs of the opening tag.
    // The unquoted-value alternative excludes `>` (and `/`, for self-closing
    // tags) so `<div class=test>` parses `class="test"` instead of including
    // the closing bracket — a regression that surfaced in
    // css/css-text/hanging-punctuation/hanging-punctuation-first-002.html.
    const attrs = {};
    const attrRe = /([A-Za-z_:][A-Za-z0-9_.\-:]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>/]+)))?/g;
    // Skip past `<tagName` so the attr regex doesn't pick up the tag name.
    const afterName = tagOpen.replace(/^<\s*[A-Za-z][A-Za-z0-9-]*\s*/, '<');
    let am;
    attrRe.lastIndex = 1; // past the leading '<'
    while ((am = attrRe.exec(afterName)) !== null) {
      const k = am[1].toLowerCase();
      const v = am[2] ?? am[3] ?? am[4] ?? '';
      attrs[k] = v;
    }
    out.push({
      tag: tagName, attrs,
      raw: html.slice(i, elementEnd),
      innerHtml: html.slice(innerStart, innerEnd),
    });
    // wave-26 WWS: this is now the element any following whitespace run
    // belongs to. Text items (collectText) deliberately never claim it —
    // a run between element A and element B is A's `wsAfter` whether or
    // not the walker also emitted it as a standalone text item.
    lastElemIdx = out.length - 1;
    i = elementEnd;
  }
  return out;
}

// ── HTML5 auto-closing tag table ─────────────────────────────────────────────
//
// Per HTML Living Standard §13.2.6.4 ("the in body insertion mode"), several
// element types implicitly close when a sibling-level opener of a specific
// kind appears. We mirror the subset that the WPT corpus actually relies on.
// Each key is a tag whose end-tag may be omitted; the value is the set of
// opener tag names that, when seen after the key element's open tag, act as
// its implicit close.
//
// Key references:
//   - <p>: closed by any of the block-level elements listed in the spec's
//     "have a p element in button scope" close trigger set, plus another <p>.
//   - <li>: closed by another <li>.
//   - <dt> / <dd>: closed by either of <dt>, <dd>.
//   - <option>: closed by another <option> or <optgroup>.
//   - <thead> / <tbody>: closed by <tbody>, <tfoot> (or another <thead> for
//     authoring resilience; the spec lists tbody/tfoot specifically).
//   - <tr>: closed by another <tr> or a section change (<tbody>/<tfoot>/<thead>).
//   - <td> / <th>: closed by another <td>/<th> or a row change (<tr>).
//
// We do NOT model the rarer cases (rp/rt, ruby) because they don't appear
// in the bucket-A corpus. The current scaffolding makes adding them trivial.
const AUTO_CLOSE_TRIGGERS = {
  p:  new Set([
    'address','article','aside','blockquote','details','div','dl','fieldset',
    'figcaption','figure','footer','form','h1','h2','h3','h4','h5','h6',
    'header','hgroup','hr','main','menu','nav','ol','p','pre','section',
    'table','ul',
  ]),
  li: new Set(['li']),
  dt: new Set(['dt','dd']),
  dd: new Set(['dt','dd']),
  option: new Set(['option','optgroup']),
  thead:  new Set(['tbody','tfoot']),
  tbody:  new Set(['tbody','tfoot']),
  tr:  new Set(['tr','tbody','tfoot','thead']),
  td:  new Set(['td','th','tr']),
  th:  new Set(['td','th','tr']),
};

// ── wave-33 lane N (N1): the LOAD-BEARING unclosed-wrapper adoption ──────────
//
// MEASURED PROBLEM (runs/wave32-final/sections/css-tables, absolute-tables-010
// .tentative — the wave-32 reclassification):
//
//   <div class="container" style="margin-left: 100px;">
//     <div style="margin-left: -100px;">
//       <table>…</table>          ← position:absolute, height:100px, green
//   </div>                        ← ONE `</div>` for TWO `<div>` openers
//
// walkChildren pairs that single `</div>` with the OUTER div (its depth
// counter hits the closer at depth 2→1 and stores it as `lastCloseStart`),
// so the outer div's inner buffer STOPS before it. The INNER div is then
// left with no `</div>` anywhere in its fragment: `lastCloseStart` stays -1,
// the final `else` branch below sets innerEnd === elementEnd === cursor ===
// tagOpenEnd + 1, and the element is emitted with a ZERO-LENGTH body. Its
// real content is re-walked from right after the open tag — so the abspos
// <table> comes out a SIBLING of the -100px wrapper instead of its child.
//
// That flattening is not cosmetic. CSS 2.1 §10.3.7 / css-position-3 §3.3
// define an abspos box's static position as the position its hypothetical
// box would have had in its PARENT's content flow; with the wrapper gone
// from the parent chain, no runtime can subtract the wrapper's -100px
// margin, and all three engines faithfully paint the green square at x=116
// where the ref has it at x=16.
//
// THE SPEC RULE would be blunt: HTML Living Standard §13.2.6.5 ("an
// end-of-file token" pops every still-open element) says an unmatched open
// tag is closed by its PARENT's close, i.e. its body runs to end-of-
// fragment, full stop. Applying that unconditionally was MEASURED over all
// 10681 bucket-A tests: 115 fixtures drift, and 15 of them LOSE content —
// because the tree walk is depth-capped at 5, and a document like
// css-text/text-transform/text-transform-capitalize-035.html (six
// `<div lang=…>` blocks each terminated by a typo'd `<div>` instead of
// `</div>`, so TWELVE unclosed divs nest 12 deep) pushes half its text past
// the cap, where extractBodyTreeNested silently drops it. Flat-but-complete
// beats nested-but-truncated, so the blunt rule is not the trade we want.
//
// THE GATE (deliberately narrower than the spec, and honest about it): adopt
// the end-of-fragment body only when BOTH hold —
//
//   1. the adopted body actually CONTAINS an element (`<tag`). With no
//      element inside, adoption changes no structure at all — the text was
//      already reachable — so we leave those bytes on the pre-wave-33 path.
//   2. the wrapper is LOAD-BEARING: its own `style=` attribute declares at
//      least one BOX-AFFECTING property. Those are exactly the declarations
//      whose effect on the children is only computable THROUGH the parent
//      link — offsets (margin/padding/border/inset), containing-block
//      establishment (position/transform), and the box's own extent
//      (width/height/display/float/overflow). A wrapper with no such
//      declaration contributes nothing a child needs its parent for, so
//      nesting it buys nothing and only spends depth budget.
//
// WHY THE `style=` ATTRIBUTE AND NOT THE MATCHED CASCADE: the direction of
// the failure modes is asymmetric here, and opposite to the styledTags
// guard's. Over-adopting DELETES content at the depth cap; under-adopting
// merely keeps today's flat-but-complete emission. So the conservative
// choice is the signal the walker can see locally and that authors use
// precisely when the wrapper's geometry is the point of the test. The known
// under-fix — a wrapper whose box geometry comes from a STYLESHEET rule
// (`div { margin-top: 1em }`) stays flattened — is stated, not hidden;
// capitalize-035 above is exactly that shape and is exactly the case we do
// NOT want adopted.

/** Box-affecting property prefixes: a declaration whose effect on a CHILD is
 *  only expressible through the parent link (offsets, containing block,
 *  extent). Matched as a prefix so every longhand/logical variant is covered
 *  — `margin-inline-start`, `border-block-end-width`, `inset-inline`, … */
const BOX_AFFECTING_PROPERTY_PREFIXES = [
  'margin', 'padding', 'border', 'position', 'inset',
  'top', 'right', 'bottom', 'left',
  'transform', 'translate', 'rotate', 'scale', 'perspective',
  'width', 'height', 'min-width', 'min-height', 'max-width', 'max-height',
  'display', 'float', 'clear', 'overflow', 'box-sizing', 'zoom',
];

/** Does `styleAttr` (the raw contents of a `style="…"` attribute) declare at
 *  least one BOX_AFFECTING_PROPERTY_PREFIXES property? Split on top-level
 *  `;` is enough: a property NAME can never contain `;` or `(`, so we only
 *  need the text left of each `:`. */
export function hasBoxAffectingInlineStyle(styleAttr) {
  if (typeof styleAttr !== 'string' || styleAttr.length === 0) return false;
  for (const decl of styleAttr.split(';')) {
    const colon = decl.indexOf(':');
    if (colon < 0) continue;
    const prop = decl.slice(0, colon).trim().toLowerCase();
    if (!prop) continue;
    if (BOX_AFFECTING_PROPERTY_PREFIXES.some((p) => prop === p || prop.startsWith(`${p}-`))) {
      return true;
    }
  }
  return false;
}

/** The N1 gate itself (see the banner above). `tagOpen` is the element's raw
 *  open tag, `html`/`from`/`n` bound the body it WOULD adopt.
 *  Exported so the unit tests can pin the gate without going through a walk. */
export function adoptsUnclosedBody(tagOpen, html, from, n) {
  // Condition 1 — adoption must actually change the structure. `indexOf('<')`
  // is enough: comments/DOCTYPE were stripped upstream, and a bare `<` in
  // text is not legal HTML, so any `<` after the opener starts an element.
  if (from >= n) return false;
  const firstTag = html.indexOf('<', from);
  if (firstTag < 0 || firstTag >= n) return false;
  // Condition 2 — the wrapper must be load-bearing. Read the `style=`
  // attribute straight off the raw open tag (walkChildren parses attrs a few
  // lines later; doing it here would mean parsing twice for every element,
  // whereas this branch is reached only for genuinely unclosed tags).
  const m = /\sstyle\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(tagOpen);
  if (!m) return false;
  return hasBoxAffectingInlineStyle(m[1] ?? m[2] ?? m[3] ?? '');
}

/**
 * wave-21 A-RC1 HEAD-UNWRAP fix: the ONE shared body-content locator.
 *
 * Every walker used to duplicate the same two-step locate: (1) prefer an
 * explicit <body>…</body>; (2) otherwise peel a leading <html>/<head>/<body>
 * wrapper with `/^<(html|head|body)\b[^>]*>([\s\S]*?)(?:<\/\1\s*>|$)/i` and
 * keep GROUP 2 — the wrapper's inner content. That group-2 rule is correct
 * for <html>/<body> (their content IS the body content) but was a SILENT
 * TOTAL LOSS for <head>: on a doc shaped
 *
 *   <html><head>…</head>   ← head properly closed
 *     <p>…</p><div></div>  ← body content, but NO <body> tag
 *   </html>
 *
 * iteration 1 unwraps <html> (fine), iteration 2 then matched
 * `<head>…</head>` and replaced the buffer with the HEAD'S OWN content —
 * every element after </head> was discarded, the walk found nothing
 * renderable, and buildComponents emitted a lone 100x100 placeholder with
 * `lossy: false` (the exact cross-fade-premultiplied-alpha failure this
 * wave exists for). Per HTML §13.2.6.4.4 ("after head" insertion mode) the
 * content FOLLOWING a closed <head> is body content, so for a matched
 * 'head' wrapper we now keep everything AFTER the match instead of group 2.
 *
 * A <head> that never closes (regex matched via the `$` alternative) still
 * unwraps to group 2: with no </head> the head/body boundary is implicit
 * (§13.2.6.4.3 — body-content tokens implicitly close the head), so head
 * scaffolding and body content are interleaved in group 2 and the callers'
 * HEAD_ONLY_TAGS filter separates them — same behaviour as before.
 *
 * The loop runs up to THREE peels (was two): a full shell is
 * <html> → <head> → <body>, and since the head fix keeps the sibling
 * <body…> wrapper in the buffer (instead of destroying it), a third
 * iteration must be able to unwrap it (shape: <html><head>…</head><body>
 * with an unclosed body — bodyMatch requires the close tag, so it lands
 * here).
 *
 * Returns `{ inner, fallback }`: `inner` is the body-content buffer;
 * `fallback` is true when no explicit <body>…</body> pair existed (callers
 * use it to decide whether the HEAD_ONLY filter is needed at depth 1).
 * Exported so the unit tests can pin the head-unwrap contract directly.
 *
 * ── wave-29 S-RC1 PHANTOM BODY (step 1.5) ──────────────────────────────────
 *
 * The step-1 → step-2 pair still had a hole: a document whose <body …> start
 * tag is NOT the first thing after the doctype and which never closes. The
 * whole css-pseudo/active-selection family is exactly that shape —
 *
 *   <!DOCTYPE html>
 *     <meta charset="UTF-8">      ← head scaffolding, NOT wrapped in <head>
 *     <title>…</title> <link…> <style>…</style> <script>…</script>
 *   <body onload="startTest();">  ← never closed
 *     <p>Test passes if …
 *     <div id="test">Selected Text</div>
 *
 * — and it fell through BOTH steps. Step 1 needs a `</body>` (absent). Step 2
 * anchors its wrapper regex at `^` after the doctype, and the first tag there
 * is `<meta>`, not html/head/body, so the loop breaks on iteration 0 and the
 * buffer stays the ENTIRE document. Downstream, walkChildren then emits a
 * `body` component for the bare unclosed start tag — an element with no
 * content, which buildComponents renders as a 100x100 phantom placeholder
 * sitting on top of the real <p>/<div>. Every SSIM comparison for these tests
 * was scored against a fixture carrying a box the browser never painted.
 *
 * Step 1.5 closes it with the spec rule the other two steps already lean on:
 * per HTML §13.2.6.4.4 an end tag for `body` is OPTIONAL, so a start tag with
 * no matching close is well-formed and everything after it is body content.
 *
 * TWO honesty guards keep this from over-firing, both derived from the same
 * insertion-mode reading (§13.2.6.4.7 "in body"): a `<body>` start tag seen
 * while ALREADY in body is a parse error that the parser IGNORES (it only
 * merges attributes onto the existing body element). So the tag we slice at
 * must be the one that actually OPENS the body:
 *
 *   1. MASKED SCAN — the `<body` we match must be real markup, not text that
 *      merely looks like a tag. `maskNonMarkupForBodyScan` blanks comments,
 *      <script>/<style>/<title>/<noscript> element contents and quoted
 *      attribute values to same-length runs of spaces, so indices still line
 *      up against the original string. Without it six corpus documents fire
 *      on a `<body>` written inside a JS string
 *      (cssom/computed-style-002 `frmDoc.write('<body …')`), inside a prose
 *      comment (css-flexbox/flexbox-root-node-001b) or — the damaging one —
 *      inside an attribute: css-writing-modes/orthogonal-root-resize-icb-001
 *      has `<iframe src="data:text/html,…<body style='margin:0;'>…">`, and
 *      slicing there would have thrown away the real <p> and <iframe>.
 *
 *   2. HEAD-ONLY PREFIX — everything before the start tag must be markup the
 *      parser can still be in "before head" / "in head" / "after head" mode
 *      for: the doctype, whitespace, and start/end tags drawn only from
 *      BODY_PREFIX_TAGS. A non-head element (or non-whitespace text) before
 *      the tag means the body was ALREADY implicitly opened, so this `<body>`
 *      is the ignorable-parse-error kind and slicing at it would silently
 *      drop everything the implicit body already contains
 *      (css-contain/content-visibility/slot-content-visibility-3-crash puts
 *      `<body hidden>` after a real <div>/<template>/<span> subtree).
 *
 * When either guard declines, step 1.5 is a no-op and control falls through
 * to the step-2 peel — the pre-wave-29 behaviour, byte for byte.
 */

/** Element names whose CONTENT is never markup we want to scan for a body
 *  start tag. <script>/<style> are raw-text elements and <title>/<noscript>
 *  are escapable-raw-text/parser-state-dependent (HTML §13.2.5.1) — a
 *  `<body>` written inside any of them is TEXT to the tokenizer, never a
 *  tag. Blanking the whole element (tags included) is fine: step 1.5 only
 *  ever slices at a point AFTER a match, and the prefix guard treats a
 *  blanked run as the whitespace it now is. */
const BODY_SCAN_RAWTEXT_RX =
  /<(script|style|title|noscript)\b[^>]*>[\s\S]*?<\/\1\s*>/gi;

/** Quoted attribute values, blanked so `src="…<body …>…"` cannot be mistaken
 *  for a tag. Applied AFTER comment + raw-text blanking so a stray `="` in a
 *  comment or a JS string can never mis-pair the quotes here. */
const BODY_SCAN_ATTR_VALUE_RX = /=\s*("[^"]*"|'[^']*')/g;

/** Tags the HTML parser tolerates BEFORE the body opens — the "in head"
 *  element set (§13.2.6.4.4) plus the two shell wrappers. Anything else
 *  implicitly opens the body, which is what guard 2 tests for. */
const BODY_PREFIX_TAGS = new Set([
  'html', 'head',                                    // document shell wrappers
  'base', 'basefont', 'bgsound', 'link', 'meta',     // metadata, void
  'title', 'style', 'script', 'noscript', 'template', // metadata, with content
]);

/**
 * Blank every region of `html` whose bytes are not tag markup, preserving
 * LENGTH so a match index maps straight back onto the original string.
 * Exported so the unit tests can pin the masking contract directly.
 */
export function maskNonMarkupForBodyScan(html) {
  // Same-length blank: one space per original character. Newlines become
  // spaces too — irrelevant, since the mask is only ever scanned for tags.
  const blank = (m) => ' '.repeat(m.length);
  // Comments first: they can legally contain anything, including `<script>`.
  let out = html.replace(/<!--[\s\S]*?-->/g, blank);
  // Then raw-text elements, then attribute values (see the RX doc comments
  // above for why this order is the safe one).
  out = out.replace(BODY_SCAN_RAWTEXT_RX, blank);
  out = out.replace(BODY_SCAN_ATTR_VALUE_RX, blank);
  return out;
}

/**
 * Guard 2: is everything in `prefix` (a MASKED slice ending just before a
 * `<body …>` start tag) still "before the body" per the HTML insertion
 * modes? True only when the prefix holds nothing but the doctype,
 * whitespace, and start/end tags from BODY_PREFIX_TAGS.
 * Exported for direct unit pinning.
 */
export function isBeforeBodyPrefix(prefix) {
  // Drop the doctype — it is a document-level token, not an element.
  let rest = prefix.replace(/<!doctype\b[^>]*>/i, ' ');
  // Also drop any remaining markup declaration / processing-instruction-ish
  // token (`<!…>`, `<?…>`); the tokenizer treats them as comments/bogus and
  // neither opens the body.
  rest = rest.replace(/<[!?][^>]*>/g, ' ');
  // Walk every start/end tag: an unlisted name means the body already opened.
  const tagRx = /<\/?([a-zA-Z][\w-]*)\b[^>]*>/g;
  let m;
  while ((m = tagRx.exec(rest)) !== null) {
    if (!BODY_PREFIX_TAGS.has(m[1].toLowerCase())) return false;
  }
  // Finally: non-whitespace TEXT outside those tags also opens the body
  // (§13.2.6.4.4 — a character token in "after head" mode inserts <body>).
  // Strip the tags we just validated and require the remainder be blank.
  return rest.replace(tagRx, '').trim() === '';
}

export function locateBodyContent(html) {
  // Step 1 — explicit, well-formed <body>…</body> wins outright: its inner
  // is body content BY DEFINITION and no unwrapping is needed.
  const bodyMatch = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  if (bodyMatch) return { inner: bodyMatch[1], fallback: false };
  // Step 1.5 — S-RC1: an UNCLOSED <body …> anywhere in the document (see the
  // banner above). Scan the masked copy so only real markup can match, then
  // require the head-only prefix before slicing.
  const masked = maskNonMarkupForBodyScan(html);
  const openBody = /<body\b[^>]*>/i.exec(masked);
  if (openBody && isBeforeBodyPrefix(masked.slice(0, openBody.index))) {
    // Everything after the start tag is body content. `fallback: true` is
    // both literally accurate (no <body>…</body> PAIR existed) and the safe
    // choice: it keeps the callers' HEAD_ONLY_TAGS filter on, so an in-body
    // <script>/<style> still cannot manufacture a phantom component.
    return { inner: html.slice(openBody.index + openBody[0].length), fallback: true };
  }
  // Step 2 — no <body> pair: peel document-shell wrappers front-to-back.
  let inner = html;
  for (let unwrap = 0; unwrap < 3; unwrap++) {
    // Skip an optional <!doctype …> + leading whitespace before the wrapper.
    const skipped = inner.replace(/^\s*<!doctype\b[^>]*>\s*/i, '').replace(/^\s+/, '');
    // Lazy group 2 = wrapper content up to its close tag (or EOF via `$`).
    const wrapMatch = /^<(html|head|body)\b[^>]*>([\s\S]*?)(?:<\/\1\s*>|$)/i.exec(skipped);
    if (!wrapMatch) break;
    // A-RC1: a CLOSED head's content is head scaffolding — body content is
    // what FOLLOWS </head> (HTML §13.2.6.4.4). Detect "closed" by the match
    // ending in the close tag; an EOF-terminated (`$`) match keeps group 2.
    const closedHead = wrapMatch[1].toLowerCase() === 'head'
      && /<\/head\s*>$/i.test(wrapMatch[0]);
    inner = closedHead ? skipped.slice(wrapMatch[0].length) : wrapMatch[2];
  }
  return { inner, fallback: true };
}

export function extractBodyChildren(html) {
  // Find <body>…</body>. If absent, treat the whole document as the body
  // (minimal WPT tests sometimes omit explicit <body>).
  //
  // Bug 5 fix (pilot-001 / css-anchor-position/anchor-center-no-default):
  // when we fall through to whole-document mode the walker would otherwise
  // emit components for every depth-1 element including <title>, <meta>,
  // <link>, <style>, <script>, <head>. Those are head-scoped and never
  // render — they manufacture phantom 100x100 placeholders that pollute
  // the IR (one anchor-positioning test produced 5 components for 1 real
  // <div>). When in fallback mode we keep the walk wide but filter out
  // HEAD_ONLY_TAGS at emit time. The explicit-<body> path doesn't need
  // this guard because authors don't put <title>/<meta> inside <body>.
  //
  // wave-21 A-RC1: the locate + unwrap now goes through the ONE shared
  // locateBodyContent (head-unwrap fix banner above) so all four walkers
  // agree on what "body content" is — this site previously skipped the
  // wrapper peel entirely, emitting a phantom `html` component for
  // closed-<html> no-<body> docs.
  const { inner, fallback } = locateBodyContent(html);
  const raw = walkChildren(inner);
  // In fallback mode strip head-only elements; otherwise the inner is body
  // and everything is by definition body content.
  const filtered = fallback
    ? raw.filter((c) => !HEAD_ONLY_TAGS.has(c.tag))
    : raw;
  // Drop the internal `innerHtml` field from the public API (back-compat
  // with the existing unit tests that snapshot `kids[i].tag`/`attrs`/`raw`).
  return filtered.map(({ tag, attrs, raw: r }) => ({ tag, attrs, raw: r }));
}

/**
 * Walk the body subtree to a bounded depth, returning each element with its
 * ancestor chain so descendant selectors can match honestly.
 *
 * Bug 2 fix (pilot-001 / css-counter-styles/css3-counter-styles-101): the
 * extractor previously emitted only top-level body children. Selectors like
 * `ol li {…}`, `.test ol`, `div .target` whose rightmost compound matched a
 * descendant of a body child were silently dropped — selectorMatches() only
 * compared the rightmost compound against the body-child's tag, so the
 * `<li>` rule never landed on anything. ~201 css-counter-styles + ~156
 * css-lists tests die from this single root cause.
 *
 * Returns `Array<{ tag, attrs, raw, ancestors: Array<{ tag, attrs }> }>`
 * including the top-level children themselves (ancestors = []) and their
 * nested descendants down to `maxDepth` (default 3 — enough for the
 * `ol > li`, `table > tr > td`, `div > .test > p` shapes the WPT corpus
 * uses without unbounded blow-up on deeply nested fixtures).
 */
export function extractBodyTree(html, maxDepth = 3) {
  // Prefer the explicit <body>...</body>. If absent, treat the whole document
  // as the body and rely on HEAD_ONLY_TAGS + the shared wrapper peel to skip
  // head scaffolding. wave-21 A-RC1: the locate + implicit-<html>/<head>
  // unwrap is now the ONE shared locateBodyContent (see its banner) — the
  // closed-<head> shape previously discarded everything after </head> here.
  const { inner } = locateBodyContent(html);
  const out = [];
  function recurse(fragment, ancestors, depth) {
    const kids = walkChildren(fragment);
    for (const k of kids) {
      // Head-only tags never become body components, regardless of depth.
      // <title>/<meta>/<link>/<style>/<script>/<base>/<head> are scaffolding
      // — emitting them creates phantom 100x100 placeholders (Bug 5) and at
      // deeper levels (e.g. an inline <style> nested in a div) confuses the
      // naïve regex walker.
      if (HEAD_ONLY_TAGS.has(k.tag)) continue;
      out.push({
        tag: k.tag, attrs: k.attrs, raw: k.raw,
        ancestors: ancestors.slice(),
      });
      if (depth + 1 < maxDepth && k.innerHtml) {
        recurse(k.innerHtml, [...ancestors, { tag: k.tag, attrs: k.attrs }], depth + 1);
      }
    }
  }
  recurse(inner, [], 0);
  return out;
}

/**
 * Extract the OWN text content of an element from its innerHtml.
 *
 * "Own text" = text nodes that are direct children of the element. Text
 * inside descendant elements is NOT included — those characters belong to
 * whatever descendant component owns them, per the per-element `_text`
 * contract documented in the swarm-001 css-color/color-001 investigation.
 *
 * Algorithm: walk innerHtml, collect every char range that is NOT inside
 * a child element tag (or its content). We do that by scanning forward,
 * appending text up to the next '<', then jumping past the matching close
 * tag (using the same nested-tag depth tracking as walkChildren). HTML
 * comments / DOCTYPE / processing instructions are skipped (already
 * stripped by stripComments anyway, but defensive).
 *
 * Whitespace handling matches CSS `white-space: normal` defaults: leading
 * + trailing whitespace trimmed, internal runs collapsed to a single
 * space. This is the rendered surface a browser would paint by default;
 * white-space: pre tests are rare in the WPT corpus and are out of scope
 * for the v1 contract (would require knowing the parent's white-space
 * value at extract time, which would couple the extractor to CSS cascade).
 *
 * Returns the empty string when the element has no own text.
 *
 * Exported so the unit tests can pin the contract. Since wave-12 this is a
 * thin wrapper over scanOwnText() with merging OFF — byte-identical to the
 * pre-wave-12 behaviour for every legacy caller.
 */
export function extractOwnText(innerHtml) {
  // Merging disabled (mergeCtx = null): descendant-element text is always
  // excluded, exactly as before wave-12. Only the `.text` half is exposed.
  return scanOwnText(innerHtml, null).text;
}

// ── wave-12 EXTRACTOR-INLINE: pure-inline run merging ────────────────────────
//
// The cross-platform inline-run fragmentation fix. WPT's standard reftest
// preamble is `<p>Test passes if there is a filled green square and
// <strong>no red</strong>.</p>`. The pre-wave-12 extractor flattened the
// <p>'s OWN text nodes into one string — GLUING 'and' + '.' across the
// removed <strong> ('…green square and .') — and emitted the <strong> as a
// SEPARATE child component that every runtime stacks as a BLOCK. Net effect:
// the paragraph renders as 3 lines vs the browser-ref's 2, displacing
// everything below by ~+20px on ALL THREE platforms, and the synthetic
// 'and .' token also splits Android/iOS line breaking (UAX#14 LB13 treats
// the orphan '.' as a distinct break opportunity).
//
// SHORT-TERM CONTRACT: merge pure-inline children (strong/em/b/i/span/code
// whose content is text-only — no nested elements, no attributes) INTO the
// parent's `_text` in reading order, dropping the inline styling (default
// bold weight etc.) and marking the component lossy with reason
// 'inline-run-merged'. This is an honest, documented approximation: a small
// font-weight/anti-aliasing divergence replaces a structural 3-vs-2-line
// divergence plus an orphan token. The REAL fix — an interleaved inline-runs
// wire on IRComponent — is a byte-shape change and therefore v3-gated per
// schema/spec/05-versioning.md (v2 is the frozen current wire; any further
// byte-shape change is a major-version freeze event, not a casual PR).
//
// Non-pure inline children (nested elements, any attribute, block-ish tags,
// or tags targeted by a CSS rule) keep the current child-component path so
// styled test subjects are never destroyed by the merge.

// The inline tags eligible for merging. Exactly the phrase-content tags the
// WPT preamble corpus uses; block-ish tags (<div>, <p>, …) and semantic
// containers stay components. <span> is included ONLY because bare spans
// (no attrs, no matching rules) are pure text wrappers — the attribute +
// styledTags guards below keep every styled-subject span on the child path.
// wave-16 POST-LOAD: exported so post-load-extract.mjs's in-browser walk can
// apply the SAME merge filter (same tag set, same predicate inputs) and stay
// element-for-element aligned with the static traversal.
export const INLINE_MERGE_TAGS = new Set(['strong', 'em', 'b', 'i', 'span', 'code']);

/** Parse the attribute map out of a raw open tag (`<strong title="x">`).
 *  Mirrors walkChildren's attr scan exactly (same regex, same
 *  unquoted-value exclusions) so merge decisions made from a raw tagOpen
 *  agree with decisions made from walkChildren's parsed kids. */
function parseAttrsFromTagOpen(tagOpen) {
  const attrs = {};
  // Strip the tag name so the attr regex can't capture it as an attribute
  // (same normalisation walkChildren applies before its attr scan).
  const afterName = tagOpen.replace(/^<\s*[A-Za-z][A-Za-z0-9-]*\s*/, '<');
  // Attr tokens: name, then optionally = "double" | 'single' | unquoted.
  const attrRe = /([A-Za-z_:][A-Za-z0-9_.\-:]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>/]+)))?/g;
  attrRe.lastIndex = 1; // past the leading '<'
  let am;
  while ((am = attrRe.exec(afterName)) !== null) {
    // Lowercased key + first non-undefined value alternative ('' for bare).
    attrs[am[1].toLowerCase()] = am[2] ?? am[3] ?? am[4] ?? '';
  }
  return attrs;
}

/**
 * Collect every tag name that appears as the RIGHTMOST compound's tag in
 * any CSS rule. Used as a merge guard: a pure-inline child whose tag is
 * directly targeted by a rule (`strong { color: red }`, `.note em {…}`,
 * `span::before {…}`) must stay a component, otherwise the merge would
 * silently drop the very declarations under test.
 *
 * Rightmost-only is sufficient: a non-rightmost compound (`strong span`)
 * needs the tag to have ELEMENT descendants to match anything, and a
 * mergeable child is text-only by definition — no descendants exist to
 * lose. The universal `*` is deliberately NOT collected: WPT reset rules
 * (`* { margin: 0 }`) also land on the parent that keeps the text, and
 * non-inherited universal props on an anonymous inline run are a corner
 * accepted under the 'inline-run-merged' lossy marker.
 *
 * wave-33 lane N (N2) — THE RIGHTMOST-UNSUPPORTED GUARD HOLE (deferred from
 * wave-30 A2). The pre-wave-33 code `continue`d on an unsupported rightmost
 * compound with the reasoning "our matcher drops those rules everywhere, so
 * they can't style a child component either — the merge loses nothing".
 * That reasoning is only half true, and the missing half is the whole bug:
 * dropping the RULE is fine, but the merge ALSO deletes the ELEMENT. For
 *
 *     span[hidden] { … }        b:hover { … }
 *
 * the `<span>` / `<b>` under test carries no attribute the guard notices,
 * so isPureInlineMergeable absorbs it into its parent's `_text` and the box
 * ceases to exist. After that NO channel can deliver those declarations —
 * not the static matcher, not the post-load overlay, not the attr bake —
 * because there is no component left to deliver them to. The rule was
 * dropped AND its subject was deleted.
 *
 * So an unsupported rightmost compound now contributes its TAG, when the tag
 * is identifiable. Two sources, in order:
 *   1. `parsed.needTag` — parseCompound walks the compound left-to-right and
 *      records the type selector BEFORE it hits whatever it refuses, so
 *      `b:hover` already arrives here with needTag `b`.
 *   2. the compound's LEADING type selector, for the early-outs that return
 *      before the walk starts — the `compound.includes('[')` bail, which is
 *      exactly the `span[hidden]` shape.
 * Nothing wider: the same asymmetry the A2b fallback names applies
 * (over-protecting keeps a correctly-placed unstyled box, under-protecting
 * deletes the box under test), but the A2b raw scan harvests EVERY bare
 * identifier in the selector text, which here would pull ancestor tags
 * (`div span[hidden]` → `div`) and functional-pseudo arguments into the
 * guard for no measured benefit.
 *
 * MEASURED COST, stated rather than implied (wave-33 lane N-finish, the
 * HEAD-vs-patched A/B; the frozen wave32-final gate caps every section at 12
 * tests and does NOT contain these, so each number is a fresh HEAD-side run
 * at the same cap). Corpus-wide this guard changes exactly 3 of the 10681
 * bucket-A tests, none of which loses text or a component — but it is NOT a
 * uniform win on the ref:
 *
 *   selectors-4/lang-021              0.9999 patched, wptPass (no HEAD run)
 *   css-text/letter-spacing-204       0.7554 → 0.7554   (unmoved)
 *   CSS2/selector/lang-pseudoclass-001 0.7774 → 0.7684  (−0.0090, REGRESSED)
 *
 * The regression is not the guard misfiring — the `<em>` it saves is the
 * rule's subject and MUST survive. It is the cost of the split itself, and
 * the cause is upstream of this function: `<p>…green <em>…</em></p>` splits
 * into parent `_text` + child, and the boundary SPACE is dropped, because
 * extractOwnText trims and the wave-32 lane R `_runs` list — the one channel
 * that preserves inter-run spaces ("the quick <u>brown</u> fox must keep the
 * space before AND after the child") — is emitted ONLY when the reorder
 * fired. Text-then-child never reorders, so no `_runs`, so "green" and "and"
 * render welded. HEAD hit the identical trim on every ALREADY-guarded inline
 * child; this lane only routes two more tests onto that pre-existing path.
 * Fixing it means widening the `_runs` emission condition, which is the
 * lane-R wire contract, not this guard — recorded as the follow-up rather
 * than smuggled in at the end of a lane. Do NOT "fix" it by reverting the
 * guard: that trades a 0.009 SSIM dent for a deleted element.
 *
 * wave-30 A2b — the MALFORMED-CHAIN fallback. When splitSelectorChain
 * returns null the selector could not be tokenised at all, so we cannot say
 * which compound is the rule's host; the pre-A2b code then contributed
 * NOTHING and the merge went ahead unguarded. That is the asymmetric-cost
 * case: keeping a `<span>` as its own component costs an extra (correctly
 * placed, unstyled) box, while merging it away can delete the very element
 * the rule was written to style. So we raw-scan the selector text for bare
 * tag names and over-protect. The RAW SCAN stays scoped to the null-chain
 * path on purpose — a chain that DID tokenise has an identified host
 * compound, so harvesting every bare identifier out of its selector text
 * would guard ancestor tags and pseudo arguments for nothing.
 *
 * This paragraph used to reject the NARROWER move too, warning that "widening
 * the guard to every parseCompound-unsupported rule would keep spans out of
 * the inline merge corpus-wide (a real layout change: an unmerged inline
 * element becomes a stacked block component)". wave-33 lane N made exactly
 * that move for the RIGHTMOST compound (the N2 note above) and MEASURED the
 * feared cost instead of arguing it: 3 of 10681 bucket-A tests change at all,
 * none loses text or a component, and the layout change is real on exactly
 * one of them (−0.0090 SSIM, see the N2 table). "Corpus-wide" was the right
 * mechanism and the wrong magnitude — the sentence is kept, corrected, rather
 * than quietly deleted, so the next lane inherits the measurement and not the
 * fear.
 *
 * Exported so the unit tests can pin the guard.
 */
export function collectStyledTags(rules) {
  const out = new Set();
  for (const r of rules) {
    // Tokenise the selector into compounds; null = malformed chain — we
    // cannot identify the host compound, so fall back to the raw scan.
    const chain = splitSelectorChain(r.selector);
    if (!chain || chain.compounds.length === 0) {
      for (const t of rawScanSelectorTags(r.selector)) out.add(t);
      continue;
    }
    // Only the rightmost compound identifies the rule's HOST element.
    const rightmost = chain.compounds[chain.compounds.length - 1];
    const parsed = parseCompound(rightmost);
    if (parsed.unsupported) {
      // wave-33 lane N (N2): the rule is unmatchable, but its SUBJECT must
      // still survive the inline merge — see the doc comment above.
      const tag = parsed.needTag ?? leadingTypeSelector(rightmost);
      if (tag && tag !== '*') out.add(tag);
      continue;
    }
    // Record the concrete host tag; '*' is excluded (see doc comment).
    if (parsed.needTag && parsed.needTag !== '*') out.add(parsed.needTag);
  }
  return out;
}

/**
 * wave-33 lane N (N2): the LEADING type selector of a compound, lower-cased,
 * or null when the compound does not start with one. Per Selectors-4 §5.1 a
 * type selector may only appear at the START of a compound, so anchoring at
 * index 0 is the complete rule — `span[hidden]` → `span`, `.note` → null,
 * `*[hidden]` → null, `#x` → null. Deliberately NOT a scan: see the guard
 * hole note in collectStyledTags for why the A2b raw scan is too wide here.
 *
 * Exported so the unit tests can pin the extraction rule.
 */
export function leadingTypeSelector(compound) {
  if (typeof compound !== 'string') return null;
  const m = /^([A-Za-z][A-Za-z0-9-]*)/.exec(compound);
  return m ? m[1].toLowerCase() : null;
}

/**
 * wave-30 A2b: last-resort tag harvest from a selector our tokeniser could
 * not decompose. Pulls every bare tag-name token — an identifier NOT
 * preceded by `.`, `#`, `:`, `-`, `[`, `=`, `"`, `'` or a word character, so
 * class names (`.note`), ids (`#x`), pseudo names (`:first-child`, whose
 * `first` and `child` are joined by `-`), attribute names/values and
 * `An+B`-style arguments never leak in — and lower-cases them to match
 * parseCompound's `needTag` normalisation.
 *
 * Over-collection is the intended failure mode (see collectStyledTags): a tag
 * that never actually hosts a rule merely keeps its element out of the inline
 * merge, whereas under-collection deletes a styled element. Only INLINE_MERGE
 * tags can be affected at all, since the guard is consulted nowhere else.
 */
function rawScanSelectorTags(selector) {
  const out = [];
  if (typeof selector !== 'string') return out;
  // [^\w.#:\-[="'] as the preceding character, or start-of-string.
  const re = /(^|[^\w.#:[\-="'])([A-Za-z][A-Za-z0-9]*)/g;
  let m;
  while ((m = re.exec(selector)) !== null) out.push(m[2].toLowerCase());
  return out;
}

/**
 * Is this child element a pure-inline run the parent may absorb into its
 * `_text`? All four conditions must hold:
 *   1. tag is one of the phrase-content INLINE_MERGE_TAGS;
 *   2. content is TEXT-ONLY — no '<' in innerHtml means no nested elements
 *      (comments were already stripped upstream by stripComments);
 *   3. NO attributes at all — an id/class/style (or even dir/title) marks
 *      the element as an addressable/styleable subject, not anonymous prose;
 *   4. no CSS rule targets the tag directly (styledTags guard, see
 *      collectStyledTags) — `strong { color: red }` keeps <strong> a
 *      component so the declaration under test survives.
 *
 * Exported so the unit tests can pin the predicate.
 */
export function isPureInlineMergeable(tag, attrs, innerHtml, styledTags = null) {
  // 1. Only phrase-content tags ever merge; blocks keep the child path.
  if (!INLINE_MERGE_TAGS.has(tag)) return false;
  // 2. Nested elements (`<strong><span>x</span></strong>`) keep the child
  //    path — merging would need recursive flattening + would hide real
  //    structure from the renderer.
  if (innerHtml && innerHtml.includes('<')) return false;
  // 3. Any attribute disqualifies: id/class/style are style hooks, dir
  //    flips bidi, title etc. are rare enough that conservatism is free.
  if (attrs && Object.keys(attrs).length > 0) return false;
  // 4. Tag directly targeted by a rule — the declarations must land, so
  //    the element must exist as a component to receive them.
  if (styledTags && styledTags.has(tag)) return false;
  return true;
}

// ── wave-22 EX2 B-RC4a: the scoped inline-chain collapse ────────────────────
//
// MEASURED PROBLEM (runs/wave21-final/sections/css-text-decor):
//   css-text-decor/text-decoration-color.html wraps ONE sentence in three
//   nested styled spans —
//     <div><span id=blue-underline><span id=gray-overline>
//          <span id=green-line-through>…text…</span></span></span></div>
//   Each span carries an `id`, so wave-12's isPureInlineMergeable refuses
//   them (rule 3: any attribute disqualifies) and the extractor emits FOUR
//   stacked block components — one empty div plus three ever-narrower
//   boxes, only the innermost holding text. The ref paints ONE line with
//   three decoration lines over it. Result: web-ref 0.708, ios-ref 0.495,
//   android-ref 0.493 — the worst trio in the section.
//   text-decoration-inset-001/002 (`<h1>the quick <u>brown</u> fox</h1>`)
//   is the same disease in its other form: the <u> is styled so it stays a
//   component, h1's own text glues to 'the quick fox', and the wave-21
//   B-RC9a honesty flag 'inline-run-reordered' fires because our paint
//   order (quick→fox→brown) is not the browser's (quick→brown→fox).
//
// THE COLLAPSE: when EVERY element in a node's subtree is a decoration-only
// inline wrapper, the whole subtree is ONE inline formatting run — CSS
// Text §3 / CSS Text Decoration 3 §1.3 ("decorating box"): the run's text
// is a single sequence and each ancestor's decoration lines all paint over
// the text they contain. So we flatten the subtree to ONE text run (in
// document order — no reorder, hence the B-RC9a flag is cleared) and hoist
// the decorations onto `_decorations`.
//
// WIRE CONTRACT (`_decorations` → IR v2 `meta.decorations`; the extension
// point spec/04-metadata-fields.md sanctions, same lane as wave-20's
// `_attrs` → `meta.attrs`) — the MINIMAL shape, consumed by lane DECOR:
//
//   _decorations: [ { line: "underline", color: "blue" },
//                   { line: "overline",  color: "gray" },
//                   { line: "line-through", color: "green" } ]
//
//   * ORDER is outermost-first (the collapse root, then each wrapper from
//     outside in) — the paint order CSS Text Decoration 3 §1.3 gives.
//   * `line` is exactly ONE line keyword; an element declaring two
//     (`text-decoration: underline overline`) contributes two entries with
//     the same color, so consumers never have to re-tokenise.
//   * `color` is the CSS colour TOKEN as authored, and it STAYS authored
//     all the way to the runtimes. The converter does NOT normalise it to
//     the IR sRGB leaf the way it does for a real `text-decoration-color`
//     DECLARATION: `meta` members are extractor-owned payloads forwarded
//     verbatim (the wave-20 `meta.attrs` precedent), so the converter
//     never interprets this object. Each runtime resolves the token with
//     its own CSS token parser at decode time — `CSSTokenParser.color` on
//     iOS, `ValueExtractors.parseCssColorLiteral` on Compose (both via
//     the DecorationWire twins), and the browser itself on web, where the
//     token goes straight back into a `text-decoration-color`
//     declaration. Rationale + the known parser-coverage gap:
//     schema/spec/04-metadata-fields.md.
//     Omitted when the run resolves to `currentColor`, which is the CSS
//     initial value (css-text-decor-3 §2.2) — absence means "use the text
//     colour", it is never a silent drop.
//   * AUTHORITATIVE when present: `meta.decorations` is the complete set of
//     lines for the run. The flat `text-decoration*` longhands that survive
//     on the component are the merged bag old readers already understood
//     (see the root-wins merge below), so a reader that ignores the key
//     paints a SUBSET, never a superset — no double-painting either way.
//
// The tag list is deliberately TINY. These six are the inline wrappers
// whose only visual contribution IS a text decoration, so flattening the
// run cannot lose a font/weight/baseline effect. em/strong/b/i (weight and
// slant), small/big/code (font), sub/sup (baseline), a/abbr/mark (UA
// chrome) are all excluded: a chain containing one keeps today's shape.
export const INLINE_CHAIN_TAGS = new Set([
  'span', 'u', 's', 'strike', 'ins', 'del',
]);

// UA decoration defaults, HTML Rendering §15.3.6 ("Phrasing content"):
//   u, ins { text-decoration: underline }
//   s, strike, del { text-decoration: line-through }
// The extractor models no UA stylesheet, so without this table a
// `<u>`-wrapped run carries text-decoration-COLOR (authored) and no LINE
// at all — which is precisely why text-decoration-inset-001 renders 'brown'
// with zero underline today. Used ONLY inside the collapse (see the
// uaDerived flag below), so no non-collapsed component's flat bag changes.
const UA_DECORATION_LINE = {
  u: 'underline', ins: 'underline',
  s: 'line-through', strike: 'line-through', del: 'line-through',
};

// css-text-decor-3 §2.1 `text-decoration-line` keywords we model. `blink`
// is in the grammar but paints nothing static; `none` is handled as the
// explicit "this element contributes no line" answer, not as a keyword.
const DECORATION_LINE_KEYWORDS = new Set(['underline', 'overline', 'line-through']);

// css-text-decor-3 §2.3 `text-decoration-style` keywords — recognised only
// so the shorthand tokeniser can tell a style token from a COLOUR token.
const DECORATION_STYLE_KEYWORDS = new Set(['solid', 'double', 'dotted', 'dashed', 'wavy']);

// The property allow-list a chain LINK may declare. Everything here is
// text-decoration state that the collapse either hoists into
// `_decorations` or folds into the collapsed component's flat bag; a link
// declaring ANY other property (a colour, a font, a box property) refuses
// the collapse, which is what keeps the merged flat bag exactly equivalent
// to the pre-collapse per-component bags. Includes the -webkit- alias
// because the corpus authors it beside the standard one (dotted-001/002).
const DECORATION_FAMILY_PROPS = new Set([
  'text-decoration', '-webkit-text-decoration',
  'text-decoration-line', 'text-decoration-color', 'text-decoration-style',
  'text-decoration-thickness', 'text-decoration-inset', 'text-decoration-skip',
  'text-decoration-skip-ink', 'text-underline-offset', 'text-underline-position',
]);

// Attributes a chain link may carry. id/class/style are selector fuel that
// propsForElement has ALREADY consumed into the resolved bag we check, and
// lang/title are non-visual. Anything else (notably `dir`, which flips
// bidi, and any presentational attribute) refuses the collapse.
const INLINE_CHAIN_ALLOWED_ATTRS = new Set(['id', 'class', 'style', 'lang', 'title']);

// UA heading metrics, HTML Rendering §15.3.7. Baked to PIXELS against the
// 16px root default the browser-ref pins (capture-browser-ref pins
// font-family + line-height on html/body but leaves font-size at the UA
// 16px), because an `em` value would land in the IR as `null` (the
// runtime-dependent rule in schema/spec/02-values.md) and paint nothing.
//   h1 { font-size: 2em; margin-block: 0.67em; font-weight: bold }
// → 32px font, 0.67 × 32px = 21.44px block margins.
const UA_H1_PROPS = {
  'font-size': '32px', 'font-weight': 'bold',
  'margin-top': '21.44px', 'margin-bottom': '21.44px',
};
// Guard keys: if the collapse root already declares any of these, the UA
// default is not the used value and we must not invent one.
const UA_H1_GUARD_PROPS = [
  'font-size', 'font', 'font-weight', 'margin', 'margin-top', 'margin-bottom',
  'margin-block', 'margin-block-start', 'margin-block-end',
];

/**
 * Paren-aware top-level whitespace tokeniser for a shorthand value, so
 * `dotted rgb(255, 0, 0) underline` yields three tokens instead of five.
 * (splitCompounds does the same job for selectors; kept separate because
 * that one also has to honour selector syntax.)
 */
function splitValueTokens(value) {
  const out = [];
  let buf = '';
  let depth = 0;
  for (const c of String(value)) {
    if (c === '(') { depth++; buf += c; continue; }
    if (c === ')') { depth--; buf += c; continue; }
    if (/[\s,]/.test(c) && depth === 0) { if (buf) { out.push(buf); buf = ''; } continue; }
    buf += c;
  }
  if (buf) out.push(buf);
  return out;
}

/**
 * Resolve ONE element's contribution to a collapsed run's decorations.
 * Returns `{ lines: string[], color: string|null, uaDerived: boolean }`.
 *
 * Cascade order mirrors css-text-decor-3 §2: the `text-decoration-line`
 * longhand wins over the `text-decoration` shorthand's line component (the
 * props bag is already cascade-ordered by propsForElement, but a bag may
 * legitimately hold both, and the longhand is the more specific answer);
 * `text-decoration-color` likewise wins over the shorthand's colour.
 * `none` is an explicit answer meaning "this element contributes nothing"
 * — per §2.1 it never removes an ANCESTOR's decoration, so we simply emit
 * no entry for this element rather than clearing the list.
 *
 * `uaDerived` marks a line that came from UA_DECORATION_LINE rather than
 * an authored declaration — the caller keeps those OUT of the flat
 * property bag (see the fold below) so no non-collapsed rendering path
 * gains a line the pre-wave-22 fixtures never carried.
 *
 * Exported so the unit tests can pin the contract.
 */
export function decorationContribution(tag, props) {
  // Shorthand tokens, if any — used as the fallback source for both the
  // line list and the colour.
  const shorthand = props['text-decoration'] ?? props['-webkit-text-decoration'];
  const shTokens = shorthand ? splitValueTokens(shorthand).map((t) => t.toLowerCase()) : [];
  // Line list: longhand first, else the shorthand's line keywords.
  const lineSrc = props['text-decoration-line'];
  let lines = [];
  let explicit = false;
  if (typeof lineSrc === 'string') {
    explicit = true;
    lines = splitValueTokens(lineSrc)
      .map((t) => t.toLowerCase())
      .filter((t) => DECORATION_LINE_KEYWORDS.has(t));
  } else if (shTokens.length) {
    // A shorthand is present: it always sets the line component (to `none`
    // when no keyword appears), so this element's answer is explicit.
    explicit = true;
    lines = shTokens.filter((t) => DECORATION_LINE_KEYWORDS.has(t));
  }
  // No authored line anywhere → fall back to the UA sheet for u/s/ins/del.
  let uaDerived = false;
  if (!explicit && UA_DECORATION_LINE[tag]) {
    lines = [UA_DECORATION_LINE[tag]];
    uaDerived = true;
  }
  // Colour: longhand wins; else the shorthand's non-line, non-style,
  // non-thickness token (css-text-decor-3 §2.5 orders the shorthand's
  // components freely, so identify the colour by elimination).
  let color = props['text-decoration-color'] ?? null;
  if (!color && shTokens.length) {
    const raw = shorthand ? splitValueTokens(shorthand) : [];
    for (let i = 0; i < raw.length; i++) {
      const t = shTokens[i];
      if (DECORATION_LINE_KEYWORDS.has(t) || DECORATION_STYLE_KEYWORDS.has(t)) continue;
      if (t === 'none' || t === 'blink') continue;
      // A bare number/length is the (css-text-decor-4) thickness slot.
      if (/^-?[\d.]/.test(t) || t === 'auto' || t === 'from-font') continue;
      color = raw[i]; // preserve the AUTHORED casing/spacing of the token
      break;
    }
  }
  return { lines, color, uaDerived };
}

/**
 * Flatten an HTML fragment to its text content, tags removed, in document
 * order. Used to build a collapsed run's single text string (and, per
 * element, to decide whether that element's decoration covers the WHOLE
 * run or only part of it).
 *
 * The decode-then-white-space boundary is the SAME one scanOwnText
 * documents at length: character references decode after tag/text
 * separation and before the CSS Text §4.1 ASCII-only collapse, which is
 * the HTML-tokenizer-then-CSS order a browser applies. `preserveWhitespace`
 * mirrors the pre-family carve-out of every other scanner in this file.
 *
 * Exported so the unit tests can pin the contract.
 */
export function flattenInlineText(html, preserveWhitespace = false) {
  if (!html) return '';
  let buf = '';
  let i = 0;
  const n = html.length;
  while (i < n) {
    // Text region — everything up to the next '<' is content.
    if (html[i] !== '<') {
      const next = html.indexOf('<', i);
      const end = next < 0 ? n : next;
      buf += html.slice(i, end);
      i = end;
      continue;
    }
    // Any markup token (open tag, close tag, comment, PI) contributes no
    // text: skip to its '>' . Comments were stripped upstream; handling
    // them here keeps the helper safe for direct unit-test calls.
    const close = html.indexOf('>', i);
    i = close < 0 ? n : close + 1;
  }
  const decoded = decodeCharacterReferences(buf);
  if (preserveWhitespace) return decoded;
  return decoded
    .replace(/[ \t\n\r\f]+/g, ' ')
    .replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, '');
}

/**
 * Strip an element's own open/close tags from its `raw` markup, yielding
 * the inner markup. walkChildren keeps `raw` (outer) on every node but not
 * `innerHtml`, and the collapse needs the inner form for its
 * "did the walker actually see every descendant?" guard.
 */
function innerMarkupOf(raw) {
  if (!raw) return '';
  return String(raw)
    .replace(/^<[^>]*>/, '')
    .replace(/<\/[A-Za-z][A-Za-z0-9-]*\s*>$/, '');
}

/**
 * Decide whether a nested-tree node's subtree is a collapsible inline run,
 * and if so compute everything buildComponents needs.
 *
 * `node` is the freshly built NestedNode (children already recursed);
 * `innerHtml` is the fragment the walker read it from; `rootProps` is the
 * node's own resolved property bag; `resolveProps(tag, attrs, ancestors,
 * pos)` returns propsForElement's FULL `{props, pseudo}` result for a
 * descendant — the SAME function the component builder uses, so the two
 * can never disagree about what a wrapper declares.
 *
 * Returns null when the collapse does not apply — every guard below is a
 * refusal, never a silent approximation, so anything not matching the
 * narrow shape keeps its pre-wave-22 fixture bytes exactly.
 *
 * Exported so the unit tests can pin the predicate.
 */
export function collapseInlineRun(node, innerHtml, rootProps, resolveProps, preserveWhitespace = false) {
  // Production-only: legacy walkers/tests call extractBodyTreeNested with
  // no resolver and keep the wave-21 shape byte-for-byte.
  if (typeof resolveProps !== 'function') return null;
  // Nothing nested → nothing to collapse (the wave-12 merge already
  // handled the flat `foo<span>bar</span>baz` case).
  if (!node.children || node.children.length === 0) return null;
  // Walk the subtree in document order (pre-order DFS = outermost-first,
  // which IS the decoration paint order). Any refusal aborts the whole
  // collapse — partial collapses would lose declarations silently.
  const links = [];
  let ok = true;
  const visit = (d) => {
    if (!ok) return;
    // 1. Tag must be a decoration-only inline wrapper (see INLINE_CHAIN_TAGS).
    if (!INLINE_CHAIN_TAGS.has(d.tag)) { ok = false; return; }
    // 2. Only non-visual / already-consumed attributes.
    for (const a of Object.keys(d.attrs ?? {})) {
      if (!INLINE_CHAIN_ALLOWED_ATTRS.has(a)) { ok = false; return; }
    }
    // 3. The walker must have SEEN the whole subtree: a leaf whose inner
    //    markup still contains '<' means either the maxDepth cut truncated
    //    it or a wave-12 merge absorbed a child — in both cases we cannot
    //    prove every descendant is a decoration-only wrapper, so refuse.
    //    EXCEPTION: a wrapper this same routine already collapsed one
    //    recursion level down (`chainCollapsed`). extractBodyTreeNested
    //    recurses depth-first, so `div > span#a > span#b > span#c` reaches
    //    span#a FIRST and empties its children — without this exception
    //    the div would then refuse and the outer link's decoration would
    //    be stranded on a 100x100 placeholder (the exact wave-21 shape of
    //    text-decoration-color.html's fourth block). The nested result is
    //    spliced in below instead of re-walking the vanished children.
    if (d.children.length === 0 && !d.chainCollapsed &&
        innerMarkupOf(d.raw).includes('<')) { ok = false; return; }
    // 4. The link may declare ONLY text-decoration state. This is the
    //    guard that makes the flat-bag fold below lossless: with no other
    //    property in play, merging the link bags into the root's cannot
    //    change any non-decoration rendering.
    const resolved = resolveProps(d.tag, d.attrs, d.ancestors, d.pos ?? null);
    const p = resolved.props ?? {};
    for (const key of Object.keys(p)) {
      if (!DECORATION_FAMILY_PROPS.has(key)) { ok = false; return; }
    }
    // 5. A wrapper carrying ::before/::after/::marker generated content is
    //    NOT a bare inline run — flattening would drop the content the
    //    test is about (CSS Generated Content L3 §3.2). Refuse.
    if (resolved.pseudo && Object.keys(resolved.pseudo).length > 0) { ok = false; return; }
    links.push({ node: d, props: p });
    // A wrapper the inner recursion already collapsed carries its whole
    // sub-chain's answer (`decorations` outermost-first, starting with its
    // OWN entry) plus the declarations it already folded. Splice those in
    // instead of walking children that no longer exist.
    if (d.chainCollapsed) {
      links[links.length - 1].preDecorations = d.decorations ?? [];
      links[links.length - 1].preExtras = d.collapsedProps ?? {};
      links[links.length - 1].prePartial = d.inlineChainCollapsed === true;
      return;
    }
    d.children.forEach(visit);
  };
  node.children.forEach(visit);
  if (!ok || links.length === 0) return null;
  // The run's single text string, in document order.
  const text = flattenInlineText(innerHtml, preserveWhitespace);
  if (!text.trim()) return null; // nothing visible to carry — keep today's shape
  // SCOPE GATE: only decoration-bearing runs collapse. A plain
  // `<div><span>x</span></div>` (no decoration anywhere) is left exactly as
  // it is, which is what keeps this change inside the css-text-decor blast
  // radius instead of rewriting every nested-span fixture in the corpus.
  const rootContrib = decorationContribution(node.tag, rootProps);
  const bearing = rootContrib.lines.length > 0 ||
    links.some((l) => Object.keys(l.props).length > 0 ||
                      (l.preDecorations?.length ?? 0) > 0 ||
                      decorationContribution(l.node.tag, l.props).lines.length > 0);
  if (!bearing) return null;
  // Build the outermost-first entry list. `covers` records whether the
  // decorating element contains the WHOLE run — when it does not, painting
  // its line over the whole run is an approximation and must be marked.
  const decorations = [];
  let partial = false;
  const pushEntry = (contrib, covers) => {
    for (const line of contrib.lines) {
      const entry = { line };
      // Omit the colour when it resolves to currentColor (the CSS initial
      // value) — absence is the documented "use the text colour" answer.
      const c = contrib.color ?? rootProps.color ?? null;
      if (c && String(c).trim().toLowerCase() !== 'currentcolor') entry.color = c;
      decorations.push(entry);
      if (!covers) partial = true;
    }
  };
  pushEntry(rootContrib, true); // the root contains the whole run by definition
  for (const l of links) {
    // Coverage is measured on the AUTHORED markup (`raw`), which survives
    // an inner collapse untouched — so this stays correct for spliced
    // sub-chains too. Recorded on the link because the flat-bag fold below
    // needs it as well (a UA line only folds when it is EXACT).
    const covers = flattenInlineText(innerMarkupOf(l.node.raw), preserveWhitespace) === text;
    l.covers = covers;
    if (l.preDecorations) {
      // Already-computed sub-chain: its entries are outermost-first and
      // already carry their colours. Re-stamping the colour here would
      // overwrite a descendant's own text-decoration-color with this
      // link's, so the entries pass through verbatim.
      for (const e of l.preDecorations) decorations.push({ ...e });
      // The sub-chain is an approximation if it already was one, or if it
      // covers only part of THIS (larger) run.
      if (l.prePartial || (!covers && l.preDecorations.length > 0)) partial = true;
      continue;
    }
    pushEntry(decorationContribution(l.node.tag, l.props), covers);
  }
  // Fold the links' AUTHORED decoration declarations into a flat bag the
  // pre-wave-22 readers still understand. Precedence is OUTERMOST-WINS all
  // the way down, which is the decorating-box rule of css-text-decor-3
  // §1.3: the outermost box that establishes the decoration owns it and a
  // descendant's value does not override it. Root first (never displaced —
  // exactly what decorating-box-thickness-001 asserts, the div's 10px
  // thickness beating the span's 1px), then each link in document order,
  // first writer wins. `_decorations` remains the authoritative full list;
  // this bag only exists so a reader that ignores the new key still paints
  // a SUBSET of the correct lines rather than nothing.
  const extraProps = {};
  // wave-22 EX2 SKEPTIC: the outermost-wins fold DROPS a link's declaration
  // whenever an outer box already wrote that key. For `text-decoration-line`
  // and `text-decoration-color` that loses nothing — `_decorations` carries
  // both per entry. But `_decorations` entries are `{line, color?}` ONLY, so
  // a dropped STYLE / THICKNESS / INSET / OFFSET has no channel at all and
  // vanished silently (measured on css/css-text-decor/
  // text-decoration-style-multiple.html: three nested spans declaring
  // `underline solid coral` / `overline dashed skyblue` / `line-through wavy
  // green` collapsed to a flat `underline solid coral` + three colourful
  // `_decorations` entries, with `dashed` and `wavy` — the whole subject of
  // that test — gone and `_lossyReasons: []`). That is exactly the silent
  // fallthrough the repo's hard rules forbid, so the drop is now LOUD.
  let declDropped = false;
  for (const l of links) {
    // Does THIS link establish a decoration of its own? A link that declares
    // no line (css-text-decor-3 §1.3's decorating box is then an ancestor)
    // contributes no decoration for its sub-properties to modify, so its
    // thickness/style are INERT and dropping them loses nothing — that is
    // precisely what decorating-box-thickness-001 asserts (the span's 1px
    // must not affect the div's underline). Only a line-bearing link's
    // dropped modifiers are a real loss.
    const dropContrib = l.preDecorations
      ? null : decorationContribution(l.node.tag, l.props);
    const linkBearsLine = l.preDecorations
      ? l.preDecorations.length > 0 : dropContrib.lines.length > 0;
    // Own bag first, then anything a spliced sub-chain had already folded
    // (which is by construction deeper, so it only fills remaining gaps).
    for (const [k, v] of Object.entries({ ...l.props, ...(l.preExtras ?? {}) })) {
      if (k in rootProps || k in extraProps) {
        // Displaced by an outer box. Loud only when the value carries
        // information `_decorations` cannot express AND actually differs
        // from the winner (an identical redeclaration loses nothing).
        const winner = (k in rootProps) ? rootProps[k] : extraProps[k];
        if (linkBearsLine && String(v) !== String(winner) &&
            carriesUnexpressibleDecoration(k, v)) declDropped = true;
        continue;
      }
      extraProps[k] = v;
    }
    // UA-derived lines (the `u`/`s`/`ins`/`del` defaults this file models
    // because it reads no UA stylesheet) fold into the flat bag ONLY when
    // the wrapper covers the WHOLE run — then the fold is EXACT and a
    // reader ignoring `_decorations` still paints the right line over the
    // right text (css-text-decor's text-decoration-color-recalc-002 is
    // `<p style="color:red"><s>…</s></p>`: the <s> IS the p's entire
    // content, so folding `line-through` reproduces the browser exactly).
    // A PARTIAL wrapper (`the quick <u>brown</u> fox`) is deliberately NOT
    // folded: widening its line to the whole run in the flat bag would be
    // a silent over-paint. It stays in `_decorations`, where lane DECOR
    // owns the decision, and the run carries the 'inline-chain-collapsed'
    // marker so the widening is never invisible.
    const ua = l.preDecorations ? null : decorationContribution(l.node.tag, l.props);
    if (ua?.uaDerived && l.covers && ua.lines.length > 0 &&
        !('text-decoration' in rootProps) && !('text-decoration-line' in rootProps) &&
        !('text-decoration' in extraProps) && !('text-decoration-line' in extraProps)) {
      extraProps['text-decoration-line'] = ua.lines.join(' ');
    }
  }
  // UA heading metrics: an <h1> collapse root renders at 2em/bold with
  // block margins in the ref, and the extractor models no UA sheet. Only
  // when the root declares none of the guarded properties itself.
  let uaHeading = null;
  if (node.tag === 'h1' && !UA_H1_GUARD_PROPS.some((k) => k in rootProps)) {
    uaHeading = { ...UA_H1_PROPS };
  }
  return { text, decorations, extraProps, uaHeading, partial, declDropped };
}

// wave-22 EX2 SKEPTIC: the css-text-decor-3 sub-properties that MODIFY an
// established decoration (§2.3 style, §2.4 thickness, §2.5/§3 offsets and
// skips). None of them has a slot in a `_decorations` entry (`{line, color}`),
// so when the outermost-wins fold displaces one it is gone from the fixture
// entirely — hence the loud marker. `text-decoration-line` / `-color` are
// deliberately ABSENT from this set: both survive per entry, so displacing
// them in the flat bag is the documented lossless subset, not a loss.
const DECORATION_MODIFIER_PROPS = new Set([
  'text-decoration-style', 'text-decoration-thickness', 'text-decoration-inset',
  'text-decoration-skip', 'text-decoration-skip-ink',
  'text-underline-offset', 'text-underline-position',
]);

/**
 * Does this displaced declaration carry decoration state `_decorations`
 * cannot express? True for the modifier longhands above, and for a
 * `text-decoration` shorthand whose token list holds a STYLE keyword
 * (css-text-decor-3 §2.3) or a css-text-decor-4 thickness token — the two
 * shorthand components with no entry field. A shorthand that is only
 * line + colour keywords is fully re-expressed by the entry list.
 *
 * Exported so the unit tests can pin the predicate.
 */
export function carriesUnexpressibleDecoration(key, value) {
  if (DECORATION_MODIFIER_PROPS.has(key)) return true;
  if (key !== 'text-decoration' && key !== '-webkit-text-decoration') return false;
  return splitValueTokens(value).some((t) => {
    const lt = t.toLowerCase();
    // Line keywords + the two no-paint keywords: expressible (or inert).
    if (DECORATION_LINE_KEYWORDS.has(lt) || lt === 'none' || lt === 'blink') return false;
    // Style keyword — no entry field.
    if (DECORATION_STYLE_KEYWORDS.has(lt)) return true;
    // Thickness slot (a length/number, `auto`, or `from-font`) — no field.
    if (/^-?[\d.]/.test(lt) || lt === 'auto' || lt === 'from-font') return true;
    // Anything else is the colour token, which every entry carries.
    return false;
  });
}

/**
 * wave-12 EXTRACTOR-INLINE: the merge-aware sibling of extractOwnText.
 * Returns `{ text, merged }` where `text` is the element's own text WITH
 * every pure-inline child's content spliced in at its reading-order
 * position, and `merged` counts how many inline runs were absorbed
 * (drives the 'inline-run-merged' lossy marker in buildComponents).
 *
 * `mergeCtx` is `{ styledTags?: Set<string> }` (see collectStyledTags);
 * pass null to disable merging entirely (identical to extractOwnText).
 *
 * Pin: the standard WPT preamble innerHtml
 *   'Test passes if there is a filled green square and <strong>no red</strong>.'
 * merges to the exact single string
 *   'Test passes if there is a filled green square and no red.'
 *
 * Exported so the unit tests can pin the contract.
 */
export function extractOwnTextMerged(innerHtml, mergeCtx = null, preserveWhitespace = false) {
  // Full result object — callers need both the text and the merge count.
  // wave-15: `preserveWhitespace` (default false — legacy callers keep the
  // collapse) is threaded through for elements whose resolved white-space
  // is in the pre family (see WHITESPACE_PRESERVING + scanOwnText docs).
  return scanOwnText(innerHtml, mergeCtx, preserveWhitespace);
}

// ── wave-15 BIDI-EXTRACT part 1: character-reference decoding ────────────────
//
// css-text/bidi/bidi-tab-001 root cause: the source markup is
// `<span dir=ltr>&#9;0</span>` and the extractor copied the LITERAL 6-char
// string '&#9;0' into `_text` — every runtime then painted an ampersand
// instead of a TAB, so the tab-vs-bidi interaction under test never existed
// in the fixture at all. Per HTML §13.2.5 (character reference states), the
// HTML tokenizer decodes references while producing TEXT tokens, i.e.
// BEFORE CSS ever sees the characters; our scanner walks raw markup, so we
// must decode at the equivalent boundary ourselves.
//
// WHERE THE BOUNDARY SITS (no double-decode, no re-parse): decoding is
// applied ONCE, inside the two text scanners (scanOwnText /
// extractLeadingBodyTextInfo), to the accumulated TEXT buffer — i.e. AFTER
// tag/text separation is settled (a decoded '<' from `&lt;` can never be
// mistaken for markup because the scanner has already consumed all tags)
// and BEFORE the CSS white-space collapse (a decoded TAB is collapsible
// white space under `white-space: normal` per CSS Text §4.1, and survives
// under the pre family — exactly the browser pipeline order). Decoding is a
// single left-to-right String.replace pass that never rescans its own
// output, so `&amp;#9;` decodes to the LITERAL '&#9;' — the same answer a
// real HTML tokenizer gives.

// The named character references we decode. HTML defines ~2200 named refs
// (all case-sensitive); the WPT corpus uses a small stable subset — the XML
// core five plus the whitespace/bidi-control/punctuation names below. Names
// NOT in this map are left verbatim (conservative: an unknown `&foo;` stays
// visible in the capture instead of silently vanishing).
const NAMED_CHARACTER_REFS = {
  // XML core five — the escaping set every HTML author uses.
  amp: '&', lt: '<', gt: '>', quot: '"', apos: "'",
  // Non-ASCII spaces — preserved verbatim by the §4.1 ASCII-only collapse,
  // so decoding them here is what makes NBSP-family tests honest.
  nbsp: '\u00A0', ensp: '\u2002', emsp: '\u2003', thinsp: '\u2009',
  // Bidi controls + joiners — load-bearing for the css-text bidi family.
  // All zero-width/invisible characters, hence escapes not literals.
  zwnj: '\u200C', zwj: '\u200D', lrm: '\u200E', rlm: '\u200F', shy: '\u00AD',
  // Common punctuation/symbol names seen across WPT instruction prose.
  mdash: '—', ndash: '–', hellip: '…', middot: '·',
  bull: '•', laquo: '«', raquo: '»', copy: '©',
  times: '×', minus: '−',
  rarr: '→', larr: '←', uarr: '↑', darr: '↓',
  // wave-29 S-RC2 — the two ASCII-whitespace names. HTML's named-reference
  // table spells them with capitals (`&NewLine;` = U+000A LINE FEED,
  // `&Tab;` = U+0009 CHARACTER TABULATION) and the lookup below is
  // case-sensitive by design, so these keys must carry that exact casing.
  // Load-bearing for css-pseudo/active-selection-057, whose third subtest is
  // `<div id="subtest3">&NewLine;&NewLine;</div>` under `white-space: pre`:
  // the test asserts that div paints nothing because both references are
  // line-break CONTROL characters, not glyphs. Undecoded, `_text` carried
  // the literal 18-character string '&NewLine;&NewLine;' and every runtime
  // painted ampersand prose at font-size:100px — the exact opposite of the
  // "nothing should be painted or viewable" assertion. Decoded, the
  // pre-family preserve path (WHITESPACE_PRESERVING) keeps both newlines
  // verbatim and the div becomes the empty two-line box the browser draws.
  // `&Tab;` rides along as the same-class name for U+0009: the numeric
  // `&#9;` spelling is already decoded (wave-15 bidi-tab-001), so omitting
  // its named twin would be an arbitrary split of one tokenizer rule.
  NewLine: '\n', Tab: '\t',
};

/**
 * Decode numeric (`&#9;` / `&#x41;`) and known named (`&amp;` …) character
 * references in a TEXT-ONLY string (markup already stripped by the caller).
 *
 * Numeric refs outside Unicode (> U+10FFFF), the NUL code point, and lone
 * surrogates decode to U+FFFD REPLACEMENT CHARACTER, mirroring the HTML
 * spec's numeric-character-reference-end error handling (§13.2.5.80) —
 * String.fromCodePoint would throw on them otherwise. The HTML C1-control
 * remap table (e.g. `&#150;` → U+2013) is NOT implemented: the corpus
 * never uses C1 numeric refs, and a raw C1 control renders invisibly on
 * every platform anyway — documented gap, not a silent one.
 *
 * Exported so the unit tests can pin the contract (bidi-tab-001's
 * '&#9;0' → TAB + '0' is the load-bearing pin).
 */
export function decodeCharacterReferences(text) {
  // Fast path: no ampersand means no reference — return the same string.
  if (!text || !text.includes('&')) return text;
  // One alternation, one pass: hex numeric | decimal numeric | named.
  // Replacements are never rescanned, so `&amp;#9;` → literal '&#9;'.
  return text.replace(
    /&(?:#[xX]([0-9a-fA-F]+)|#([0-9]+)|([a-zA-Z][a-zA-Z0-9]*));/g,
    (whole, hex, dec, name) => {
      if (hex !== undefined || dec !== undefined) {
        // Numeric reference — parse in the matching radix.
        const cp = hex !== undefined ? parseInt(hex, 16) : parseInt(dec, 10);
        // Spec-shaped error handling: NUL, out-of-range, and surrogate
        // code points become U+FFFD (see doc comment).
        if (!Number.isFinite(cp) || cp === 0 || cp > 0x10FFFF ||
            (cp >= 0xD800 && cp <= 0xDFFF)) return '\uFFFD';
        return String.fromCodePoint(cp);
      }
      // Named reference — exact (case-sensitive) match against our subset;
      // unknown names stay verbatim (conservative, see map comment).
      const mapped = NAMED_CHARACTER_REFS[name];
      return mapped !== undefined ? mapped : whole;
    },
  );
}

// ── wave-15 BIDI-EXTRACT part 3: the pre family ──────────────────────────────
//
// `white-space` values whose CSS Text §4.1.1 "Phase I" rules PRESERVE
// segment breaks and preserve spaces/tabs — text under these must NOT go
// through the collapse at the bottom of the scanners. `pre-line` is
// deliberately absent: it preserves newlines but still collapses
// spaces/tabs, which our binary collapse/preserve switch cannot express —
// pre-line text takes the collapse path (documented approximation; zero
// pre-line uses in the current css-text bidi family).
const WHITESPACE_PRESERVING = new Set(['pre', 'pre-wrap', 'break-spaces']);

/**
 * Shared own-text scanner behind extractOwnText (mergeCtx = null) and
 * extractOwnTextMerged (mergeCtx = { styledTags }). One walker, one
 * whitespace rule, so the two contracts can never drift. Returns
 * `{ text, merged }`.
 *
 * wave-15: `preserveWhitespace` (default false — every legacy caller keeps
 * the collapse) skips the §4.1 collapse entirely for elements whose
 * resolved `white-space` is in the pre family (see WHITESPACE_PRESERVING):
 * spaces, tabs, and newlines in the source text — INCLUDING decoded
 * `&#9;`-style references — reach `_text` verbatim. Caveat carried from
 * HTML §13.2.5: the spec drops ONE newline immediately after a `<pre>`
 * open tag; we don't (the corpus styles `white-space: pre` on <div>s,
 * where no such rule exists).
 */
function scanOwnText(innerHtml, mergeCtx, preserveWhitespace = false) {
  // Merge counter — how many pure-inline children were absorbed.
  let merged = 0;
  // wave-21 B-RC9a REORDER HONESTY: `reordered` flags the case where the
  // element's own text GLUES ACROSS a child that stays a component — e.g.
  // 'the quick <u>brown</u> fox' when <u> is styled/non-mergeable: `_text`
  // becomes 'the quick fox' and the <u> child renders as a SEPARATE block
  // after it, so the reading order the browser paints (quick→brown→fox) is
  // NOT the order our renderers paint (quick→fox→brown). That is a lossy
  // approximation and must be marked ('inline-run-reordered' downstream) —
  // it previously shipped with lossyReasons []. The full fix (ordered
  // inline-run splitting on the wire) is a v3 byte-shape change; the flag
  // keeps the gate honest until then (documented follow-up).
  //
  // ── wave-31 lane S: MEASURED, and the bail is deliberate ────────────────
  //
  // WHERE THE ORDER IS LOST: nowhere in this scanner — it is lost at the
  // WIRE. `_text` is a single string field on the component and every
  // renderer paints it BEFORE the children array (web's NodeRenderer says
  // so at its mixed-content branch: "text renders BEFORE the children (the
  // wire has no interleaved inline-runs shape yet)"; Compose and SwiftUI
  // do the same). So the moment a non-mergeable child sits BETWEEN two
  // text nodes, the fixture can only express run+child (order kept) or
  // child+run (order lost) — never run/child/run. This scanner does the
  // only honest thing available: it concatenates in reading order and
  // says so.
  //
  // CANONICAL VICTIM (the wave-31 CSS2 unblocker):
  //   CSS2/abspos/static-inside-inline-001 —
  //     <span id=inline><div id=abspos></div> X </span>
  //   The div is styled (`#abspos`), so it survives as a child; 'X' lands
  //   in the span's `_text` and paints FIRST. The test asserts the abspos'
  //   STATIC POSITION (§10.6.4): with the div first the preceding inline
  //   fragment is empty → zero-height line box (§9.4.2) → top = 0; with
  //   'X' first the fragment carries text → 100px line → top = 100. Our
  //   order flips the very quantity under test. (-002 wants the 100px
  //   answer and is accidentally right; -003 is -001 with a border.)
  //
  // BLAST SET, enumerated on the committed corpus (1838 tests re-extracted,
  // probe _diag31/lane-s): 1203 components across 472 fixture files in 14
  // spec sections carry this flag — css-lists 37, css-content 29,
  // css-contain 28, css-writing-modes 25, css-text-decor 23, css-pseudo 17,
  // css-align 9, css-backgrounds 8, … Child-shape histogram: 163 span,
  // 139 bdi, 91 li·li·li, 82 rt×5, 81 br, 72 div. These are REAL in-flow
  // reorders (ruby annotations, bdi runs, list items) — not a corner.
  //
  // WHY NO FIX HERE (the bounded-fix probe, and why it failed):
  //   The obvious bounded shape is "split the run at the non-mergeable
  //   child": emit the trailing text as a synthesized anonymous LAST child
  //   so document order survives as run/child/run siblings. It is bounded
  //   in the EXTRACTOR and unbounded everywhere else —
  //     • it invents components, so every id-keyed downstream artifact
  //       moves (screenshot names, manifest keyMaps, per-test-ir);
  //     • the synthesized child is a childless text component, which every
  //       renderer paints through its PLACEHOLDER path — a `display:block`
  //       span on web, a labelled box on both natives. A block box inside
  //       an inline box splits it (CSS 2.1 §9.2.1.1), so the split would
  //       re-break the very line box it was meant to preserve unless all
  //       THREE renderers first learn an inline anonymous-text-run box.
  //       That is a renderer-contract change on three platforms, i.e. the
  //       same v3 wire work wave-21 deferred, wearing a disguise.
  //   Narrowing to "the preceding kept children are ALL out-of-flow" (the
  //   CSS2 shape, where the loss is purely the static position and no
  //   in-flow content moves at all) bounds the BLAST — 6 components corpus
  //   wide, 5 more mixed — but not the WORK: it needs the identical
  //   three-platform anonymous-run box. Bounded blast, unbounded cost.
  //
  // THE BAIL (what this wave ships): the flag stays LOUD and stays on the
  // wire. Verified end-to-end on the re-extracted corpus: of 472 files
  // carrying a flagged component, 0 TEST fixtures fail to propagate the
  // reason into `_wpt.lossyReasons` (285 __ref fixtures carry no lossy
  // summary at all — refs use the `{ref, of, specSection}` block; that is
  // a separate, reported gap, not a dropped reason). build-combined-fixture
  // forwards `_wpt.lossyReasons` into the keyMap and inject-wpt-block
  // stamps it on every manifest row, so an affected test is scored WITH its
  // approximation on the record, never silently.
  //
  // ── wave-32 lane R: THE BAIL IS LIFTED — `_runs` shipped ────────────────
  //
  // The design wave-31 wrote here is now the code below it. `_runs` is an
  // ORDERED content list — `[{text}, {child: <authoring key>}, {text}]` —
  // emitted ONLY for the components this flag fires on, i.e. exactly the
  // 1,203-component / 472-file population enumerated above. Every other
  // component carries no key at all, so the rest of the corpus stays
  // byte-identical (that is what makes the wire additive rather than a v3
  // break — see schema/spec/03-children.md §4.1 and 05-versioning.md's
  // additive meta-key rule, the wave-22 `_decorations` precedent).
  //
  // WHAT CHANGED vs the wave-31 design note, and why:
  //   * The reference is the child's AUTHORING KEY, not "id". The converter
  //     MINTS ids (`<lowercased-name>-<NNN>`) at the flatten boundary, so an
  //     id written here names nothing after the hop; the authoring key
  //     survives as the child's `name`. In the extractor-direct pipeline the
  //     three spellings coincide, so both resolutions agree.
  //   * `_text` STAYS on the component, carrying this scanner's
  //     concatenation. A reader that ignores `_runs` therefore behaves
  //     exactly as it did before the key existed — same (lossy) paint, no
  //     decode failure. `_runs` is authoritative only for readers that
  //     honour it.
  //   * The reorder REASON is retired per-component, and only when the runs
  //     actually emitted: buildNode drops 'inline-run-reordered' from the
  //     lossy list for a component carrying `_runs`, and keeps it for every
  //     component where the alignment check below refused (see alignRuns).
  //
  // The three renderers each gained ONE inline anonymous-run box, per the
  // design: web a bare text node in the children walk (NodeRenderer +
  // InlineRuns.ts), Compose and SwiftUI a text run emitted at its slot in
  // the same content pass their children walk uses.
  let reordered = false;
  // wave-32 lane R: the ordered raw pieces, built alongside `textBuf` in
  // ONE walk so the two can never disagree about what the element's own
  // content is. Entries are `{t:'text', raw}` (verbatim source text, still
  // encoded and un-collapsed — the two normalisation steps run once, at the
  // bottom, exactly as they do for `textBuf`) and `{t:'el', tag}` for a
  // child that stays a component. Merge-absorbed children contribute their
  // inner text to the CURRENT text piece, which is precisely why an
  // absorbed run never produces a `_runs` entry: it is already in reading
  // order by construction.
  const segments = [];
  /** Append source text, coalescing with a preceding text piece. */
  const pushText = (s) => {
    if (!s) return;
    const last = segments[segments.length - 1];
    if (last && last.t === 'text') last.raw += s;
    else segments.push({ t: 'text', raw: s });
  };
  // Set once a NON-absorbed child element (component path) has been seen;
  // any subsequent non-whitespace own text is by definition out-of-order.
  let sawKeptChild = false;
  if (!innerHtml) return { text: '', merged, reordered, runProto: null };
  const n = innerHtml.length;
  const VOID = new Set([
    'area','base','br','col','embed','hr','img','input',
    'link','meta','param','source','track','wbr',
  ]);
  let i = 0;
  let textBuf = '';
  while (i < n) {
    // Plain text region — append until next '<'.
    if (innerHtml[i] !== '<') {
      const next = innerHtml.indexOf('<', i);
      const end = next < 0 ? n : next;
      const slice = innerHtml.slice(i, end);
      // B-RC9a: non-whitespace text AFTER a kept child = the glue case.
      // ASCII-whitespace-only runs (the newline+indent between sibling
      // tags) collapse away under §4.1 and can't reorder anything.
      if (sawKeptChild && /[^ \t\n\r\f]/.test(slice)) reordered = true;
      textBuf += slice;
      // wave-32 lane R: the same bytes, recorded at their position.
      pushText(slice);
      i = end;
      continue;
    }
    // '<' encountered. Skip comments / DOCTYPE / PIs defensively.
    if (innerHtml.startsWith('<!', i) || innerHtml.startsWith('<?', i)) {
      const close = innerHtml.indexOf('>', i);
      i = close < 0 ? n : close + 1;
      continue;
    }
    // Closing tag at this level — should never happen if innerHtml is
    // well-formed (the parent's walker pairs them), but tolerate by
    // breaking out of the scan.
    if (innerHtml.startsWith('</', i)) {
      // Skip past this stray close tag and continue — it's not text.
      const close = innerHtml.indexOf('>', i);
      i = close < 0 ? n : close + 1;
      continue;
    }
    // Element open — find tag name, then skip past matching close tag
    // (or just the open tag for void / self-closing). Text inside this
    // descendant element is NOT our own text.
    const tagOpenEnd = innerHtml.indexOf('>', i);
    if (tagOpenEnd < 0) break;
    const tagOpen = innerHtml.slice(i, tagOpenEnd + 1);
    const tagName = (/^<\s*([A-Za-z][A-Za-z0-9-]*)/i.exec(tagOpen)?.[1] || '').toLowerCase();
    if (!tagName) { i = tagOpenEnd + 1; continue; }
    const selfClose = tagOpen.endsWith('/>') || VOID.has(tagName);
    if (selfClose) {
      // B-RC9a: a void child (<br>, <img>, …) always stays a component —
      // own text appended after it renders BEFORE it on our side.
      sawKeptChild = true;
      // wave-32 lane R: a void child occupies a position in the inline
      // flow exactly like a paired one (a <br> IS the line break the
      // surrounding runs sit either side of).
      segments.push({ t: 'el', tag: tagName });
      i = tagOpenEnd + 1;
      continue;
    }
    // Find matching close tag, accounting for same-name nesting (mirrors
    // walkChildren so behaviour is consistent on `<div><div>x</div></div>`).
    const openRe = new RegExp(`<${tagName}\\b`, 'gi');
    const closeRe = new RegExp(`</${tagName}\\s*>`, 'gi');
    let depth = 1;
    let cursor = tagOpenEnd + 1;
    // wave-12: remember where the matching close tag STARTS so the merge
    // branch can slice the child's inner content (walkChildren tracks the
    // same `lastCloseStart` for its innerHtml computation).
    let lastCloseStart = -1;
    while (depth > 0 && cursor < n) {
      openRe.lastIndex = cursor;
      closeRe.lastIndex = cursor;
      const o = openRe.exec(innerHtml);
      const c = closeRe.exec(innerHtml);
      if (!c) break;
      if (o && o.index < c.index) {
        depth++;
        cursor = o.index + o[0].length;
      } else {
        depth--;
        lastCloseStart = c.index;
        cursor = c.index + c[0].length;
      }
    }
    // wave-33 lane N (N1): keep this scanner and walkChildren agreeing about
    // where an UNCLOSED child ends. walkChildren now grants a load-bearing
    // unclosed wrapper the end-of-fragment body (adoptsUnclosedBody); if we
    // did not mirror that here, the wrapper's inner text would be counted
    // TWICE — once as the parent's own `_text` (this scanner falling through
    // with cursor === tagOpenEnd + 1) and once as the adopted child's own
    // text in the tree walk. MEASURED on css-break/inline-skipping-
    // fragmentainer-001, whose `<span style="position:relative">` loses its
    // `</span>` to an outer div's close-pairing: the `&nbsp;` was emitted on
    // both the span and its parent. Same gate, same fragment, one answer.
    if (lastCloseStart < 0 && adoptsUnclosedBody(tagOpen, innerHtml, tagOpenEnd + 1, n)) {
      cursor = n;
    }
    // wave-12 EXTRACTOR-INLINE: pure-inline run merging. When merging is
    // on and this child is a text-only phrase element with no attributes
    // and no tag-targeting rule, splice its inner text into the parent's
    // buffer AT THIS POSITION — reading order preserved, so the preamble
    // becomes '…green square and no red.' instead of '…and .'. The guard
    // on lastCloseStart skips malformed unclosed tags (their trailing text
    // is picked up as plain parent text by the outer loop anyway).
    // wave-21 B-RC9a: assume the child stays a component until the merge
    // branch below absorbs it — the flag is cleared on absorb so a merged
    // run (which keeps reading order by construction) never trips it.
    let childKept = true;
    if (mergeCtx && lastCloseStart >= 0) {
      // Child's inner content — the slice between open and close tags.
      const childInner = innerHtml.slice(tagOpenEnd + 1, lastCloseStart);
      // Attributes parsed from the raw open tag (same scan walkChildren
      // uses) so the predicate here agrees with the tree walker's.
      const attrs = parseAttrsFromTagOpen(tagOpen);
      if (isPureInlineMergeable(tagName, attrs, childInner, mergeCtx.styledTags ?? null)) {
        // Absorb the run; the shared whitespace collapse below normalises
        // any boundary spacing per CSS Text §4.1.
        // B-RC9a: absorbed text landing AFTER a kept sibling still glues
        // out of order ('<u>x</u> then <em>tail</em>' paints tail before
        // x on our side), so the post-kept check applies here too.
        if (sawKeptChild && /[^ \t\n\r\f]/.test(childInner)) reordered = true;
        textBuf += childInner;
        // wave-32 lane R: an absorbed run is TEXT at this position — it
        // coalesces with the neighbouring pieces, which is why merging can
        // never produce a `_runs` entry of its own.
        pushText(childInner);
        merged++;
        childKept = false; // absorbed — not a component, order preserved
      }
    }
    // B-RC9a: a paired child that was NOT absorbed keeps the component
    // path — remember it so any later own text flags the reorder.
    if (childKept) {
      sawKeptChild = true;
      // wave-32 lane R: its position in the inline flow, recorded.
      segments.push({ t: 'el', tag: tagName });
    }
    i = cursor;
  }
  // wave-15 BIDI-EXTRACT part 1: decode character references NOW — after
  // the tag/text separation above (decoded '<' can't be re-parsed as
  // markup) and BEFORE the white-space step below (a decoded TAB is
  // collapsible under `normal` and preserved under the pre family, exactly
  // the HTML-tokenizer-then-CSS order a browser applies). See the
  // decodeCharacterReferences doc comment for the boundary rationale.
  const decodedBuf = decodeCharacterReferences(textBuf);
  // wave-15 part 3: pre-family elements keep their text VERBATIM — no
  // collapse, no trim — per CSS Text §4.1.1 (spaces/tabs/segment breaks
  // are all preserved under pre/pre-wrap/break-spaces). bidi-tab-001's
  // '\t0' spans and tab-bidi-001's literal-TAB runs depend on this.
  // wave-32 lane R: the run list, built from the SAME pieces the buffer was
  // built from and normalised by the SAME two steps — only per piece, and
  // with the trim applied to the ENDS OF THE SEQUENCE rather than to every
  // entry. That distinction is the whole point (spec 03 §4.1 rule 6):
  // `the quick <u>brown</u> fox` must keep the space before AND after the
  // child, so trimming each run individually would silently delete two word
  // spaces the browser paints.
  //
  // ── wave-34 lane R: THE EMISSION CONDITION, WIDENED ─────────────────────
  //
  // wave-32 gated the list on `reordered` — own text appearing AFTER a kept
  // child. That is the case where the concatenation paints in the WRONG
  // ORDER, and it is genuinely the only case where `_text` alone is a LIE.
  // But it is not the only case where `_text` alone is LOSSY, and wave-33
  // lane N measured the difference on the ref:
  //
  //   `<p>…green <em>…</em></p>` — text, then a kept child, nothing after.
  //   No reorder (our paint order IS document order), so wave-32 emitted no
  //   runs; `_text` therefore carried the COLLAPSED-AND-TRIMMED buffer and
  //   the boundary space between 'green' and the `<em>` was gone. The two
  //   words render welded. CSS Text §4.1 rule 4 collapses that space to one
  //   space; it does not delete it — the deletion is purely an artifact of
  //   trimming a buffer whose true end is an element boundary, not the end
  //   of the line. Cost on the record: CSS2/selector/lang-pseudoclass-001
  //   0.7774 → 0.7684 when wave-33 lane N routed two more tests onto that
  //   pre-existing path (see splitSelectorChain's N2 note, which recorded
  //   this widening as the follow-up rather than smuggling it in).
  //
  // So the condition becomes: emit whenever the component's own text and its
  // kept element children INTERLEAVE AT ALL — i.e. there is at least one
  // element segment AND the own-text buffer is non-empty after normalisation.
  // `reordered` stays in the disjunction because it is a superset guard for
  // the pre family (where `hasOwnText` is measured on the un-collapsed
  // buffer) and because retiring it here would couple two independent facts.
  //
  // WHAT THE WIDENING DOES *NOT* COVER, deliberately: a component whose own
  // text normalises to the EMPTY string — the `<div>\n<span/>\n<b/>\n</div>`
  // shape, where the only own text is the inter-sibling newline+indent. That
  // whitespace is real (the browser paints one word space between the two
  // inline children) but it is ALREADY on the wire, as the wave-26 `ws-after`
  // marker; emitting it a second time as a `{text:' '}` run would double the
  // space on every renderer that honours both. The `ws-after` channel owns
  // the child/child boundary; `_runs` owns the text/child boundary. One fact,
  // one channel — see the WS_AFTER_ROLE banner for the other half.
  //
  // The list stays ADDITIVE at the wire: `_text` is untouched (still the
  // concatenation), `alignRuns` still refuses unless the two walks prove they
  // describe the same children, and a reader that ignores `_runs` paints
  // exactly what it painted before. The only new bytes are on components that
  // genuinely interleave.
  const sawElementChild = segments.some((s) => s.t === 'el');
  // Collapse whitespace per CSS white-space:normal default.
  //
  // Bug 3 fix (css-text/hanging-punctuation-first-002): the JS `\s` class
  // matches U+3000 IDEOGRAPHIC SPACE (and many other Unicode whitespace
  // characters), so the prior `/\s+/g` regex stripped the leading U+3000
  // from `<div class=test>　↓</div>` and killed the hanging-punctuation
  // test signal. Per CSS Text §4.1 the only characters that participate
  // in the collapsible-whitespace rule are space (U+0020), tab (U+0009),
  // line feed (U+000A), carriage return (U+000D), and form feed (U+000C).
  // Non-ASCII whitespace (U+00A0 NO-BREAK SPACE, U+3000 IDEOGRAPHIC SPACE,
  // every other Unicode space) is preserved verbatim. We collapse the
  // ASCII subset, then trim leading/trailing ASCII spaces only — same
  // [ \t\n\r\f]+ class, not `\s`.
  // wave-34 lane R: the pre family keeps its buffer VERBATIM (§4.1.1), so
  // `text` is `decodedBuf` there and `collapsed` is never consulted; the
  // single expression below keeps the two paths' own-text answer in ONE
  // place, which is what the interleave test needs to read.
  const collapsed = preserveWhitespace ? decodedBuf : decodedBuf
    .replace(/[ \t\n\r\f]+/g, ' ')
    .replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, '');
  // wave-34 lane R: the widened gate (see the banner above). Computed AFTER
  // the normalisation because "has own text" is a fact about the SHIPPED
  // `_text`, not about the raw buffer — a component whose only own text is
  // inter-sibling indentation must stay on the `ws-after` channel.
  const runProto = (reordered || (sawElementChild && collapsed.length > 0))
    ? buildRunProto(segments, preserveWhitespace)
    : null;
  // Text plus the wave-12 merge count (0 whenever mergeCtx was null), the
  // wave-21 B-RC9a reorder flag (see the declaration comment above) and the
  // wave-32 lane R run list (null unless the component interleaves).
  return { text: collapsed, merged, reordered, runProto };
}

/**
 * wave-32 lane R — turn scanOwnText's raw ordered pieces into the run list
 * shape `_runs` ships: `[{ text }, { el: <n-th kept child> }, …]`.
 *
 * The element entries are still POSITIONAL (`el: k`, the k-th kept child in
 * scan order) rather than keyed: only the tree walker knows the children's
 * authoring keys, and only IT can prove the two enumerations agree. That
 * proof lives in alignRuns; this function's contract stops at "same pieces,
 * same order, normalised".
 *
 * NORMALISATION, mirroring scanOwnText's bottom exactly:
 *  1. character references decode per piece — a reference never spans a
 *     child element, so per-piece and whole-buffer decoding agree byte for
 *     byte (see decodeCharacterReferences for the tokenizer-order rationale);
 *  2. `preserveWhitespace` (the pre family) skips step 3 entirely;
 *  3. otherwise the CSS Text §4.1 ASCII collapse runs per piece, and the
 *     TRIM runs only on the sequence's outer edges — leading whitespace off
 *     the first piece if it is text, trailing off the last if it is text.
 *
 * Pieces that normalise to the empty string are dropped: they carry no
 * glyphs and no box, and an empty entry would only add a zero-length text
 * node downstream. A whitespace-ONLY piece between two children is NOT
 * empty — it is the inter-run word space and survives as `{ text: ' ' }`.
 *
 * Exported for the unit tests (the normalisation rules above are the part
 * most likely to drift silently).
 */
export function buildRunProto(segments, preserveWhitespace = false) {
  if (!segments || segments.length === 0) return null;
  // Step 1 + 3a: per-piece decode, then per-piece collapse (pre family
  // keeps its spaces/tabs/segment breaks verbatim, CSS Text §4.1.1).
  const normalised = segments.map((seg) => {
    if (seg.t !== 'text') return seg;
    const decoded = decodeCharacterReferences(seg.raw);
    return { t: 'text', raw: preserveWhitespace ? decoded : decoded.replace(/[ \t\n\r\f]+/g, ' ') };
  });
  // Step 3b: the OUTER trim. Same ASCII class as the whole-buffer trim; the
  // pre family is exempt for the same reason it skips the collapse.
  if (!preserveWhitespace) {
    const first = normalised[0];
    if (first && first.t === 'text') first.raw = first.raw.replace(/^[ \t\n\r\f]+/, '');
    const last = normalised[normalised.length - 1];
    if (last && last.t === 'text') last.raw = last.raw.replace(/[ \t\n\r\f]+$/, '');
  }
  // Emit, numbering the element entries in scan order so alignRuns can key
  // them against the tree walker's kept-children array.
  const out = [];
  let keptSeq = 0;
  for (const seg of normalised) {
    // `tag` rides along purely so alignRuns can PROVE the two walks agree
    // (see its banner); it never reaches the wire.
    if (seg.t === 'el') { out.push({ el: keptSeq, tag: seg.tag }); keptSeq += 1; continue; }
    if (seg.raw.length > 0) out.push({ text: seg.raw });
  }
  return out.length > 0 ? out : null;
}

/**
 * wave-32 lane R — prove that scanOwnText's kept-element enumeration and
 * the tree walker's kept-children array describe the SAME elements, then
 * rewrite the positional `{el:k}` entries as `{childIndex:k}`.
 *
 * WHY A PROOF AND NOT AN ASSUMPTION. The two walks are different code:
 * scanOwnText decides "kept" from `isPureInlineMergeable` alone, while
 * `recurse` first drops HEAD_ONLY_TAGS (a body-level `<style>`), then
 * applies the SAME mergeable predicate, and finally stops recursing at
 * `maxDepth` (which leaves a node with kept elements in its source but an
 * EMPTY children array). Any of those makes the k-th scanned element a
 * different thing from the k-th child — and a `_runs` list that names the
 * wrong box is worse than no list at all, because it silently reorders
 * content instead of loudly approximating it.
 *
 * So the check is total: same COUNT, and same TAG at every position. On any
 * mismatch this returns null, the component ships without `_runs`, and the
 * 'inline-run-reordered' reason stays on it — the wave-21 bail, still loud,
 * exactly where the new wire cannot prove itself.
 *
 * @param proto    buildRunProto's output (`{text}` / `{el}` entries).
 * @param children the tree walker's KEPT children, in document order.
 * @returns `[{text}|{childIndex}]`, or null when the two walks disagree.
 */
export function alignRuns(proto, children) {
  if (!proto || proto.length === 0) return null;
  const kids = children ?? [];
  const els = proto.filter((e) => e.el !== undefined);
  // Count first — the cheap half of the proof, and the one that catches
  // maxDepth truncation and head-only filtering alike.
  if (els.length !== kids.length) return null;
  // Tag equality at every position — catches the case where both walks
  // kept the same NUMBER of elements but not the same ones.
  for (let k = 0; k < els.length; k += 1) {
    if (els[k].tag !== kids[k].tag) return null;
  }
  // A single-entry list says nothing the plain `_text` channel does not
  // already say, so it is not worth a wire key (and a lone `{child}` with
  // no text is not a reorder at all).
  if (proto.length < 2) return null;
  return proto.map((e) => (e.el === undefined ? { text: e.text } : { childIndex: e.el }));
}

/**
 * wave-11 TITAN fix 2 (css-grid abspos descendant-static-position-001..004
 * + every WPT test opening with bare instruction prose): extract the
 * LEADING anonymous text of <body> — non-whitespace text nodes that are
 * direct body children and precede the first renderable element, e.g.
 * `<body>There should be no red:\n<div class="grid">…`.
 *
 * Why: browsers wrap such text in an anonymous block box (CSS 2.1
 * §9.2.1.1) that occupies a full line box (~18px at the UA default)
 * BEFORE the first element child. walkChildren() emits only ELEMENT
 * children, so the extractor silently dropped that line and every
 * platform rendered ~18px higher than the browser-ref — 100% of the web
 * gap on the 4 css-grid descendant-static-position tests and a
 * ~0.03–0.05 SSIM penalty on the natives. buildComponents() emits the
 * returned string as a leading `_text`-bearing block component (the same
 * `_text` channel the per-element ownText path uses).
 *
 * Scope: the LEADING run only. Anonymous text INTERLEAVED between (or
 * trailing after) element siblings is rare in the corpus and remains a
 * documented gap — representing it faithfully needs order-preserving
 * text components between the `__N` siblings, which the flat components
 * map cannot express today. No silent fallthrough: those runs were
 * always dropped; this narrows the loss to the non-leading cases.
 *
 * Head-only elements (<style>/<script>/<link>/<meta>/…) encountered
 * before the first renderable element are SKIPPED — including their
 * content, so `<style>` CSS text never leaks into `_text` — because in
 * the no-<body> fallback the whole document is scanned and head
 * scaffolding precedes the body prose.
 *
 * Whitespace collapses per the same CSS `white-space: normal` ASCII-only
 * rule as extractOwnText (CSS Text §4.1). Returns '' when body opens
 * with an element (the common case — fixtures stay byte-identical).
 *
 * wave-12 EXTRACTOR-INLINE interplay: since wave-12 this is a thin
 * string-returning wrapper over extractLeadingBodyTextInfo with merging
 * OFF (legacy contract preserved). buildComponents calls the Info variant
 * WITH a mergeCtx so a leading run like
 * `<body>There should be <strong>no red</strong>: <div>…` yields the
 * single string 'There should be no red:' — the pure-inline element is
 * absorbed in reading order and the run CONTINUES past it instead of
 * stopping (ordering stays correct when both wave-11 and wave-12 apply;
 * the tree walker's depth-0 filter skips the same absorbed elements so
 * they never double-emit as components).
 *
 * Exported so the unit tests can pin the contract.
 */
export function extractLeadingBodyText(html) {
  // Merging disabled — byte-identical to the wave-11 behaviour for every
  // legacy caller; only the `.text` half of the Info result is exposed.
  return extractLeadingBodyTextInfo(html, null).text;
}

/**
 * wave-12 EXTRACTOR-INLINE: the merge-aware leading-body-text scanner.
 * Returns `{ text, merged }` — `merged` counts pure-inline elements
 * absorbed into the leading run (drives the 'inline-run-merged' lossy
 * marker on the `__text` component). `mergeCtx` is
 * `{ styledTags?: Set<string> }` or null (no merging — wave-11 semantics:
 * the run stops at the FIRST renderable element of any kind).
 *
 * wave-15: `preserveWhitespace` (default false) mirrors scanOwnText — when
 * the BODY's resolved white-space is in the pre family (buildComponents
 * derives it from the body-root rule bag), the leading prose keeps its
 * spaces/tabs/newlines verbatim instead of collapsing. Character
 * references decode on both paths (same tokenize-then-white-space boundary
 * as scanOwnText; see decodeCharacterReferences).
 *
 * Exported so the unit tests can pin the contract.
 */
export function extractLeadingBodyTextInfo(html, mergeCtx = null, preserveWhitespace = false) {
  // Merge counter for the leading run — reported to buildComponents.
  let merged = 0;
  // Same body-locator + implicit-<html>/<head> unwrap as
  // extractBodyTreeNested so both walkers agree on what "body content" is —
  // wave-21 A-RC1: both now call the ONE shared locateBodyContent (closed
  // <head> keeps the content AFTER </head>; see the helper's banner).
  const { inner } = locateBodyContent(html);
  const n = inner.length;
  // Void set mirrors walkChildren — <link>/<meta>/<base> have no close tag.
  const VOID = new Set([
    'area','base','br','col','embed','hr','img','input',
    'link','meta','param','source','track','wbr',
  ]);
  let i = 0;
  let buf = '';
  while (i < n) {
    // Text region — accumulate until the next '<'.
    if (inner[i] !== '<') {
      const next = inner.indexOf('<', i);
      const end = next < 0 ? n : next;
      buf += inner.slice(i, end);
      i = end;
      continue;
    }
    // Comments / DOCTYPE / PIs — already stripped upstream, but skip
    // defensively (mirrors extractOwnText).
    if (inner.startsWith('<!', i) || inner.startsWith('<?', i)) {
      const close = inner.indexOf('>', i);
      i = close < 0 ? n : close + 1;
      continue;
    }
    // Stray close tag (a dangling `</head>` in fallback mode) — not text,
    // not a renderable element: skip past it and keep scanning.
    if (inner.startsWith('</', i)) {
      const close = inner.indexOf('>', i);
      i = close < 0 ? n : close + 1;
      continue;
    }
    // Element open tag — identify it.
    const tagOpenEnd = inner.indexOf('>', i);
    if (tagOpenEnd < 0) break; // malformed tail — nothing more to read
    const tagOpen = inner.slice(i, tagOpenEnd + 1);
    const tagName = (/^<\s*([A-Za-z][A-Za-z0-9-]*)/i.exec(tagOpen)?.[1] || '').toLowerCase();
    if (!tagName) { i = tagOpenEnd + 1; continue; }
    // First RENDERABLE element ends the leading run (its own text belongs
    // to its component via the ownText channel, not to the body prose) —
    // UNLESS (wave-12) merging is on and the element is a pure-inline run,
    // in which case its text is absorbed and the leading run CONTINUES.
    if (!HEAD_ONLY_TAGS.has(tagName)) {
      if (mergeCtx) {
        // Locate the element's matching close tag. Mergeable elements are
        // text-only, so same-name nesting is impossible: if the content
        // between here and the FIRST `</tag>` contains any '<', the
        // predicate rejects it anyway — no depth tracking needed.
        const selfClose = tagOpen.endsWith('/>') || VOID.has(tagName);
        if (!selfClose) {
          const closeRe = new RegExp(`</${tagName}\\s*>`, 'gi');
          closeRe.lastIndex = tagOpenEnd + 1;
          const c = closeRe.exec(inner);
          if (c) {
            // Candidate inner content + attrs, then the shared predicate —
            // EXACTLY the one the depth-0 tree filter uses, so an element
            // absorbed here is guaranteed to be skipped there (no double
            // emission) and vice versa.
            const childInner = inner.slice(tagOpenEnd + 1, c.index);
            const attrs = parseAttrsFromTagOpen(tagOpen);
            if (isPureInlineMergeable(tagName, attrs, childInner, mergeCtx.styledTags ?? null)) {
              // Absorb the run in reading order and continue scanning —
              // the whitespace collapse at the end normalises boundaries.
              buf += childInner;
              merged++;
              i = c.index + c[0].length;
              continue;
            }
          }
        }
      }
      // Non-mergeable renderable element — the leading run ends here.
      break;
    }
    // Head-only element: skip it WHOLESALE (open tag through matching
    // close) so <style>/<script> bodies never masquerade as prose.
    if (tagOpen.endsWith('/>') || VOID.has(tagName)) {
      i = tagOpenEnd + 1;
      continue;
    }
    // Head-only tags never nest same-name in the corpus, so the first
    // close tag is the matching one (no depth tracking needed here).
    const closeRe = new RegExp(`</${tagName}\\s*>`, 'gi');
    closeRe.lastIndex = tagOpenEnd + 1;
    const c = closeRe.exec(inner);
    i = c ? c.index + c[0].length : n;
  }
  // wave-15 part 1: decode character references at the same boundary as
  // scanOwnText — text/tag separation is settled, white-space step is next.
  const decodedLeading = decodeCharacterReferences(buf);
  // wave-15 part 3: pre-family body keeps the leading prose verbatim (CSS
  // Text §4.1.1 — no collapse, no trim); see the preserveWhitespace doc.
  // Whitespace-ONLY runs still yield '' even under pre: in the no-<body>
  // fallback the scan crosses head scaffolding, and the newlines BETWEEN
  // head-only elements are text the HTML parser would have dropped in the
  // head — emitting them as a phantom `__text` of blank lines would be
  // markup noise, not prose (documented trade: a genuinely blank-line-led
  // pre body loses those blanks; zero such tests in the corpus).
  if (preserveWhitespace) {
    return /[^ \t\n\r\f]/.test(decodedLeading)
      ? { text: decodedLeading, merged }
      : { text: '', merged };
  }
  // Collapse ASCII whitespace only — same CSS Text §4.1 rule (and the same
  // U+3000-preserving rationale) as extractOwnText above. The merge count
  // rides alongside so buildComponents can lossy-mark the __text component.
  return {
    text: decodedLeading
      .replace(/[ \t\n\r\f]+/g, ' ')
      .replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, ''),
    merged,
  };
}

/**
 * wave-21 B-RC2: body-level bare text BETWEEN and AFTER elements.
 *
 * The wave-11 leading-text machinery only rescued prose BEFORE the first
 * body element; a raw text run between/after elements simply vanished —
 * css-multicol/auto-fill-auto-size-001-print is an empty styled <div>
 * followed by the bare words "On the first page", and all three platforms
 * rendered ONLY the div (the words were dropped with lossy:false). Browsers
 * wrap every such run in an anonymous block box (CSS 2.1 §9.2.1.1), so each
 * run must become a `_text` component in document order.
 *
 * Returns `Array<{ afterElemIndex, text }>` where `afterElemIndex` is the
 * COUNT of kept depth-0 elements preceding the run (≥ 1 — the leading run,
 * afterElemIndex 0, stays the province of extractLeadingBodyTextInfo, whose
 * merge-absorb semantics this scanner deliberately does not duplicate).
 * buildComponents emits run k between tree elements k-1 and k, preserving
 * reading order exactly — no reorder, hence no lossy marker.
 *
 * ALIGNMENT CONTRACT: `afterElemIndex` counts elements through the SAME
 * decisions extractBodyTreeNested's depth-0 filter makes (same
 * locateBodyContent buffer, same walkChildren pairing, same HEAD_ONLY skip,
 * same isPureInlineMergeable + seenBlock keep rule), so index k here always
 * refers to tree[k-1] there. Any drift would attach a run to the wrong gap.
 *
 * `preserveWhitespace` mirrors the leading scanner: body-scope prose
 * collapses per CSS Text §4.1 unless the body's resolved white-space is in
 * the pre family. Exported so the unit tests can pin the contract.
 */
export function extractBodyTextRuns(html, mergeCtx = null, preserveWhitespace = false) {
  // Same buffer every walker sees (A-RC1 shared locator — load-bearing for
  // the alignment contract above).
  const { inner } = locateBodyContent(html);
  // Interleaved element + text items in document order (B-RC2 walker mode).
  const items = walkChildren(inner, { collectText: true });
  const runs = [];
  // Count of KEPT elements so far — the gap index for the run being built.
  let keptCount = 0;
  // Mirrors the depth-0 filter's seenBlock: true once any non-mergeable
  // element was kept (ends the leading-run absorb window).
  let seenBlock = false;
  // Raw accumulator for the current gap's text (may span comment breaks).
  let buf = '';
  // Finalize the accumulated text for the gap ENDING here. Leading-gap text
  // (keptCount === 0) is discarded — extractLeadingBodyTextInfo owns it.
  const flush = () => {
    const raw = buf;
    buf = '';
    if (keptCount === 0) return;
    // Character references decode at the same tokenize-then-whitespace
    // boundary as scanOwnText (wave-15 part 1).
    const decoded = decodeCharacterReferences(raw);
    // Pre-family body keeps the run verbatim, but whitespace-ONLY runs are
    // markup noise in both modes (same trade documented on the leading
    // scanner); normal mode collapses per CSS Text §4.1 (ASCII class only —
    // U+3000 et al. are non-collapsible, same rationale as scanOwnText).
    const text = preserveWhitespace
      ? (/[^ \t\n\r\f]/.test(decoded) ? decoded : '')
      : decoded.replace(/[ \t\n\r\f]+/g, ' ').replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, '');
    if (text) runs.push({ afterElemIndex: keptCount, text });
  };
  for (const it of items) {
    // Text item — accumulate; the run may continue across head-only
    // scaffolding and absorbed inline elements.
    if (it.text !== undefined) { buf += it.text; continue; }
    // Head-only elements are invisible scaffolding (Bug 5) — the run
    // continues across them exactly as the browser renders it.
    if (HEAD_ONLY_TAGS.has(it.tag)) continue;
    // Keep/absorb decision — byte-for-byte the depth-0 filter's rule.
    const mergeable = mergeCtx
      ? isPureInlineMergeable(it.tag, it.attrs, it.innerHtml, mergeCtx.styledTags ?? null)
      : false;
    if (mergeable && !seenBlock) {
      // Leading-run absorbed element: its text lives in the `__text`
      // component (extractLeadingBodyTextInfo). Not kept, not counted —
      // and only reachable while keptCount === 0 (first kept element is
      // necessarily non-mergeable), so no between-gap run is affected.
      continue;
    }
    // Kept element — the current gap ends here; the next begins after it.
    if (!mergeable) seenBlock = true;
    flush();
    keptCount++;
  }
  // Trailing gap (text after the last element — the auto-fill-print case).
  flush();
  return runs;
}

/**
 * Walk the body subtree and return a NESTED tree shape — each node carries
 * its element children under `children: [...]` and its own text under
 * `ownText`. This is the data source the fixture builder uses to populate
 * IRComponent.children + the new `_text` field.
 *
 * Why a separate function from extractBodyTree?
 *   - extractBodyTree returns a FLAT array (depth ≤ 3) and is consumed by
 *     the legacy unit tests + the matched-rule logic that wants every
 *     descendant with its ancestor chain. Keep it back-compat.
 *   - extractBodyTreeNested returns a TREE (depth ≤ 5 per the swarm-001
 *     fix-A spec) and is consumed only by buildComponents to emit
 *     IRComponent.children. Higher cap (5) covers the deeper nesting
 *     patterns documented in css-grid abspos + css-overflow + css-contain
 *     (e.g. `body > section > div > p > span`) without unbounded blow-up
 *     on pathological fixtures. Empirically the WPT corpus uses ≤4 deep
 *     for 99% of tests; 5 is a safety margin without being absurd.
 *
 * Returns Array<NestedNode> where NestedNode is:
 *   { tag, attrs, raw, ownText: string,
 *     ancestors: Array<{tag,attrs}>, children: Array<NestedNode> }
 *
 * Empty `children` arrays are still emitted (rather than omitted) so the
 * caller doesn't need to null-check; the fixture serializer is responsible
 * for omitting them when persisting.
 *
 * wave-12 EXTRACTOR-INLINE: `mergeCtx` (`{ styledTags?: Set<string> }` or
 * null) switches on pure-inline run merging. When set:
 *   - each node's `ownText` comes from extractOwnTextMerged, i.e. text-only
 *     phrase children (strong/em/b/i/span/code, no attrs, no tag rule) are
 *     absorbed into the parent's text in reading order;
 *   - those absorbed children are FILTERED OUT of the walk (no component,
 *     no sibling-position slot) — at depth ≥ 1 all of them, at depth 0
 *     (body level) only the LEADING run (which extractLeadingBodyTextInfo
 *     absorbed into the `__text` component); a body-level inline element
 *     AFTER the first block sibling keeps the component path, because
 *     nothing else carries its text (the block-stacking divergence remains
 *     for that rare case, but no text is ever silently lost);
 *   - nodes whose ownText absorbed at least one run carry
 *     `inlineMerged: true` so buildComponents can emit the
 *     'inline-run-merged' lossy marker.
 * Null (legacy callers/tests) keeps the pre-wave-12 behaviour exactly.
 */
export function extractBodyTreeNested(html, maxDepth = 5, mergeCtx = null) {
  // Same body-locator + implicit-<html>/<head> unwrap as every other walker
  // — wave-21 A-RC1: the four sites now share the ONE locateBodyContent
  // (closed <head> keeps the content AFTER </head>; see the helper banner).
  // Sharing the locator is load-bearing for wave-21 B-RC2 too: the body
  // text-run scanner (extractBodyTextRuns) aligns its kept-element indices
  // with this walk, which only holds if both see the SAME inner buffer.
  const { inner } = locateBodyContent(html);
  // Bug 2 fix (selectors__child-indexed-no-parent): each ancestor entry
  // gains a `pos: {sibIndex, sibCount, sibTypeIndex, sibTypeCount, isEmpty,
  // isRoot}` field so non-rightmost pseudo-classes can be evaluated honestly
  // (e.g. `.parent:first-child .target`). The actual ancestor list returned
  // to callers keeps its legacy shape (real ancestors only — no synthetic
  // `:root` entry) so existing selectorMatches() tests stay green. The
  // synthetic root is injected in propsForElement() at match time, which
  // is the only call site that consumes pos data.
  /**
   * wave-34 lane R: `domSink` is an out-parameter — the caller hands in an
   * object and this level fills `domSink.list` with its DOM-TRUTHFUL child
   * list (see the domKids banner below). It exists because the parent needs
   * that list even when the level produced ZERO components (every child
   * merged away), which is precisely the case `pos.kids` must not miss.
   */
  function recurse(fragment, ancestors, depth, domSink = null) {
    // Head-only scaffolding never becomes components (Bug 5), at any depth.
    const rawKids = walkChildren(fragment).filter((k) => !HEAD_ONLY_TAGS.has(k.tag));
    // wave-12 EXTRACTOR-INLINE: drop the pure-inline kids whose text was
    // absorbed elsewhere — by the PARENT's ownText (depth ≥ 1, all of
    // them) or by the leading `__text` component (depth 0, leading run
    // only). `seenBlock` marks the end of the body-level leading run: once
    // any non-mergeable kid has been kept, later inline kids stay
    // components so their text is never silently lost (see the function
    // doc comment). The predicate is the SAME isPureInlineMergeable the
    // ownText scanner + leading-text scanner use, so absorb/skip decisions
    // can never disagree.
    let seenBlock = false;
    // wave-21 A-RC6: keep each KEPT kid's index within rawKids (`domIdx`) —
    // the RENDERABLE-sibling position including merge-absorbed inline
    // elements. CSS Values 5 §5.1 sibling-index() counts ELEMENT siblings
    // in the DOM; a merge-absorbed <span> is still a DOM element there
    // (the conic-gradient sibling-index test's leading empty <span> exists
    // ONLY to make the <div> sibling #2), so the filtered `idx` below is
    // the wrong number to bake. Documented limit: head-only siblings
    // inside <body> (rare; corpus puts them in <head>) are not counted.
    const kids = [];
    rawKids.forEach((k, rawIdx) => {
      let keep = true;
      if (mergeCtx) {
        const mergeable =
          isPureInlineMergeable(k.tag, k.attrs, k.innerHtml, mergeCtx.styledTags ?? null);
        if (!mergeable) { seenBlock = true; }
        // Depth 0: only the leading run was absorbed (into __text); inline
        // kids after the first kept sibling remain components.
        // Depth ≥ 1: the parent's merged ownText carries this kid's text.
        else keep = depth === 0 ? seenBlock : false;
      } // legacy path (mergeCtx null) — no merging, keep all
      // Annotate rather than copy: `domIdx` rides the walker's kid object,
      // which is private to this recursion (walkChildren allocates fresh).
      if (keep) { k.domIdx = rawIdx; kids.push(k); }
      else k.mergedAway = true;
    });
    // ── wave-34 lane R: THE DOM-TRUTHFUL CHILD LIST (`:has()`'s handle) ────
    //
    // Every other position fact this walker stamps is about OUR COMPONENT
    // TREE, because that is what the pseudo is asked about at render time.
    // `:has()` is different: Selectors-4 §5.4 asks whether the DOM contains
    // a matching element, and the browser's answer counts merge-absorbed
    // inline children — which our component tree has already thrown away.
    //
    // MEASURED, and the reason this list exists at all:
    //   selectors/dir-pseudo-in-has.html — `.ltr:has(*:dir(ltr))` over
    //   `<div class="ltr"><span></span></div>`. The stylesheet targets no
    //   `span`, so wave-12's isPureInlineMergeable absorbs it and the div
    //   ships with NO children. Answering `:has()` off the component list
    //   would say "no descendant" and silently paint the div red — and,
    //   worse, would say it CONFIDENTLY, since a parse-supported `:has()`
    //   no longer feeds countUnsupportedRules and so no longer earns the
    //   wave-30 A3 post-load browser that was covering this test.
    //
    // So the list runs over `rawKids`, not `kids`. Entries for KEPT children
    // are back-filled from their node's own `pos` in the second pass below;
    // entries for MERGED children are complete HERE, because
    // isPureInlineMergeable's rule 2 refuses any child containing `<` — a
    // merged element provably has NO element descendants, so `kids: []` is a
    // fact about it and not a truncation guess.
    const domKids = rawKids.map((k) => (k.mergedAway
      ? {
        tag: k.tag,
        attrs: k.attrs,
        // Provably childless (rule 2), so the subtree scan can stop here.
        kids: [],
        // `:empty` (Selectors-4 §6.6) — no children AND no text.
        isEmpty: !(k.innerHtml ?? '').trim(),
        // `:dir(auto)`'s first-strong corpus is just its text (no elements).
        subtreeText: (k.innerHtml ?? '').replace(/[ \t\n\r\f]+/g, ' ').trim(),
      }
      : { tag: k.tag, attrs: k.attrs }));
    if (domSink) domSink.list = domKids;
    // First pass: compute per-sibling position metadata. We pre-walk and
    // count tag occurrences so each child gets its global sib-index AND
    // its tag-typed sib-index (drives `:nth-of-type` / `:first-of-type`).
    const totalCount = kids.length;
    const typeCounts = new Map();
    for (const k of kids) typeCounts.set(k.tag, (typeCounts.get(k.tag) ?? 0) + 1);
    // swarm-003 Bug 2: capture the full sibling list (tag+attrs only —
    // we don't need the inner HTML) so `:nth-child(N of S)` can renumber
    // among siblings that match the selector list S. The array is
    // shared by reference across all siblings to keep the allocation
    // overhead at O(siblings) per level rather than O(siblings²).
    const siblingList = kids.map((k) => ({ tag: k.tag, attrs: k.attrs }));
    const typeSeen = new Map();
    const out = [];
    kids.forEach((k, idx) => {
      const seen = typeSeen.get(k.tag) ?? 0;
      typeSeen.set(k.tag, seen + 1);
      // Recurse to capture nested element children, BUT respect maxDepth.
      let children = [];
      const pos = {
        isRoot: false,
        sibIndex: idx,
        sibCount: totalCount,
        sibTypeIndex: seen,
        sibTypeCount: typeCounts.get(k.tag),
        // :empty matches when the element has NO children (incl. text).
        isEmpty: false, // patched after recurse, once we know the kids
        // swarm-003 Bug 2 (`:nth-child(N of S)`): full sibling list, in
        // document order. Consumed by evalPseudo's of-selector branch.
        siblings: siblingList,
        // wave-21 A-RC6: 0-based renderable-sibling index BEFORE the merge
        // filter (see the kids loop above) — buildComponents bakes
        // sibling-index() as domSibIndex + 1 (the function is 1-based).
        domSibIndex: k.domIdx ?? idx,
      };
      const childAncestor = { tag: k.tag, attrs: k.attrs, pos };
      // wave-15 BIDI-EXTRACT part 3: resolve this element's effective
      // white-space BEFORE extracting its text. buildComponents supplies
      // `mergeCtx.resolveWhiteSpace` (own matched value → nearest ancestor
      // with one → body root → 'normal', mirroring CSS inheritance since
      // white-space is inherited per CSS Text §3); legacy callers without
      // the resolver keep the unconditional-collapse behaviour. `pos` is
      // built above ownText now (harmless reorder — pos never depended on
      // the text; only its isEmpty patch below does).
      const preserve = mergeCtx?.resolveWhiteSpace
        ? WHITESPACE_PRESERVING.has(mergeCtx.resolveWhiteSpace(k.tag, k.attrs, ancestors, pos))
        : false;
      // wave-12: with merging on, ownText absorbs this element's pure-
      // inline children in reading order (extractOwnTextMerged); the
      // corresponding kids are dropped by the recursion filter above.
      // Legacy path (mergeCtx null) is byte-identical to extractOwnText.
      // wave-15: pre-family elements keep spaces/tabs/newlines verbatim.
      const ownRes = extractOwnTextMerged(k.innerHtml, mergeCtx, preserve);
      const ownText = ownRes.text;
      // wave-34 lane R — THE RELATIONAL METADATA (`pos.kids`), for `:has()`.
      //
      // Selectors-4 §5.4 asks a question no other pseudo asks: not "where is
      // this element among its siblings" but "what is UNDERNEATH it". So the
      // matcher needs a handle on the subtree, and the walker is the only
      // code that has one.
      //
      // The handle is FREE: the child level's own `siblingList` is already
      // allocated (every child's `pos.siblings` points at it), and it already
      // carries exactly the fields compoundMatches needs — `tag`, `attrs`,
      // plus the second pass's `isEmpty` / `subtreeText`. Pointing `kids` at
      // that same array costs one reference per node and stays ACYCLIC (it
      // only ever points DOWN the tree, unlike the `pos.siblings[i].pos`
      // back-reference the second pass's banner explains we must not create).
      //
      // WHEN IT IS DELIBERATELY LEFT UNDEFINED: the `maxDepth` cut. Below the
      // cut the walker knows the element HAS element children but never
      // enumerated them, so an empty list there would be a silent lie —
      // `div:has(span)` would answer "no span" about a subtree we never
      // looked at. Undefined instead makes evalPseudo's `has` branch answer
      // null, which drops the rule and (via countUnsupportedRules) routes the
      // test to the post-load browser that CAN see it. No silent fallthrough.
      const kidSink = {};
      let walkedKids = false;
      if (depth + 1 < maxDepth && k.innerHtml) {
        children = recurse(
          k.innerHtml,
          [...ancestors, childAncestor],
          depth + 1,
          kidSink,
        );
        walkedKids = true;
      } else if (!k.innerHtml) {
        // Empty element — "no children" is a FACT here, not a truncation.
        walkedKids = true;
      }
      // The DOM-truthful list the recursion just filled (see the domKids
      // banner): every element child, merged-away ones included.
      if (walkedKids) pos.kids = kidSink.list ?? [];
      pos.isEmpty = children.length === 0 && !ownText;
      const node = {
        tag: k.tag, attrs: k.attrs, raw: k.raw,
        ownText,
        ancestors: ancestors.slice(),
        children,
        pos,
      };
      // wave-26 lane WWS: carry the walker's inter-sibling whitespace fact
      // onto the tree node so buildNode can stamp the `ws-after` marker
      // (see the WS_AFTER_ROLE banner). Set only when true, so every node
      // whose source packed siblings flush keeps its exact legacy shape.
      // KNOWN UNDER-REPORT (conservative by design): the flag describes the
      // gap to the NEXT WALKER ITEM, and the merge filter above may drop an
      // absorbed pure-inline element out of that gap — `<div/><span>x</span>
      // <div/>` therefore reads as "no whitespace" between the two divs.
      // Under-reporting only ever withholds a space; it can never add one.
      if (k.wsAfter) node.wsAfter = true;
      // wave-12: flag nodes that absorbed inline runs so buildComponents
      // emits the 'inline-run-merged' lossy marker (honest approximation:
      // the run's default styling — bold for <strong> etc. — is dropped).
      // Only set when true, keeping legacy node shapes byte-identical.
      if (ownRes.merged > 0) node.inlineMerged = true;
      // wave-21 B-RC9a: flag nodes whose ownText glued ACROSS a kept child
      // (order changed — see scanOwnText's reordered doc) so
      // buildComponents emits the 'inline-run-reordered' lossy marker.
      // Only set when true — legacy node shapes stay byte-identical.
      if (ownRes.reordered) node.inlineReordered = true;
      // wave-32 lane R: the ordered content list, but ONLY once the two
      // walks have proven they describe the same children (see alignRuns).
      // When alignRuns refuses, the node keeps `inlineReordered` alone (if
      // it had it) and buildNode ships the wave-21 bail unchanged.
      //
      // wave-34 lane R: the gate is now `runProto` itself rather than
      // `reordered` — scanOwnText decides the emission population (own text
      // AND at least one kept element child; see its widening banner), and
      // this call site only asks whether the two walks agree. The reorder
      // FLAG keeps its own independent life below: a widened, non-reordered
      // component never had it, so retiring it stays a no-op there.
      if (ownRes.runProto) {
        const aligned = alignRuns(ownRes.runProto, children);
        if (aligned) node.runs = aligned;
      }
      // wave-22 EX2 B-RC4a: the scoped inline-chain collapse. Runs LAST,
      // on the finished node, because it needs the recursed `children` to
      // prove every descendant is a decoration-only inline wrapper (see
      // collapseInlineRun's banner for the full contract). `resolveProps`
      // is supplied only by buildComponents, so every legacy caller of
      // extractBodyTreeNested keeps its pre-wave-22 tree byte-for-byte.
      const collapsed = mergeCtx?.resolveProps
        ? collapseInlineRun(
            node, k.innerHtml,
            mergeCtx.resolveProps(k.tag, k.attrs, ancestors, pos).props,
            mergeCtx.resolveProps, preserve,
          )
        : null;
      if (collapsed) {
        // The subtree becomes ONE text run: the flattened document-order
        // text replaces ownText and the wrapper children disappear.
        node.ownText = collapsed.text;
        node.children = [];
        // The run is now painted in true document order, so the wave-21
        // reorder approximation no longer exists — clearing the flag is
        // the whole point of the fix, not a suppression of a real loss.
        delete node.inlineReordered;
        // wave-32 lane R: the collapse just emptied `children`, so any run
        // list computed above names boxes that no longer exist. Drop it —
        // the flattened run IS the document order now, and `_text` alone
        // expresses it. (The two features can therefore never co-occur on
        // one component, which is why `_decorations` and `_runs` need no
        // precedence rule between them.)
        delete node.runs;
        // Fields buildComponents consumes (see buildNode). All
        // omit-when-absent so non-collapsed nodes keep their exact shape.
        node.decorations = collapsed.decorations;
        node.collapsedProps = collapsed.extraProps;
        // Marks the node as "already proven a decoration-only inline run"
        // so an OUTER collapse one recursion level up can splice this
        // result in rather than refusing on the now-empty children (see
        // guard 3's exception in collapseInlineRun).
        node.chainCollapsed = true;
        if (collapsed.uaHeading) node.uaHeadingProps = collapsed.uaHeading;
        // Only a PARTIAL-coverage decoration (a wrapper that contains part
        // of the run, e.g. the <u> in `the quick <u>brown</u> fox`) is an
        // approximation — its line now paints over the whole run. Full
        // ancestor chains (div>span>span>span) lose nothing and stay clean.
        if (collapsed.partial) node.inlineChainCollapsed = true;
        // wave-22 EX2 SKEPTIC: a line-bearing link's style/thickness/inset/
        // offset was displaced by an outer box and `_decorations` has no
        // field for it — the declaration is gone from the fixture, so say so.
        if (collapsed.declDropped) node.inlineChainDeclDropped = true;
        // The node is no longer empty (:empty must not match it).
        pos.isEmpty = false;
      }
      out.push(node);
    });
    // wave-30 A1/A2 — SECOND PASS: stamp the two facts a `:dir()` / `:empty`
    // compound needs about an element it is NOT the subject of.
    //
    // `subtreeText` is the dir=auto text corpus (HTML §15.3.4's first-strong
    // scan walks the element's whole subtree; see collectSubtreeText, whose
    // own-text-then-children-in-order concatenation this reproduces in O(n)
    // by reusing the children's already-stamped values instead of re-walking).
    // It must exist BEFORE match time because :dir() can be asked about this
    // element from three directions — as the subject, as an ancestor of the
    // subject, or as a preceding SIBLING (`:dir(ltr) + #target`, the whole
    // point of selectors__dir-selector-auto-direction-change-001) — and the
    // matcher holds no tree handle in any of them.
    //
    // Stamped on BOTH the node's own `pos` and its entry in the SHARED
    // `siblings` list, as plain scalars. Deliberately not a back-reference to
    // `pos`: `pos.siblings[i].pos === pos` would make the tree cyclic and
    // break every JSON round-trip through it.
    //
    // Runs after the sibling loop so it sees each node's FINAL text — the
    // wave-22 inline-chain collapse rewrites ownText and empties children.
    // Known ordering limit (documented, not silent): mergeCtx.resolveWhiteSpace
    // and mergeCtx.resolveProps run DURING the loop, so a `:dir(auto)` rule
    // consulted by those two resolvers still sees an unstamped pos and falls
    // back to ltr. No corpus test resolves white-space or a collapse guard
    // through a :dir() selector.
    out.forEach((node, i) => {
      const parts = [node.ownText || ''];
      for (const child of node.children ?? []) parts.push(child.pos?.subtreeText || '');
      const text = parts.filter(Boolean).join(' ');
      node.pos.subtreeText = text;
      siblingList[i].subtreeText = text;
      siblingList[i].isEmpty = node.pos.isEmpty;
      // wave-34 lane R: the third stamped fact, and the reason it must live
      // on the SIBLING entry and not only on `pos`. `:has()` is asked about
      // an element we are not the subject of in exactly the same three
      // directions `:dir()` is, and the corpus proves the sibling direction
      // is real: selectors/nth-child-of-has.html's
      // `div:nth-child(even of :has(span))` renumbers among the SIBLINGS
      // matching S, and evalPseudo's of-selector branch matches each sibling
      // through siblingPositionMeta — which can only forward what the entry
      // carries. `undefined` propagates faithfully (a maxDepth-truncated
      // node stays undecidable for a sibling asker too).
      siblingList[i].kids = node.pos.kids;
      // …and the same three facts onto THIS level's DOM-truthful entry, at
      // its raw index (merged entries were already complete at construction).
      const dk = domKids[kids[i].domIdx];
      if (dk) {
        dk.kids = node.pos.kids;
        dk.isEmpty = node.pos.isEmpty;
        dk.subtreeText = text;
      }
    });
    return out;
  }
  return recurse(inner, [], 0);
}

/**
 * swarm-003 Bug 3 (selectors__nth-child-of-pseudo-class): scan HTML
 * `<script>` blocks for `customElements.define(...)` calls and return
 * the Set<string> of defined tag names. Consumed by evalPseudo's
 * `:defined` branch via the ctx parameter.
 *
 * The WPT corpus uses static `customElements.define('my-element', ...)`
 * literals — no dynamic registration via variables, no eval-style
 * gymnastics — so a simple regex scan is sufficient. We tolerate both
 * single- and double-quoted tag names, and any whitespace inside the
 * argument list. Returns an empty Set when no script blocks register
 * any custom elements.
 *
 * Exported for unit testing.
 */
export function collectDefinedTags(html) {
  const out = new Set();
  if (!html) return out;
  // Pull every <script>…</script> body (the registration calls live
  // inside the inline script bodies; external scripts can't be loaded
  // by the extractor anyway).
  const scriptRe = /<script\b[^>]*>([\s\S]*?)<\/script[^>]*>/gi;
  let m;
  while ((m = scriptRe.exec(html)) !== null) {
    const body = m[1];
    // customElements.define('tag-name', …) — quoted name in arg 0.
    const defineRe = /customElements\s*\.\s*define\s*\(\s*['"]([A-Za-z][\w-]*)['"]/g;
    let d;
    while ((d = defineRe.exec(body)) !== null) {
      // Custom element tag names must contain a hyphen per HTML §4.13.1;
      // accept anything matching the literal form regardless and let
      // evalPseudo's `:defined` branch handle the always-true built-in
      // case via its own tag-shape check.
      out.add(d[1].toLowerCase());
    }
  }
  return out;
}

/**
 * Synthetic document-element ancestor injected at match time (NOT in the
 * public ancestors chain). Selectors-4 §3.4.3 + §6.4.1 carve-out: the
 * document root has `isRoot:true` so child-indexed pseudos all match. We
 * model the root as if it were the sole sibling of itself (sibCount/
 * typeCount = 1) so anyone evaluating `:nth-of-type(1)` on it sees the
 * expected truth.
 *
 * wave-36 M7: the tag is `html`, not `:root`. `:root` is a pseudo-class and
 * carries no type selector, so it still matches this entry through
 * `pos.isRoot` (evalPseudo's `root` branch) — but a bare `html` TYPE
 * compound (`html body div`, `html > body > div`) now matches too, which
 * the old `:root`-tagged sentinel could never do.
 */
const ROOT_SENTINEL_ANCESTOR = {
  tag: 'html', attrs: {},
  pos: {
    isRoot: true,
    sibIndex: 0, sibCount: 1,
    sibTypeIndex: 0, sibTypeCount: 1,
    isEmpty: false,
  },
};

/**
 * wave-36 M7 (BODY-ANCESTOR): the second synthetic ancestor — `<body>`.
 *
 * The component walker's ancestor chain starts INSIDE body (buildComponents:
 * every component IS a body descendant), so a top-level component arrived at
 * `propsForElement` with `ancestors = []`. Prepending only the root sentinel
 * meant `body` could never appear in a chain, and therefore EVERY rule whose
 * non-rightmost compound is `body` — `body > div`, `body span`, `body * + *`,
 * the single most common WPT scoping idiom — silently matched nothing. The
 * declarations under test (widths, borders, margins on `body > div`) were
 * dropped wholesale, which is why css-logical's float/clear tests render the
 * right floats inside the wrong (border-less, full-bleed) container.
 *
 * Per HTML §13.2.6.4 the parser always synthesises `<head>` and `<body>` as
 * the document element's only two element children, so body is honestly the
 * 2nd of 2 element children (`sibIndex: 1, sibCount: 2`) and the only `body`
 * of its type (`sibTypeIndex: 0, sibTypeCount: 1`). Attributes are left empty:
 * the static extractor has no obligation to model `<body class>` (script-
 * mutated in the corpus' only such test), and an empty bag is strictly closer
 * to the truth than "no body exists at all".
 */
const BODY_SENTINEL_ANCESTOR = {
  tag: 'body', attrs: {},
  pos: {
    isRoot: false,
    sibIndex: 1, sibCount: 2,
    sibTypeIndex: 0, sibTypeCount: 1,
    isEmpty: false,
  },
};

/**
 * The document scaffolding every component hangs under, in document order
 * (outermost first) — exactly the shape `selectorMatchesPseudoElement`
 * consumes right-to-left. Frozen so no caller can mutate the shared bag.
 */
const DOCUMENT_SENTINEL_ANCESTORS = Object.freeze([
  ROOT_SENTINEL_ANCESTOR, BODY_SENTINEL_ANCESTOR,
]);
export { DOCUMENT_SENTINEL_ANCESTORS };

// Bug 1 fix (pilot-001 / css-color/color-001): the previous implementation
// dropped <p>, <noscript>, <script>, <h1..h3> via an INSTRUCTION_TAGS set
// because the WPT corpus typically uses `<p>...</p>` as instruction prose.
// That filter was too aggressive — WPT also routinely uses
// `<p class="test">…</p>` as the *styled subject* (canonical pattern in
// css-color, css-text, css-fonts, css-pseudo, css-text-decor). Dropping
// styled <p> nodes silently destroyed the property under test.
//
// We now rely on the existing `matchedRules === 0 && Object.keys(props) === 0`
// branch in the fixture builder to recognise true instruction nodes — those
// land as a 100x100 placeholder, exactly the cost we previously paid for
// the wrongly-dropped subject. <script> bodies still don't render in any
// SDUI host but that's a downstream renderer concern, not an extractor one.
//
// Head-only elements remain skipped during the no-<body> fallback (Bug 5);
// see `HEAD_ONLY_TAGS` below.
// wave-16 POST-LOAD: exported so post-load-extract.mjs can replicate the
// EXACT same head-only filter inside the live browser walk — the two
// traversals must agree element-for-element or the computed-style overlay
// would land on the wrong components (see post-load-extract.mjs's
// element-mapping contract).
export const HEAD_ONLY_TAGS = new Set([
  'title', 'meta', 'link', 'style', 'script', 'base', 'head',
]);

// Bug 1 fix (css3-counter-styles-101): tags considered "generic" — i.e.
// the renderer treats them as a plain styled container. We DON'T emit
// `_tag` for these so the existing hand-authored visual-test/fixtures/*
// fixtures stay byte-identical (their components are conceptually <div>s,
// just without the field). Every other tag carries real semantic weight
// (list markers, paragraph rhythm, table cell layout, headings, etc.)
// and is faithfully forwarded as `_tag` to the platform renderers.
//
// ── wave-31 lane S: `span` LEAVES the set ────────────────────────────────
//
// The original reasoning ("div and span are the IR's default container
// shapes") holds for <div> and is FALSE for <span>. A <div> is a block
// container and the renderers' default box IS a block container, so
// withholding the tag costs nothing. A <span> is an INLINE box
// (css-display-3 §2.1 — outer display `inline`, the UA default for every
// phrase element), and the default box is not: web's harness mapTag fell
// through to `<div>`, so every span that SURVIVED the inline merge was
// re-parented as a block container and the inline formatting context
// around it was destroyed — the exact opposite of the source markup.
//
// The web harness has been ready for this since wave-26: 'span' is in
// apps/web-harness/src/sdui/ComponentRenderer.tsx's TAG_ALLOWLIST with a
// "Bug 2" banner explaining that CSS-Containment-2's
// `content-visibility: hidden` hinges on the non-atomic-inline
// distinction — but the allowlist entry was dead code, because this set
// meant `_tag: 'span'` was never put on the wire for it to accept.
// Same for the wave-26 WWS inter-sibling whitespace separator, whose
// INLINE_LEVEL_SOURCE_TAGS read (`isInlineLevelSibling`) lists 'span'
// first and could never see one.
//
// SCOPE — this changes NOTHING about the merge machinery. A pure-inline
// `<span>` with no attributes and no tag-targeting rule is still absorbed
// into the parent's `_text` by isPureInlineMergeable / collapseInlineRun
// and never becomes a component at all. The change is only that a span
// which survives AS a component now carries its identity, exactly like
// the <p>/<h1>/<td>/<a> that always did.
//
// NATIVES ARE INERT to the new value (audited wave-31 lane S, both
// runtimes, every `_tag` / `meta.sourceTag` consumer):
//   • ListStyleExtractor.uaMarkerDefault / ListMarkerResolver.uaDefault —
//     switch over {ol, ul, menu, dir}; 'span' → null, same as absent.
//   • ListItemMarkerGate.rendersOwnLeadingMarker — only `== "li"`
//     short-circuits; 'span' takes the identical path as null.
//   • uaVerticalBlockMargins / UABlockMargin.vertical(forTag:) — the UA
//     block-margin table's `else` branch is (0, 0), which is what an
//     absent tag already resolved to (HTML §15.3 declares no margin for
//     phrase content).
//   • UAWidgetsResolve.kindFor / UAWidgetIntrinsics.kind — keyed on the
//     widget tag set (input/select/button/textarea/option); 'span' → nil.
//   • InlineAtomFlow.isAtom — `t in WIDGET_TAGS || t == "a"`; 'span' →
//     false, byte-identical to the null-tag early return.
//   • ComponentRenderer list-parent branches — `parentTag in {ol,ul,
//     menu,dir}` and `child._tag == "li"`; both false.
//   • ContentsUnboxing forwards `_tag` verbatim (no branch).
// So the fixture drift below is a WEB-side behaviour change only; the
// natives read the same field and reach the same code path they reached
// with no field at all.
const GENERIC_WRAPPER_TAGS = new Set(['div']);

// ── wave-22 BR-LINE-CONTEXT: inline-level tags for the <br> height rule ──────
//
// wave-21 B-RC1 modeled EVERY bare <br> as a 20px empty line box. That is
// only correct for a br that STARTS its line (a blank line — the
// abspos-containing-block-outside-spanner case B-RC1 was pinned against:
// five consecutive body-level <br>s = five 20px lines in the cached ref).
// Two measured wave-21 regressions came from applying it unconditionally:
//   - css-text empty-span-001 (0.908 → 0.760 all three platforms): every
//     `<span>…</span><br>` line rendered PLUS a 20px blank line — but per
//     CSS 2.1 §9.5 a br after inline content merely ENDS the current line
//     box (the ref paints 12 single-spaced lines, no blanks).
//   - css-flexbox flex-abspos-staticpos-justify-self-001 (0.98 → 0.93 all
//     three): the `<br clear:both>` float-row breaks gained a 20px stacked
//     box BETWEEN float rows — but a clear-br's vertical contribution is
//     CLEARANCE geometry (CSS 2.1 §9.5.2), which the wave-19 float-row
//     machinery already models (FloatRowPacking strut, pin P6; its contract
//     comment says "clear markers render as 0-height divs"). Giving the
//     marker its own height double-counts the line box.
//
// The line-context rule (buildNode + its two sibling loops):
//   height 0  — br preceded by in-flow inline-level content on its line
//               (it ends the line, contributing no height of its own), OR
//               br carrying a `clear` declaration (childless clear-br =
//               the row-break marker; engines own its geometry).
//   height 20 — br starting its line (blank line — the B-RC1 case).
//
// This set answers "does this sibling put inline content on the open line?".
// HTML phrase/replaced content the corpus uses; display/float/position
// overrides are consulted separately in buildNode (a `span {display:block}`
// sibling ends the line instead). Deliberately broader than
// INLINE_MERGE_TAGS (which gates text ABSORPTION, a stricter contract).
const INLINE_LEVEL_TAGS = new Set([
  'span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup',
  'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo',
  'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea',
  'output', 'meter', 'progress', 'ruby', 'rt', 'rb',
]);

// ── wave-26 lane WWS: the inter-sibling whitespace marker (the ws-after wire) ─
//
// MEASURED PROBLEM (corpus-v5.0, filter-effects 0.9279 mean):
//   backdrop-filter-clip-rect-2 stacks three `display:inline-block` boxes
//   per row, written one per source line —
//     <div class="no-bf">
//       <div class="box"></div>
//       <div class="box"></div>
//       <div class="box"></div>
//     </div>
//   The browser-ref collapses each newline+indent run to ONE space advance,
//   so the ref's boxes sit at x = 0, 104.5, 209. Our flat component wire
//   dropped those text nodes entirely, the composed canvas packed the boxes
//   FLUSH at 0, 100, 200, and boxes 2 and 3 landed 4.5 and 9 px left of the
//   ref — the whole 0.924 web gap, with the same displacement shifting
//   row-wrap boundaries on wider fixtures.
//
//   MEASURED, not inferred (puppeteer, ref page under the real
//   capture-browser-ref.mjs injection — Inter @16px + line-height 1.25):
//   the collapsed space advance is 4.5px, giving ref x = 0 / 104.5 / 209.
//   Do NOT reuse the wave-20 UAWidgetIntrinsics.atomGapPx 4.16 figure here:
//   that constant was measured on a different face and does not describe
//   this stage. The renderer injects a REAL text node and lets the browser
//   measure the advance in the capture's own font, so the number above is
//   documentation, never arithmetic the code depends on.
//
// WIRE CONTRACT (`_role: 'ws-after'` → IR v2 `meta.role`): the earlier of
// two adjacent siblings is stamped when SOURCE WHITESPACE separated them.
//
//   * CHANNEL CHOICE. `meta.role` is the marker channel the v2 schema
//     already declares "open-ended string by design so future markers need
//     no schema change" (schema/ir-v2.schema.json), and it is already
//     multi-valued here — 'body-root' (the synthetic body component) and
//     'line-break' (a <br>). Riding it means this lane needs NO converter,
//     schema, or IR-type change: `_role` is read by CssParsing.kt, emitted
//     as `meta.role` by IRWireV2.kt, and forwarded verbatim by the web
//     decoder today. A first-class `meta.wsAfter` boolean would be a
//     cleaner name but is a six-file wire change (converter model + parser
//     + IR model + v2 serializer + schema + runtime types) and belongs to a
//     wire lane, not here. Documented follow-up: promote it when that lane
//     runs; the harness reads through ONE predicate (isWsAfterMarked in
//     apps/web-harness/src/sdui/ComponentRenderer.tsx), so it is a
//     one-line swap on each side.
//   * PRECEDENCE, stated rather than silent: a component that ALREADY owns
//     a role keeps it. Only 'line-break' can collide (body-root is the
//     synthetic root and never has a sibling), and a <br> is never an
//     inline ATOM in the consumer's predicate — it ends a line rather than
//     occupying inline space — so the withheld marker costs nothing. The
//     collision is asserted by a unit test rather than left to inspection.
//   * DIRECTION: stamped on the EARLIER sibling ("whitespace follows me"),
//     matching the renderer hook's (prev, next) gap call.
//   * NEVER stamped on the LAST sibling: trailing whitespace before the
//     parent's close tag separates nothing, and a marker there would be a
//     standing invitation to invent a trailing space.
//   * OMIT-WHEN-ABSENT: fixtures whose siblings are flush carry nothing, so
//     every hand-authored fixture (fixtures/visual-test.json,
//     fixtures/properties/**) — which this extractor never touches anyway —
//     and every re-extracted flush-source WPT fixture stay byte-identical.
const WS_AFTER_ROLE = 'ws-after';

/**
 * Stamp the wave-26 `ws-after` marker on one emitted component.
 *
 * @param cmp        the fixture component object being emitted (mutated).
 * @param node       its tree node — `node.wsAfter` is walkChildren's fact.
 * @param hasNextSib whether a following SIBLING component exists; the
 *                   marker describes a GAP, so the last child never gets it.
 * Returns the marker actually applied (or null), so callers and tests read
 * the decision instead of re-deriving it.
 */
export function stampWsAfter(cmp, node, hasNextSib) {
  // No following sibling → no gap to describe (see the banner's LAST rule).
  if (!hasNextSib) return null;
  // The walker found no collapsible whitespace between the two elements —
  // the source packed them flush and the renderers must too.
  if (!node?.wsAfter) return null;
  // PRECEDENCE, stated not silent: an existing role wins. Today that is
  // only 'line-break' (a <br>), which is never an inline atom downstream,
  // so nothing is lost — but say it out loud rather than overwrite.
  if (cmp._role) return null;
  cmp._role = WS_AFTER_ROLE;
  return WS_AFTER_ROLE;
}

// ── wave-20 lane W1: widget-identity attributes (the meta.attrs wire) ────────
//
// RC1 (the css-ui 7-test family): walkChildren parses every element's
// attribute map for selector matching, but buildNode never FORWARDED it —
// `<input type=checkbox checked>` reached the runtimes as a bare `_tag`
// and every platform painted an attribute-less widget (web additionally
// demoted it to <div>, RC2). The CROSS-LANE WIRE CONTRACT (all wave-20
// lanes assume EXACTLY this): for the form/widget tags below, the fixture
// carries `_attrs` — an object holding ONLY the present-in-source
// attributes among WIDGET_ATTR_KEYS — and the converter forwards the
// object VERBATIM as IR v2 `meta.attrs` (the same droppable-hint channel
// as `_tag` → `meta.sourceTag`; schema/spec/05-versioning.md sanctions a
// new omit-when-absent key inside `meta` as a v2-additive change).

// The form/widget tags whose browser chrome is attribute-dependent (HTML
// §4.10 forms + the a/meter/progress widgets the css-ui corpus styles).
// Exactly the wire-contract set — runtimes mirror it byte-for-byte
// (runtimes/web/src/renderer/WidgetAttrs.ts WIDGET_TAGS).
export const WIDGET_ATTR_TAGS = new Set([
  'a', 'button', 'input', 'textarea', 'select', 'option', 'meter', 'progress',
]);

// The attribute allow-list: the identity-bearing attributes that change
// WHICH chrome the browser paints (input `type`), its state (`checked`,
// `selected`, `disabled`), its content (`value`, `alt`), or its geometry
// (`size`, `multiple`, `min`/`max`). Everything else (id/class/style) is
// selector fuel already consumed by propsForElement — never forwarded.
export const WIDGET_ATTR_KEYS = [
  'type', 'value', 'checked', 'multiple', 'size', 'alt',
  'min', 'max', 'selected', 'disabled',
];

// HTML boolean attributes (HTML §2.3.2): PRESENCE means true — '', the
// attribute's own name, and any other value are all "true"; absence is the
// only "false". The wire therefore carries literal `true`, never a string.
export const WIDGET_BOOLEAN_ATTR_KEYS = new Set([
  'checked', 'multiple', 'selected', 'disabled',
]);

// ── wave-27 lane CBAKE: the LIST-ORDINAL attribute lane ─────────────────────
//
// `<ol start='1860'>` is the other half of a list marker (the first is
// `list-style-type`, which already rides as an IR property). Nothing on the
// wire carried it, so the web harness — the one platform that renders a
// REAL <ol> and lets Blink synthesise ::marker — numbered every list from 1
// while the source said 1860 (css/css-counter-styles/*/css3-counter-styles-
// {102,107,117,159}). Forwarding it verbatim fixes web natively; the two
// natives get the resolved marker STRING instead (counter-style-bake.mjs).
//
// WHY A SEPARATE SET, not three more entries in WIDGET_ATTR_TAGS: that set
// is not just an attribute allow-list. runtimes/web mirrors it as
// WIDGET_TAGS, and the harness ComponentRenderer keys three OTHER
// behaviours off it — WPT-mode tag passthrough, the `inert` + `tabIndex:-1`
// decoration, and an UNCONDITIONAL ' ' separator between adjacent widget
// siblings. Adding `ol` there would inject that separator between every
// pair of adjacent <ol>s in the whole corpus. The lanes stay disjoint.
export const LIST_ATTR_TAGS = new Set(['ol', 'li']);

// `start` is the ordered list's counter origin (HTML §4.4.5); `value` is a
// per-item override that also RESETS the sequence (HTML §4.4.8). Both are
// forwarded as the VERBATIM source string — `value` is already a
// WIDGET_ATTR_KEYS member whose non-meter/progress typing is "string", and
// keeping `start` in the same lane avoids a second numeric-typing rule for
// one attribute the DOM re-parses anyway.
export const LIST_ATTR_KEYS = ['start', 'value'];

// ── wave-20 fix 5: the non-HTML-namespace gate ──────────────────────────────
//
// Widget identity (`_tag` → meta.sourceTag + `_attrs` → meta.attrs) is only
// meaningful for elements in the XHTML namespace: browsers give a
// createElementNS('not-html', 'input') element NO widget chrome — it renders
// as a plain unknown element (appearance-auto-non-html-namespace-001 styles
// them into empty 1em inline-blocks), so forwarding `_tag: 'input'` made the
// natives paint full UA replicas the browser-ref never shows.
//
// WHERE THE NAMESPACE IS KNOWN, honestly:
//   • the STATIC path (this file's regex walker over authored test HTML) has
//     it by construction: the HTML parser puts every element the walker can
//     reach in the HTML namespace — non-HTML namespaces arise only from
//     createElementNS in script (not executed here) or inside <svg>/<math>
//     foreign-content subtrees (which carry no widget tags in the corpus and
//     would be a walker blind spot far beyond widget identity);
//   • the SERIALIZED-DOM structure path (post-load-extract.mjs, this wave)
//     LOSES namespaces in the outerHTML → regex re-parse round-trip (the
//     risk its header notes), so the in-browser serializer — which still
//     holds the live DOM and therefore el.namespaceURI — stamps every
//     non-XHTML element with the marker attribute below BEFORE serializing.
// buildNode() suppresses `_tag`/`_attrs` when the marker is present, giving
// foreign elements the generic-container treatment (the honest mirror of the
// browser's no-chrome rendering).
export const FOREIGN_NS_MARKER_ATTR = 'data-sc-foreign-ns';

// ── wave-22 EX2 A-RC1 part 2: rule-less widgets are NOT scaffolding ─────────
//
// buildNode's "empty node" branch paints a 100x100 placeholder when an
// element matched no rule, carries no inline style, and has no own text —
// the honest reading for a bare `<div>` wrapper. It is the WRONG reading
// for a form control: `<select id=drop-down-select><option>select</option>
// </select>` in css-ui/appearance-menulist-button-001 is deliberately
// EXCLUDED from that test's only rule (`#container > *:not(#drop-down-
// select)`), so once `:not()` started matching (above) the select became
// the one child with an empty property bag — and a 100x100 drop-down is a
// bigger divergence from the ref than the widget it replaced.
//
// Per HTML Rendering §15.5 every element below has UA chrome with an
// INTRINSIC size (a select sizes to its longest option, an input to its
// `size` attribute, a meter/progress to 5em×1em); rendering it with NO
// width/height is what every browser does and what all three runtimes
// already do off `meta.sourceTag` + `meta.attrs`. So these tags opt OUT of
// the placeholder and keep their empty bag.
//
// Deliberately NARROWER than WIDGET_ATTR_TAGS: `a` and `option` are plain
// inline/text elements with no chrome and no intrinsic box — an empty one
// really is scaffolding, so they keep the placeholder. Foreign-namespace
// elements (FOREIGN_NS_MARKER_ATTR) also keep it: they have no UA chrome
// at all (see the marker's banner), so an intrinsic size would be fiction.
export const INTRINSIC_WIDGET_TAGS = new Set([
  'input', 'select', 'textarea', 'button', 'meter', 'progress',
]);

// A floating-point number token (HTML §2.3.4.2 valid floating-point
// number, plus scientific notation) — the gate for the numeric wire lanes.
const WIDGET_NUMERIC_RX = /^-?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/;

/**
 * Build the `_attrs` payload for one element, or null when the element is
 * not a widget tag / carries none of the allow-listed attributes (so bare
 * widgets and every non-widget fixture stay byte-identical pre/post W1).
 *
 * Value typing per the wire contract:
 *   - booleans (WIDGET_BOOLEAN_ATTR_KEYS) → literal `true` when present;
 *   - `min`/`max` → Number when the source text is numeric (HTML §4.10.13
 *     reflects them as floats on meter/progress and as varying types on
 *     input — a numeric wire value is losslessly reusable everywhere);
 *   - `value` → Number ONLY on meter/progress (§4.10.13/14: float-valued
 *     reflections); on <input>/<option>/<button> value is genuinely a
 *     STRING (a text field's content) and stays verbatim;
 *   - everything else (type/size/alt) → the raw source string, verbatim.
 *
 * Exported so the unit tests pin the contract table exactly.
 */
export function widgetAttrsFor(tag, attrs) {
  // wave-27 lane CBAKE: the list-ordinal lane is disjoint from the widget
  // lane (see LIST_ATTR_TAGS) — same `_attrs` envelope, its own tag set,
  // its own two keys, all verbatim strings. Handled first and returned
  // early so no widget typing rule can ever reach an <ol>/<li>.
  if (tag && LIST_ATTR_TAGS.has(tag)) {
    const list = {};
    // Allow-list order, present-only, verbatim — the same three contract
    // rules the widget lane below follows.
    for (const key of LIST_ATTR_KEYS) {
      if (attrs && key in attrs) list[key] = String(attrs[key]);
    }
    return Object.keys(list).length > 0 ? list : null;
  }
  // Non-widget tags never emit attrs — widget identity only (contract).
  if (!tag || !WIDGET_ATTR_TAGS.has(tag)) return null;
  const out = {};
  // Walk the allow-list (not the source map) so output key ORDER is the
  // contract's order — stable fixture bytes independent of authored order.
  for (const key of WIDGET_ATTR_KEYS) {
    // Present-only contract: absent source attribute ⇒ absent wire key.
    if (!attrs || !(key in attrs)) continue;
    // Boolean lane: presence IS the value (see WIDGET_BOOLEAN_ATTR_KEYS).
    if (WIDGET_BOOLEAN_ATTR_KEYS.has(key)) { out[key] = true; continue; }
    // walkChildren stores '' for bare attributes and raw strings otherwise.
    const raw = String(attrs[key]);
    // Numeric lanes: min/max everywhere; value on meter/progress only.
    const numeric = (key === 'min' || key === 'max')
      || (key === 'value' && (tag === 'meter' || tag === 'progress'));
    // Numeric text converts; non-numeric text stays verbatim (documented
    // "numbers where numeric" clause — never a silent NaN).
    out[key] = (numeric && WIDGET_NUMERIC_RX.test(raw.trim()))
      ? Number(raw.trim())
      : raw;
  }
  // No qualifying attribute → no `_attrs` field at all (omit-when-empty,
  // the same rule every other renderer-hint field follows).
  return Object.keys(out).length > 0 ? out : null;
}

// ── wave-36 lane M1: the REPLACED-ELEMENT SOURCE lane ───────────────────────
//
// THE GAP THIS CLOSES. `object-fit` / `object-position` are the CSS that
// decides HOW a replaced element's content is scaled inside its box — and
// until this wave nothing on the wire carried the content at all. The
// extractor forwarded `_tag: 'img'` and the harness substituted a fixed
// 100×100 grey-disc placeholder (ComponentRenderer's PLACEHOLDER_IMG_SRC,
// "the wave-9 IR content-contract gap"), so every capture scaled the WRONG
// image: right box, right keyword, wrong pixels. MEASURED on the wave-35
// full-corpus web map (tools/titan/results/webmap-v1.json rank 1): 154 of
// the 196 scored css-images object-* cells fail, and the family splits by
// SOURCE ELEMENT, not by keyword — `-i` (<img src>) 42/43 fail, `-e`
// (<embed src>) 30/42, `-o` (<object data>) 30/42, `-p` (<video poster>)
// 30/42. The ink numbers name the mechanism twice over: the `-e/-o/-p`
// captures carry 0.945% ink against the ref's 10.103% (the element painted
// NOTHING — those tags are outside the harness allowlist and demote to a
// bare <div>), while the `-i` captures carry 10.326% against a ref's 1.883%
// on `object-fit: none` (the 100×100 placeholder floods a 48×32 box the
// real 16×8 image would barely dot).
//
// WHAT RIDES: the corpus-relative PATH of the source file, in `_attrs.src`,
// which the converter already forwards verbatim as IR v2 `meta.attrs` (an
// opaque JsonObject — no converter change, no schema change; the same
// additive channel wave-20 opened for widget identity). A PATH and not a
// payload, exactly as `fontFaces[].src` does (schema/spec/01-envelope.md
// §5): inlining these as data URIs would have cost ~4.6 MB of extra JSON on
// css-images alone (the corpus reuses eight support images across ~2,600
// element references, and percent-encoding is 3× the raw bytes), and the
// consumer that needs the file already has a corpus route.
//
// CANONICAL KEY `src`, whatever the HTML attribute was spelled: <img>/
// <embed> use `src`, <object> uses `data` (HTML §4.8.7), <video> uses
// `poster` (§4.8.9 — the poster frame IS what a still capture paints, and
// it is object-fit-scaled like any other replaced content). Normalising is
// in keeping with the `_attrs` contract, which already retypes rather than
// mirrors (`checked` → literal `true`, `min`/`max` → Number).
//
// DISJOINT from WIDGET_ATTR_TAGS and LIST_ATTR_TAGS by construction — no
// tag appears in two of the three sets, so the merge in buildNode can never
// have a key collision. `<input type=image src=…>` is deliberately NOT here:
// `input` is a widget tag with its own lane, and a form control's image is a
// different measurable.
export const REPLACED_SRC_TAGS = new Map([
  ['img',    'src'],
  ['embed',  'src'],
  ['object', 'data'],
  ['video',  'poster'],
]);

/**
 * Build the `{ src }` half of `_attrs` for one replaced element, or null.
 *
 * PURE — the value is the author's payload VERBATIM (relative path, '/'-
 * rooted path, or data: URI). Corpus resolution, the format gate and the
 * on-disk existence check all happen later, in resolveReplacedSrc(), for the
 * same reason resolveFontFaces is split off scanFontFaces: the scan half
 * stays unit-testable without a corpus on disk.
 *
 * Exported so the unit tests pin the tag→attribute table exactly.
 */
export function replacedSrcFor(tag, attrs) {
  if (!tag || !attrs) return null;
  const key = REPLACED_SRC_TAGS.get(tag);
  if (!key) return null;                      // not a replaced element we deliver
  const raw = attrs[key];
  if (typeof raw !== 'string') return null;   // absent attribute ⇒ absent wire key
  const v = raw.trim();
  // A bare `src=""` names the document itself (HTML §4.8.4.1) — never an
  // image. Omit-when-empty, same rule as every other renderer-hint field.
  return v ? { src: v } : null;
}

/** Image formats the replaced-source lane will deliver, as a CLOSED table —
 *  the same discipline as FONT_FORMATS and for the same reason: an
 *  `<object data="x.pdf">` or `<embed src="y.html">` is not an image, and
 *  admitting one would put a path on the wire that the harness's image
 *  route refuses to serve. Byte-parallel with IMAGE_CONTENT_TYPES in
 *  apps/web-harness/vite.config.ts (the route that resolves these paths);
 *  SVG is IN here although the CSS url() inliner above excludes it, because
 *  that exclusion is about percent-encoding a text payload into a fixture,
 *  which this lane does not do. */
export const REPLACED_IMAGE_FORMATS = new Set([
  'png', 'gif', 'jpg', 'jpeg', 'webp', 'bmp', 'ico', 'svg', 'avif',
]);

/** Lossy reason for a replaced element whose source could not be delivered.
 *  DELIBERATELY NOT 'requires-bundled-asset' — see the roll-up in
 *  inlineFixtureAssets for why reusing that tag would silently move tests
 *  out of the scoring denominator. */
export const REPLACED_SRC_LOSSY_REASON = 'requires-replaced-source';

/**
 * Resolve one author-written replaced-element source against the corpus.
 *
 * Returns the corpus-relative path the harness route can serve, a `data:`
 * URI verbatim (already self-contained — nothing to deliver), or `null` when
 * the reference is UNDELIVERABLE. Every null is a deliberate decline, and
 * the caller turns it into the honest 'requires-bundled-asset' marker rather
 * than leaving a path no platform can fetch on the wire:
 *   1. http(s)/protocol-relative/`{{…}}` template — Rule 37's remote domain;
 *   2. the resolved path escapes the corpus (wptRelativePath);
 *   3. the extension is not an image format we serve;
 *   4. the file is not on disk — emitting it would hand the harness a
 *      guaranteed 404, the exact failure this channel exists to end.
 *
 * `baseDir` is the directory of the document the markup came from; a leading
 * '/' is WPT-server-root-relative, the same convention rel="match",
 * <link rel=stylesheet>, inlineUrlsInValue and resolveFontFaces all use.
 */
export async function resolveReplacedSrc(payload, baseDir) {
  const v = String(payload ?? '').trim();
  if (!v) return null;
  // data: is already the content — pass it straight through (the corpus does
  // author inline images, and there is nothing for a route to resolve).
  if (/^data:/i.test(v)) return v;
  if (urlPayloadOutOfScope(v)) return null;                 // (1) remote / template
  // Query + fragment are addressing, not path: strip before resolution so
  // `support/x.png?foo` still finds the file (the harness route strips the
  // same way on the serving side).
  const pathOnly = v.split(/[?#]/)[0];
  if (!pathOnly) return null;
  const abs = pathOnly.startsWith('/')
    ? join(WPT_DIR, pathOnly.slice(1))
    : resolve(baseDir, pathOnly);
  const rel = wptRelativePath(abs);
  if (!rel) return null;                                    // (2) escapes the corpus
  const ext = /\.([A-Za-z0-9]+)$/.exec(rel)?.[1]?.toLowerCase();
  if (!ext || !REPLACED_IMAGE_FORMATS.has(ext)) return null; // (3) not an image
  try {
    await fs.access(abs);
  } catch {
    return null;                                            // (4) not on disk
  }
  return rel;
}

/**
 * Allow-list of structural / child-indexed pseudo-classes we evaluate. Every
 * other `:foo` selector still causes compoundMatches() to return null (so the
 * outer selectorMatches bails out). The set mirrors Selectors-4 §6.4–§6.5
 * plus `:root` (§3.4.3) and the empty-tree pseudo `:empty` (§9).
 *
 *   :root, :first-child, :last-child, :only-child, :empty
 *   :first-of-type, :last-of-type, :only-of-type
 *   :nth-child(An+B[, of S]?), :nth-last-child(An+B)
 *   :nth-of-type(An+B), :nth-last-of-type(An+B)
 *
 * Bug 2 fix (selectors__child-indexed-no-parent): the prior implementation
 * outright rejected anything matching `/[\[:]/`, which silently dropped every
 * rule whose left compound was `:root:<child-indexed>`. We now strip a
 * supported pseudo from the compound, remember it, and check it later
 * against the element's position metadata.
 */
const SUPPORTED_PSEUDOS = new Set([
  'root', 'first-child', 'last-child', 'only-child', 'empty',
  'first-of-type', 'last-of-type', 'only-of-type',
  // swarm-003 Bug 3 (selectors__nth-child-of-pseudo-class): :defined matches
  // every built-in HTML element + any custom element whose tag name has been
  // passed to customElements.define(). For non-custom tags (containing no
  // hyphen) we always answer true; for custom tags we consult the
  // ctx.definedTags set assembled at extraction time by scanning <script>
  // blocks. See evalPseudo() for the eval path.
  'defined',
]);
// Pseudos that take a parenthesised An+B argument. Tracked separately because
// they have to be tokenised differently (`:nth-child(2n+1)` vs `:first-child`).
const SUPPORTED_FUNCTIONAL_PSEUDOS = new Set([
  'nth-child', 'nth-last-child', 'nth-of-type', 'nth-last-of-type',
  // wave-22 EX2 A-RC1 (css-ui__appearance-menulist-button-001, web-ref
  // 0.554 vs the 1.000 alias band its eleven sibling appearance-* tests
  // hold): the ONLY rule in that test is
  //   `#container > *:not(#drop-down-select) { appearance: menulist-button }`
  // and `not` was absent from this set, so parseCompound flagged the whole
  // compound unsupported → the rule matched NOTHING → all 13 widgets fell
  // into buildNode's `matchedRules === 0 && no props` 100x100 placeholder
  // branch with no Appearance at all (the wave21-final per-test IR at
  // runs/wave21-final/sections/css-ui/per-test-ir/…menulist-button-001.json
  // shows empty `properties` where …button-001.json shows `Appearance`).
  // Selectors-4 §5.1 defines :not() as the negation pseudo-class taking a
  // <complex-selector-list>; we support the SINGLE-COMPOUND subset (which
  // is 100% of the WPT corpus's usage) — see the `not` branch in
  // parseCompound for the argument validation and evalPseudo for the
  // negation itself.
  'not',
  // wave-34 lane R — `:has()`, the relational pseudo-class (Selectors-4 §5.4:
  // ":has() matches an element if any of the relative selectors in its
  // argument, when absolutised against that element, match at least one
  // element"). It was the largest single unmodelled-selector population left
  // in the corpus: 28 bucket-A tests carry a `:has(` token, and every one of
  // them fed countUnsupportedRules → the wave-30 A3 post-load route, i.e. we
  // paid a browser page-load to answer a question about STATIC MARKUP.
  //
  // SUPPORTED SUBSET (what the `has` branch in parseCompound validates):
  // an OPTIONAL leading combinator — none (descendant, the §5.4 default),
  // `>`, `+`, `~` — followed by exactly ONE compound. That is the whole of
  // the corpus's reachable usage: `:has(> span)` / `:has(> .a)`
  // (selectors/has-style-sharing-001…006), `:has(span)` (…-007, inside a
  // `:not()`), `:has(.c)` / `:has(span)` as an `of S` argument
  // (selectors/nth-child-of-has + the two invalidation twins), and
  // `:has(*:dir(ltr))` (selectors/dir-pseudo-in-has).
  //
  // REFUSED, honestly, each feeding the post-load route rather than a guess:
  // a nested `:has()`; a pseudo-element in the argument (§5.4 forbids it);
  // more than one compound (`:has(~ .item > :nth-child(2))` —
  // selectors/invalidation/has-with-nth-child-sibling-remove); and a
  // relative-selector LIST, which parseCss's paren-blind comma split already
  // hands us as an unbalanced fragment (the wave-22 depth guard below).
  'has',
  // wave-30 A1 (selectors__dir-selector-ltr-001, web-ref 0.395 against the
  // filled-green-square ref): the ONLY rule that paints the subject green is
  // `div:dir(ltr) { background-color: green }`, and `dir` was absent from
  // this set — so parseCompound flagged the compound unsupported, the rule
  // matched NOTHING, and the div kept the `div { background-color: red }`
  // base (the wave29-final per-test IR at
  // runs/wave29-final/sections/selectors/per-test-ir/…ltr-001.json carries
  // BackgroundColor red where the ref is green).
  // Selectors-4 §11.2 defines :dir() as taking a SINGLE <ident> that is
  // either `ltr` or `rtl`; any other argument makes the selector invalid,
  // which per CSS 2.2 §4.1.7 invalidates the whole rule. parseCompound
  // enforces exactly that (see the `dir` branch there), which is what keeps
  // dir-selector-ltr-002 (`:dir(ltrr)`) and -003 (`:dir(ltr, rtl)`) painting
  // their green base instead of the red the invalid rule would have applied.
  'dir',
]);

// wave-30 A1: the two directionality states Selectors-4 §11.2 accepts as the
// :dir() argument. Matching is ASCII case-insensitive because the argument is
// a CSS <ident> keyword (CSS Syntax 3 §3.1 — keywords are case-insensitive),
// NOT the HTML `dir` attribute value (which HTML §15.3.4 also compares
// ASCII-case-insensitively, so the two agree).
const DIR_PSEUDO_ARGS = new Set(['ltr', 'rtl']);

// swarm-003 Bug 1 (css-lists__counter-001, css-pseudo__before-*,
// css-counter-styles__*): pseudo-elements we model. Rules whose rightmost
// compound carries one of these `::pseudo` selectors attach to the host
// component's `_pseudo.<name>.properties` bucket instead of the host's
// flat properties — the sister F-G-RENDERER agent emits the rules as
// inline spans with the declared `content`. Other pseudo-elements
// (::first-line, ::first-letter, ::selection, ::placeholder, …) remain
// unsupported (logged as TODO via parseCompound's unsupportedReason). The
// pseudo-element MUST be the last token in the rightmost compound per
// Selectors-4 §3.3; we enforce that in parseCompound below.
const SUPPORTED_PSEUDO_ELEMENTS = new Set(['before', 'after', 'marker']);

// ── wave-36 lane M3: the LEGACY ONE-COLON pseudo-element notation ───────────
//
// THE HOLE. Selectors-3 §7.1 (and CSS 2.1 §5.10 before it) is explicit:
//   "for compatibility with existing style sheets, user agents must also
//    accept the previous one-colon notation for pseudo-elements introduced
//    in CSS levels 1 and 2 (namely, :first-line, :first-letter, :before and
//    :after)."
// parseCompound implemented only the `::` half. A single `:` fell through to
// the pseudo-CLASS branch, where `before`/`after` are (correctly) absent from
// SUPPORTED_PSEUDO_CLASSES — so the compound came back `unsupported` and the
// WHOLE RULE was dropped before extraction. The generated box never existed:
// no `_pseudo` bucket, no `content`, nothing for any runtime to render.
//
// MEASURED (the wave35-webmap full-corpus web map, bucket-A tests carrying a
// one-colon `:before`/`:after`): 24 tests across 8 sections, 17 of them
// FAILING, and the failure signature is the same everywhere — capture ink
// 0.00% against a ref that paints the generated text:
//   css-content/attr-case-sensitivity-001  0.9891  ink 0.00 / 0.23
//   css-content/attr-case-sensitivity-002  0.9900  ink 0.00 / 0.20
//   css-counter-styles/counter-name-case-sensitive        ink 0.00 / 0.33
//   css-counter-styles/hebrew/counter-hebrew-nested       ink 0.00 / 3.15
//   css-contain/contain-style-counters-005                ink 0.00 / 0.24
//   css-animations/animation-delay-011                    ink 0.00 / 4.27
//   css-animations/parent-after-change-style-…-flicker    ink 0.00 / 4.27
//   css-lists/counter-reset-increment-overflow-underflow  ink 0.00 / 2.89
// plus the CSS2/generated-content before-after-positioned-002/003/004 family
// this lane's brief names (`#test:after, #test:before { content:"" … }`).
//
// SCOPE — exactly the four CSS1/CSS2 pseudo-elements the spec grandfathers,
// and nothing else. `::marker` is CSS-Pseudo-4 and has NO one-colon form, so
// `:marker` stays a (rejected) pseudo-class; `:first-line`/`:first-letter`
// are listed because the spec grandfathers them, but they are NOT in
// SUPPORTED_PSEUDO_ELEMENTS, so they resolve to the SAME "unsupported"
// answer their `::` spelling already gives — the alias must not smuggle in
// support the `::` path does not have.
//
// The rightmost-token rule (Selectors-4 §3.3) is enforced identically to the
// `::` branch: anything after the pseudo name inside the compound (a class,
// an id, a functional argument) makes the compound unsupported rather than
// silently mis-attaching.
const LEGACY_ONE_COLON_PSEUDO_ELEMENTS = new Set([
  'before', 'after', 'first-line', 'first-letter',
]);

/**
 * Parse a single compound selector into a structured form. Returns
 * `{ needTag, needId, needClasses, pseudos: [{name, arg?}],
 *    pseudoElement: 'before'|'after'|'marker'|null, unsupported }`.
 * `unsupported === true` means the compound contains attr-syntax (`[…]`) or
 * a pseudo not in the allow-list above — callers should treat the rule as
 * non-matching (NOT bail-and-include, which would inflate matches).
 *
 * `pseudoElement` (swarm-003 Bug 1): when set, the compound's selector
 * matches the named pseudo-element of the host, not the host's own box.
 * Callers (selectorMatchesPseudoElement) propagate this so buildComponents
 * can route the rule's declarations into `_pseudo.<name>.properties`
 * instead of the host's flat `properties`. Per Selectors-4 §3.3 the
 * pseudo-element MUST be the last token in the rightmost compound.
 */
function parseCompound(compound, isSubject = true) {
  const out = {
    needTag: null, needId: null, needClasses: [],
    pseudos: [], pseudoElement: null, unsupported: false,
  };
  // Attribute selectors (`[attr]`, `[attr=value]`) stay unsupported. We
  // deliberately reject them rather than no-op because they tend to be
  // value-bearing (e.g. `[hidden]` toggles display); a silent match would
  // produce wrong results, a silent skip just leaves the rule out.
  if (compound.includes('[')) { out.unsupported = true; return out; }
  // Walk the compound left-to-right. Tokens: tag, .class, #id, *, or
  // :pseudo (optionally with `(...)` argument).
  let i = 0;
  const n = compound.length;
  while (i < n) {
    const ch = compound[i];
    if (ch === ':') {
      // Pseudo-element (Selectors-4 §3.3) — double `::`. swarm-003 Bug 1:
      // previously every `::foo` compound was rejected outright, dropping
      // every `div::before { content: counter(...) }` and ::marker rule
      // before extraction. We now accept the canonical generated-content
      // pseudo-elements (::before/::after/::marker) and record the name on
      // `out.pseudoElement` so callers can route the rule's declarations
      // into `_pseudo.<name>` on the host component. Other pseudo-elements
      // (::first-line, ::first-letter, ::selection, ::placeholder, …) stay
      // unsupported because they require runtime layout / selection state
      // we don't model — log a TODO via the unsupportedReason hint.
      if (compound[i + 1] === ':') {
        // Read past the double colon.
        let j = i + 2;
        while (j < n && /[A-Za-z0-9-]/.test(compound[j])) j++;
        const peName = compound.slice(i + 2, j).toLowerCase();
        // Per Selectors-4 §3.3 the pseudo-element MUST be the rightmost
        // simple selector in the compound. Anything trailing (a class, an
        // id, another pseudo) is invalid; treat the whole compound as
        // unsupported rather than silently accepting and mis-attaching.
        if (j !== n) { out.unsupported = true; return out; }
        if (!SUPPORTED_PSEUDO_ELEMENTS.has(peName)) {
          // TODO(swarm-003 Bug 1): add ::first-line / ::first-letter /
          // ::selection / ::placeholder support when the renderer grows
          // text-fragment hooks. For now bail so the rule is dropped
          // honestly instead of mis-matching the host element.
          out.unsupported = true;
          return out;
        }
        out.pseudoElement = peName;
        i = j;
        continue;
      }
      // Pseudo-class. May be functional (`:nth-child(2n+1)`) or bare
      // (`:first-child`). Single `:`.
      // Read the pseudo name (alpha/digit/dash).
      let j = i + 1;
      while (j < n && /[A-Za-z0-9-]/.test(compound[j])) j++;
      const name = compound.slice(i + 1, j).toLowerCase();
      // wave-36 lane M3: the LEGACY ONE-COLON ALIAS (Selectors-3 §7.1 — see
      // the LEGACY_ONE_COLON_PSEUDO_ELEMENTS banner). `p:before` names the
      // ::before PSEUDO-ELEMENT, not a pseudo-class, so it must take the
      // branch above's semantics verbatim — including the two rules that
      // make that branch honest:
      //   * rightmost-token (Selectors-4 §3.3): `j !== n` means something
      //     trails the pseudo name inside this compound (a class, an id, a
      //     `(`-argument that no CSS1/2 pseudo-element takes) — invalid, so
      //     the whole compound is unsupported, exactly as `p::before.x` is;
      //   * the SUPPORTED set gate: `:first-line`/`:first-letter` resolve to
      //     unsupported, byte-identical to their `::` spelling, so the alias
      //     never grants support the canonical notation lacks.
      // Placed BEFORE the functional-argument scan below so a `(` after the
      // name is caught by the `j !== n` test rather than parsed as an nth-
      // style argument for a name that can never be functional.
      if (LEGACY_ONE_COLON_PSEUDO_ELEMENTS.has(name)) {
        if (j !== n || !SUPPORTED_PSEUDO_ELEMENTS.has(name)) {
          out.unsupported = true;
          return out;
        }
        out.pseudoElement = name;
        i = j;
        continue;
      }
      i = j;
      let arg = null;
      if (compound[i] === '(') {
        // Functional pseudo. Capture balanced `(...)` — for nth-* the body
        // is always plain text without nested parens in WPT, but be safe.
        let depth = 1; let k = i + 1;
        while (k < n && depth > 0) {
          if (compound[k] === '(') depth++;
          else if (compound[k] === ')') depth--;
          if (depth > 0) k++;
        }
        // wave-22 EX2 A-RC1: an UNBALANCED argument means the compound was
        // truncated — parseCss splits a selector list on commas without
        // respecting parens, so `p:not(.a, .b)` arrives here as the
        // fragment `p:not(.a`. Accepting it would negate against `.a`
        // alone and MATCH `<p>`, a false positive on a selector the
        // browser applies only to non-.a, non-.b paragraphs. Refuse.
        if (depth > 0) { out.unsupported = true; return out; }
        arg = compound.slice(i + 1, k);
        i = k + 1; // past the ')'
      }
      const isFunctional = arg !== null;
      const supported = isFunctional
        ? SUPPORTED_FUNCTIONAL_PSEUDOS.has(name)
        : SUPPORTED_PSEUDOS.has(name);
      if (!supported) { out.unsupported = true; return out; }
      // wave-22 EX2 A-RC1: `:not()` needs its ARGUMENT validated here, at
      // parse time, so an unsupported inner selector fails the WHOLE
      // compound (rule dropped) rather than silently negating to `true`
      // and producing FALSE MATCHES — the failure mode the hard rule
      // "no silent fallthroughs" exists to prevent. Selectors-4 §5.1
      // takes a <complex-selector-list>; we accept the single-compound
      // subset only (the corpus's entire usage: `*:not(#id)`,
      // `div:not(.cls)`), because a comma list or a combinator inside
      // :not() would need a full sub-matcher we don't have.
      if (name === 'not') {
        const innerSel = (arg ?? '').trim();
        // Empty `:not()` is invalid per the grammar; a comma or any
        // combinator/whitespace means a list or complex selector — both
        // outside the supported subset, so drop the rule honestly.
        if (!innerSel || /[,\s>+~]/.test(innerSel)) { out.unsupported = true; return out; }
        // Recurse with the SAME parser so the inner compound obeys every
        // rule the outer one does (attr selectors rejected, unknown
        // pseudos rejected, nested `:not(:not(x))` rejected because the
        // inner parse yields a `not` pseudo we refuse just below).
        const inner = parseCompound(innerSel);
        // Selectors-4 §5.1: the argument must contain no pseudo-elements.
        // An unsupported inner (or a nested :not) drops the whole rule.
        if (inner.unsupported || inner.pseudoElement) { out.unsupported = true; return out; }
        if (inner.pseudos.some((p) => p.name === 'not')) { out.unsupported = true; return out; }
        // Carry the PARSED inner alongside the raw text: evalPseudo uses
        // the text (re-matched through compoundMatches so id/class/tag/
        // pseudo semantics can never drift between the two paths) and the
        // parsed form to decide whether positional data is required.
        out.pseudos.push({ name, arg: innerSel, inner });
        continue;
      }
      // wave-34 lane R: `:has()` — the RELATIVE selector is validated here,
      // at parse time, for the same reason `:not()` is: an argument we
      // cannot evaluate must drop the RULE, never degrade to a guess. The
      // asymmetry `:not()` warns about applies with double force, because
      // `:has()` most often appears in the corpus INSIDE a `:not()`
      // (has-style-sharing-007), where an unknowable inner answer would be
      // inverted into a confident false match.
      //
      // Selectors-4 §5.4 + §16: the argument is a <relative-selector-list>,
      // each entry `<combinator>? <complex-selector>` absolutised against
      // the subject (`:has(.a)` means `:has(:scope .a)`). We accept the
      // leading combinator plus ONE compound and refuse the rest.
      if (name === 'has') {
        // ── wave-34 lane R: SUBJECT-SIDE ONLY, and the measurement that
        // drew the line ─────────────────────────────────────────────────────
        //
        // `:has()` on a NON-rightmost compound asks the relational question
        // about an ANCESTOR of the subject (`:has(> .a) .b` — "a `.b` inside
        // something that has an `.a` child"). We can evaluate that: the
        // ancestor entries carry `pos.kids` like everyone else. We decline it
        // anyway, because evaluating it makes the fixture WORSE:
        //
        //   selectors/has-style-sharing-003 — `:has(> .a) .b { green }` with
        //   `.b { purple }` LATER in the sheet. Chromium paints the first
        //   `.b` GREEN (specificity 0,2,0 beats 0,1,0). propsForElement has
        //   no specificity — it is last-write-wins in document order — so our
        //   static answer is purple. Today that costs nothing, because the
        //   dropped `:has()` rule feeds countUnsupportedRules and the wave-30
        //   A3 route hands the test to the browser, which bakes
        //   `background-color: rgb(0, 128, 0)` (MEASURED, probe
        //   _diag34/laneR: postLoadAugmentFixture status 'extracted',
        //   6 overlaid). Accepting the compound would delete that route and
        //   ship the purple — a confident wrong answer replacing a correct
        //   one. Same shape, same verdict, on selectors/featureless-005
        //   (`:has(.t2) .t2`), where accepting flips a green box to red.
        //
        // Every corpus instance of the ancestor-side form is that shape, and
        // every SUBJECT-side instance is competition-free (has-style-sharing
        // -001/-002/-007, dir-pseudo-in-has, nth-child-of-has, has-nesting) —
        // so the line is not a hedge, it is where the evidence puts it. When
        // the cascade grows specificity ordering, drop the flag and re-measure.
        if (!isSubject) { out.unsupported = true; return out; }
        let rel = (arg ?? '').trim();
        // Empty `:has()` is invalid per the grammar. A comma means a
        // relative-selector LIST — but note it can rarely reach us intact,
        // since parseCss's paren-blind comma split turns `p:has(.a, .b)`
        // into the unbalanced fragment `p:has(.a`, already refused above.
        if (!rel || rel.includes(',')) { out.unsupported = true; return out; }
        // The leading combinator, defaulting to descendant (§5.4: an
        // omitted combinator is the descendant combinator).
        let comb = ' ';
        if (rel[0] === '>' || rel[0] === '+' || rel[0] === '~') {
          comb = rel[0];
          rel = rel.slice(1).trim();
          if (!rel) { out.unsupported = true; return out; }
        }
        // What remains must be a SINGLE compound: any residual whitespace or
        // combinator is a complex relative selector (`~ .item > :nth-child(2)`
        // — selectors/invalidation/has-with-nth-child-sibling-remove), which
        // needs a sub-chain matcher rooted at each candidate. Out of scope,
        // refused loudly rather than approximated.
        if (/[\s>+~]/.test(rel)) { out.unsupported = true; return out; }
        // Same parser, same rules — attr selectors rejected, unknown pseudos
        // rejected, and a NESTED `:has()` rejected just below.
        const inner = parseCompound(rel);
        // §5.4: the argument must contain no pseudo-elements.
        if (inner.unsupported || inner.pseudoElement) { out.unsupported = true; return out; }
        // Nesting is explicitly invalid per §5.4 ("`:has()` is not valid
        // within `:has()`"), and we could not evaluate it anyway.
        if (inner.pseudos.some((p) => p.name === 'has')) { out.unsupported = true; return out; }
        // `arg` carries the compound TEXT (evalPseudo re-matches it through
        // compoundMatches so the two paths can never drift); `comb` carries
        // the absolutised relation; `inner` is kept for symmetry with `not`.
        out.pseudos.push({ name, arg: rel, comb, inner });
        continue;
      }
      // wave-30 A1: `:dir()` needs its ARGUMENT validated here, at parse
      // time, for the same "no silent fallthroughs" reason `:not()` does —
      // except the consequence runs the other way. Selectors-4 §11.2's
      // grammar is `:dir( <ident> )` with `ltr` / `rtl` the only meaningful
      // values; ANY other argument is an invalid selector, and CSS 2.2
      // §4.1.7 says an invalid selector invalidates the ENTIRE rule. So an
      // unrecognised argument must mark the compound unsupported (rule
      // dropped), never "matches nothing but the rule survives" and never a
      // lenient fallback to ltr. That is not a conservatism dodge — it is
      // literally what dir-selector-ltr-002 (`div:dir(ltrr) { …red }`) and
      // -003 (`div:dir(ltr, rtl) { …red }`) assert: both tests pass ONLY
      // because the red rule is thrown away by the parser.
      if (name === 'dir') {
        const dirArg = (arg ?? '').trim().toLowerCase();
        // A comma (`ltr, rtl`) or any internal whitespace means more than
        // one component value reached the single-<ident> slot — invalid.
        if (!DIR_PSEUDO_ARGS.has(dirArg)) { out.unsupported = true; return out; }
        // Store the NORMALISED keyword so evalPseudo compares two lowercase
        // strings and can never re-derive a different answer than we did.
        out.pseudos.push({ name, arg: dirArg });
        continue;
      }
      out.pseudos.push({ name, arg });
      continue;
    }
    if (ch === '.') {
      // .class — capture the class name.
      let j = i + 1;
      while (j < n && /[A-Za-z0-9_-]/.test(compound[j])) j++;
      out.needClasses.push(compound.slice(i + 1, j));
      i = j;
      continue;
    }
    if (ch === '#') {
      // #id — capture the id name.
      let j = i + 1;
      while (j < n && /[A-Za-z0-9_-]/.test(compound[j])) j++;
      out.needId = compound.slice(i + 1, j);
      i = j;
      continue;
    }
    if (ch === '*') { i++; continue; } // universal — no constraint
    if (/[A-Za-z]/.test(ch)) {
      // Tag selector. May only appear once at the front per CSS spec;
      // tolerate trailing tags by last-write-wins (matches browser leniency).
      let j = i;
      while (j < n && /[A-Za-z0-9-]/.test(compound[j])) j++;
      out.needTag = compound.slice(i, j).toLowerCase();
      i = j;
      continue;
    }
    // Anything else (combinator chars handled by caller; stray punctuation)
    // — bail out as unsupported rather than risk a false positive.
    out.unsupported = true; return out;
  }
  return out;
}

/**
 * Evaluate an An+B argument string against a 1-based element index.
 * Accepts the WPT-common forms: `n`, `even`, `odd`, integer literals (`1`,
 * `-2`), and `An+B` / `An-B` / `-An+B` etc. Returns true when the index
 * satisfies the formula.
 *
 * The full Selectors-4 An+B grammar is at
 * https://drafts.csswg.org/css-syntax-3/#anb-microsyntax. We support the
 * subset the WPT corpus actually uses; the Selectors-4 extended form with
 * an `of S` clause is split off by splitAnBOfSelector() BEFORE this
 * function sees the argument, so evalAnB itself only ever receives a pure
 * An+B head.
 */
function evalAnB(arg, index1) {
  if (!arg) return false;
  const s = arg.trim().toLowerCase();
  if (s === 'odd')  return index1 % 2 === 1;
  if (s === 'even') return index1 % 2 === 0;
  // Pure integer literal — only the n-th element matches.
  if (/^-?\d+$/.test(s)) return Number(s) === index1;
  // An+B form. Normalise spaces away, then parse.
  const compact = s.replace(/\s+/g, '');
  // Capture coefficient + offset in `An±B` (A may be empty / +/− / number).
  const m = /^([+-]?\d*)n(?:([+-]\d+))?$/.exec(compact);
  if (!m) return false;
  // `n` alone → coefficient 1; `-n` → -1; `+n` → 1; otherwise parseInt.
  let a = m[1];
  if (a === '' || a === '+') a = 1;
  else if (a === '-') a = -1;
  else a = parseInt(a, 10);
  const b = m[2] ? parseInt(m[2], 10) : 0;
  // index1 = An + B → (index1 - B) must be a non-negative multiple of A
  // when A != 0; when A === 0 the formula is constant B.
  if (a === 0) return index1 === b;
  const k = (index1 - b) / a;
  return Number.isInteger(k) && k >= 0;
}

/**
 * swarm-003 Bug 2: paren-aware whitespace splitter for selector compound
 * decomposition. The naïve `sel.split(/\s+/)` splits `:nth-child(odd of
 * :defined)` into THREE compounds (the parens swallow internal whitespace
 * that the spec considers part of the functional pseudo's argument), so
 * the rule never reaches evalPseudo. We respect paren depth and only
 * break on whitespace at depth 0.
 *
 * Exported for unit testing.
 */
export function splitCompounds(sel) {
  const out = [];
  let buf = '';
  let depth = 0;
  for (let i = 0; i < sel.length; i++) {
    const c = sel[i];
    if (c === '(') { depth++; buf += c; continue; }
    if (c === ')') { depth--; buf += c; continue; }
    if (/\s/.test(c) && depth === 0) {
      if (buf) { out.push(buf); buf = ''; }
      continue;
    }
    buf += c;
  }
  if (buf) out.push(buf);
  return out;
}

// wave-30 A2: the three EXPLICIT combinator characters Selectors-4 §15
// defines (the fourth, descendant, is whitespace). Kept as one constant so
// the tokeniser below and the matcher in selectorMatchesPseudoElement can
// never drift on which characters are combinators.
const EXPLICIT_COMBINATORS = new Set(['>', '+', '~']);

/**
 * wave-8 (css-flexbox abspos-autopos family + css-break): tokenise a full
 * selector into its compound chain WITH combinators. Selectors-4 §15 defines
 * four combinators: descendant (whitespace), child (`>`), next-sibling (`+`),
 * subsequent-sibling (`~`). wave-30 A2 completes the set — all four are now
 * modelled.
 *
 * Why this exists: the previous pipeline (splitCompounds + a blanket
 * `/[>+~]/` reject in selectorMatchesPseudoElement) dropped every rule
 * containing `>` WHOLESALE. WPT leans on `A > B` heavily — e.g. the six
 * css-flexbox/abspos/abspos-autopos-* tests style their subject via
 * `.flex > div { position:absolute; … }`, and losing that rule shipped
 * placeholder children with no styles (the all-platform ~0.90 scores were
 * the corrupted fixture, not the renderers).
 *
 * wave-30 A2 does the same for `+` / `~`. The wave-8 reason for excluding
 * them ("the extractor has no sibling-adjacency matcher") stopped being true
 * when swarm-003 Bug 2 put the full sibling list on every element's `pos`
 * for `:nth-child(An+B of S)`: `pos.siblings` + `pos.sibIndex` ARE the
 * adjacency data, so the matcher can now answer `+`/`~` exactly instead of
 * dropping the rule. MEASURED cost of the drop
 * (selectors__dir-selector-change-001): the only rule in the test is
 * `#x:dir(rtl) + span { background-color: lime }`; dropping it also emptied
 * collectStyledTags, so the `<span>` under test was inline-MERGED into its
 * parent's text and the lime box vanished from the fixture entirely (the
 * wave29-final per-test IR has no span component at all).
 *
 * Returns `{ compounds: string[], combinators: string[] }` where
 * `combinators[i]` relates `compounds[i]` to `compounds[i+1]` and is one of
 * `' '` (descendant), `'>'` (child), `'+'` (next-sibling) or `'~'`
 * (subsequent-sibling). Returns null when the selector is malformed
 * (leading / trailing / doubled combinator), so callers treat the rule as
 * unsupported — same bail contract the old regex reject had.
 *
 * Paren-aware like splitCompounds: whitespace and combinator characters
 * inside a functional pseudo's argument (`:nth-child(2n + 1)`, where the `+`
 * is An+B syntax and NOT a combinator) never split. Exported for unit
 * testing.
 */
export function splitSelectorChain(sel) {
  const compounds = [];
  const combinators = [];
  let buf = '';
  let depth = 0;
  // The explicit combinator that will bind the NEXT compound to the previous
  // one. null = none seen in this separator run → descendant at flush time.
  let pending = null;
  // Flush the accumulated compound buffer, recording the combinator that
  // separated it from the previous compound (the explicit one seen in the
  // separator run, descendant otherwise).
  const flush = () => {
    if (!buf) return true;
    if (compounds.length > 0) combinators.push(pending ?? ' ');
    else if (pending) return false; // leading combinator — malformed
    compounds.push(buf);
    buf = '';
    pending = null;
    return true;
  };
  for (let i = 0; i < sel.length; i++) {
    const c = sel[i];
    if (c === '(') { depth++; buf += c; continue; }
    if (c === ')') { depth--; buf += c; continue; }
    if (depth > 0) { buf += c; continue; } // inside a functional pseudo arg
    if (EXPLICIT_COMBINATORS.has(c)) {
      // Explicit combinator between compounds. Flush whatever compound was
      // being read; a second combinator before any new compound text
      // (`a >> b`, `a + ~ b`) is malformed CSS — bail.
      if (!flush()) return null;
      if (pending) return null; // doubled combinator with nothing between
      if (compounds.length === 0) return null; // leading combinator — malformed
      pending = c;
      continue;
    }
    if (/\s/.test(c)) {
      // Top-level whitespace: compound boundary (descendant combinator
      // unless an explicit one already marked this separator run).
      if (!flush()) return null;
      continue;
    }
    buf += c;
  }
  if (!flush()) return null;
  // Trailing combinator with no right-hand compound (`.a >`, `.a +`).
  if (pending) return null;
  return { compounds, combinators };
}

/**
 * wave-30 A2: synthesise the position metadata for entry `k` of a `siblings`
 * list so a sibling-combinator compound can be matched with the SAME
 * compoundMatches the subject and ancestor compounds go through (one matcher,
 * no semantics drift).
 *
 * `siblings` entries carry `{ tag, attrs }` plus the two extra facts
 * extractBodyTreeNested's second pass stamps on them (`isEmpty` for `:empty`,
 * `subtreeText` for `:dir(auto)`); everything positional is DERIVED here so
 * the walker never has to store a per-sibling pos (which would make the tree
 * cyclic — see the stamping comment there).
 *
 * `sibTypeIndex` / `sibTypeCount` are counted over the list rather than
 * guessed, so `:nth-of-type` on a sibling compound is exact, not degraded.
 */
function siblingPositionMeta(siblings, k) {
  const me = siblings[k];
  let typeIndex = 0;  // 0-based rank among same-tag siblings before k
  let typeCount = 0;  // total same-tag siblings
  for (let i = 0; i < siblings.length; i++) {
    if (siblings[i]?.tag !== me.tag) continue;
    typeCount++;
    if (i < k) typeIndex++;
  }
  return {
    isRoot: false,
    sibIndex: k, sibCount: siblings.length,
    sibTypeIndex: typeIndex, sibTypeCount: typeCount,
    // Stamped facts — absent on legacy hand-built sibling lists, where the
    // conservative defaults ("not empty", "no text") keep `:empty` from
    // matching and leave `:dir(auto)` on HTML's ltr fallback.
    isEmpty: me?.isEmpty === true,
    subtreeText: me?.subtreeText ?? '',
    // wave-34 lane R: the relational handle for `:has()`, forwarded VERBATIM
    // — including `undefined`. There is no conservative default available
    // here: `[]` would assert "this sibling has no children" (a silent lie
    // below the maxDepth cut) and any non-empty guess would be worse. An
    // absent handle makes the `has` branch answer null, which drops the rule.
    kids: me?.kids,
    siblings,
  };
}

/**
 * wave-34 lane R — does this `pos` carry the handle `:has()` needs?
 *
 * ONE predicate, consulted from two places (the `:has()` branch itself and
 * the `:not()` inversion guard) so the two can never disagree about what
 * "decidable" means. `kids` is the walker's relational stamp (see
 * extractBodyTreeNested); it is deliberately absent below the maxDepth cut
 * and on every legacy hand-built `pos`.
 *
 * The sibling forms (`:has(+ x)` / `:has(~ x)`) need `siblings`/`sibIndex`
 * instead, but requiring `kids` for them too is the conservative direction:
 * both stamps come from the same walker in the same pass, so a pos that has
 * one has the other, and a pos that has neither must refuse either way.
 */
function hasRelationalMeta(pos) {
  return Array.isArray(pos?.kids);
}

/**
 * wave-34 lane R — evaluate `:has(<relative-selector>)` (Selectors-4 §5.4).
 *
 * The spec's definition is "absolutise the relative selector against the
 * subject (`:has(.a)` ≡ `:has(:scope .a)`), then match it against the tree;
 * `:has()` is true when at least one element matches". Because parseCompound
 * has already restricted the argument to `<combinator>? <one compound>`, the
 * absolutised form is exactly "some element in the set the combinator names
 * matches this compound", and the four combinators name four sets:
 *
 *   (none)  every DESCENDANT          — §5.4's default, `:scope .a`
 *   `>`     every CHILD               — `:scope > .a`
 *   `+`     the next sibling only     — `:scope + .a`
 *   `~`     every FOLLOWING sibling   — `:scope ~ .a`
 *
 * Note the sibling sets look FORWARD. That is the one place `:has()` reads
 * opposite to the sibling COMBINATORS in selectorMatchesPseudoElement, which
 * scan backwards from the subject: there the subject is the right-hand side
 * of `A + B`, here it is the left-hand side of `:scope + A`.
 *
 * Candidates are matched through the SAME compoundMatches every other
 * compound goes through, with a correctly-extended ancestor chain, so a
 * pseudo inside the argument (`:has(*:dir(ltr))` —
 * selectors/dir-pseudo-in-has) resolves exactly as it would if the element
 * were the subject of its own rule.
 *
 * @returns true / false, or null when the tree handle is missing (the
 *          maxDepth cut) — null drops the rule, never guesses.
 */
function evalHas(pseudo, pos, tag, attrs, ctx, ancestors) {
  // No relational stamp ⇒ we never enumerated the subtree. Refuse.
  if (!hasRelationalMeta(pos)) return null;
  const comb = pseudo.comb ?? ' ';
  const sel = pseudo.arg;
  // The subject's own entry, for candidates whose chain runs THROUGH it.
  const selfEntry = { tag, attrs: attrs ?? {}, pos };
  const baseChain = ancestors ?? [];

  if (comb === '+' || comb === '~') {
    // Forward sibling scan. Siblings share the subject's parent, so they
    // share its ancestor chain verbatim (the same reasoning the backward
    // sibling step in selectorMatchesPseudoElement uses).
    const sibs = pos.siblings;
    const here = pos.sibIndex;
    if (!Array.isArray(sibs) || typeof here !== 'number') return null;
    const stop = comb === '+' ? here + 1 : sibs.length - 1;
    for (let k = here + 1; k <= stop && k < sibs.length; k += 1) {
      const sib = sibs[k];
      if (!sib) continue;
      if (compoundMatches(sel, sib.tag, sib.attrs ?? {}, siblingPositionMeta(sibs, k),
        ctx, ancestors) === true) return true;
    }
    return false;
  }

  // Child / descendant. `deep` is the only difference: the child form stops
  // at one level, the descendant form recurses.
  const deep = comb !== '>';
  /** @returns true | false | null (null = an unenumerated subtree). */
  const scan = (list, chain) => {
    let sawUnknown = false;
    for (let k = 0; k < list.length; k += 1) {
      const cand = list[k];
      if (!cand) continue;
      const candPos = siblingPositionMeta(list, k);
      if (compoundMatches(sel, cand.tag, cand.attrs ?? {}, candPos, ctx, chain) === true) {
        return true;
      }
      if (!deep) continue;
      // Recurse. A candidate whose own subtree was never enumerated makes
      // the WHOLE answer unknowable — but only if nothing else matches, so
      // remember it and keep looking rather than bailing early (a positive
      // found later is still a correct `true`; §5.4 is existential).
      if (!Array.isArray(cand.kids)) { sawUnknown = true; continue; }
      const sub = scan(cand.kids, [...chain, { tag: cand.tag, attrs: cand.attrs ?? {}, pos: candPos }]);
      if (sub === true) return true;
      if (sub === null) sawUnknown = true;
    }
    return sawUnknown ? null : false;
  };
  return scan(pos.kids, [...baseChain, selfEntry]);
}

/**
 * swarm-003 Bug 2 (selectors__nth-child-of-pseudo-class): split a
 * `:nth-child(...)` argument into its An+B head and optional Selectors-4
 * `of <selector-list>` suffix. The grammar (Selectors-4 §6.4.2) is
 * `<An+B-expr> ws+ 'of' ws+ <selector-list>`. We do a tokenised scan
 * rather than a regex split because the An+B head may contain whitespace
 * (`2n + 1`, `- n + 3`) and the selector list may contain commas and
 * spaces of its own.
 *
 * Returns `{ anb: string, ofSelectors: string[] | null }`. When no `of`
 * clause is present, ofSelectors is null and the caller falls through to
 * the legacy "count all element siblings" path. When present,
 * ofSelectors is a list of comma-separated selectors (already trimmed)
 * that the caller must use to filter the parent's children before
 * applying the An+B math.
 */
export function splitAnBOfSelector(arg) {
  if (!arg) return { anb: arg, ofSelectors: null };
  // Tokenise on whitespace runs so we can detect the standalone `of`
  // keyword. The `of` token MUST be surrounded by whitespace per the
  // spec — embedded `of` substrings (e.g. inside a selector like
  // `.foo-of-bar`) cannot trigger the split. We scan from the left
  // accumulating the An+B head, and when we hit a bare `of` we treat
  // the rest as the selector list.
  const trimmed = arg.trim();
  // Match `<head> of <tail>` where `of` is whitespace-bounded. Use a
  // non-greedy head so the FIRST `of` keyword wins (the An+B grammar
  // never contains the letter `o`, only `n`).
  const m = /^([\s\S]*?)\s+of\s+([\s\S]+)$/.exec(trimmed);
  if (!m) return { anb: trimmed, ofSelectors: null };
  const head = m[1].trim();
  const tail = m[2].trim();
  // Split the tail on top-level commas only. The Selectors-4 selector
  // list is comma-separated; nested parentheses are rare in WPT but
  // tolerate them defensively by tracking paren depth.
  const selectors = [];
  let buf = '';
  let depth = 0;
  for (let i = 0; i < tail.length; i++) {
    const c = tail[i];
    if (c === '(') { depth++; buf += c; continue; }
    if (c === ')') { depth--; buf += c; continue; }
    if (c === ',' && depth === 0) {
      const s = buf.trim();
      if (s) selectors.push(s);
      buf = '';
      continue;
    }
    buf += c;
  }
  const last = buf.trim();
  if (last) selectors.push(last);
  return { anb: head, ofSelectors: selectors };
}

/**
 * Evaluate a parsed pseudo against an element's position metadata. The
 * metadata shape (`pos`) is `{ isRoot, sibIndex, sibCount, sibTypeIndex,
 * sibTypeCount, isEmpty, siblings? }` — all 0-based for
 * sibIndex/sibTypeIndex. `siblings` (optional, added by swarm-003 Bug 2)
 * is the full sibling list `[{tag, attrs}, …]` in document order; it
 * drives the Selectors-4 `:nth-child(An+B of S)` filter where we have
 * to renumber among only the siblings matching S.
 *
 * Spec carve-out (Selectors-4 §6.4.1): "an element with no parent" matches
 * `:first-child`, `:nth-child(n)`, `:last-child`, `:only-child` and all
 * typed equivalents. We surface this via `pos.isRoot === true` — when set,
 * every child-indexed pseudo returns true unconditionally.
 *
 * `tag`, `attrs` (swarm-003 Bug 3): the host element's identity so
 * `:defined` can decide whether the tag is a built-in HTML element
 * (always defined) or a custom element whose tag name appears in
 * ctx.definedTags. `ctx` carries `{ definedTags?: Set<string> }`
 * harvested from `<script>` blocks by collectDefinedTags() — when
 * absent, only built-in tags answer true for `:defined`.
 *
 * `ancestors` (wave-30 A1): the element's ancestor chain in document order
 * (immediate parent LAST), the same array selectorMatchesPseudoElement walks.
 * Consumed ONLY by `:dir()`, whose answer is an INHERITED HTML concept (see
 * resolveDirectionality) rather than a positional one. null = no chain
 * available (legacy direct callers); the resolver then falls back to the
 * element's own `dir` attribute and finally to HTML's ltr default.
 *
 * Returns null when `pos` is missing (caller can't evaluate without
 * position data — bail and treat the selector as non-matching).
 */
function evalPseudo(pseudo, pos, tag = null, attrs = null, ctx = {}, ancestors = null) {
  // wave-22 EX2 A-RC1: `:not()` is evaluated BEFORE the `!pos` bail
  // because negation of a purely structural compound (`*:not(#drop-down-
  // select)` — the css-ui menulist-button rule) needs no position data at
  // all; requiring `pos` here would keep the rule unmatched on every
  // legacy call site that passes none.
  if (pseudo.name === 'not') {
    const inner = pseudo.inner;
    // Defensive: parseCompound only ever pushes a `not` pseudo WITH a
    // validated `inner`. A missing one means a hand-built pseudo object —
    // answer "can't evaluate" rather than guessing a negation.
    if (!inner) return null;
    // The inner compound carries pseudo-classes (`:not(:first-child)`)
    // but we have no position metadata: compoundMatches would answer
    // `false` for the inner, and negating an unknowable `false` would
    // manufacture a match. Bail to null (caller treats as non-match).
    if (!pos && inner.pseudos.length > 0) return null;
    // wave-34 lane R — THE INVERSION HAZARD, closed. compoundMatches folds
    // "I cannot decide this pseudo" into plain `false` (its loop is
    // `result !== true → return false`), which the negation below would turn
    // into a confident MATCH. That is harmless for every pre-wave-34 pseudo,
    // whose undecidable case is already caught by the `!pos` guard above, but
    // `:has()` is undecidable in a NEW way — the subject may carry full
    // position metadata and still be missing the relational handle (a
    // maxDepth-truncated subtree, or a legacy hand-built `pos`). The corpus
    // shape is real: selectors/has-style-sharing-007's only rule is
    // `.special.cousin:not(:has(span))`, so a wrong answer here paints the
    // wrong box blue. Refuse instead — null drops the rule, and
    // countUnsupportedRules routes the test to the browser that can see it.
    if (inner.pseudos.some((p) => p.name === 'has') && !hasRelationalMeta(pos)) return null;
    // Re-match through the SAME compound matcher the positive path uses,
    // then negate — Selectors-4 §5.1: ":not(X) matches elements that are
    // not represented by X". `null` (unsupported inner) propagates as
    // null so the rule is dropped, never inverted.
    const m = compoundMatches(pseudo.arg, tag, attrs ?? {}, pos, ctx, ancestors);
    if (m === null) return null;
    return !m;
  }
  // wave-34 lane R — `:has()`, evaluated BEFORE the `!pos` bail so the bail's
  // own answer (null, "cannot evaluate") is reached through THIS branch's
  // explicit reasoning rather than by falling through it.
  if (pseudo.name === 'has') return evalHas(pseudo, pos, tag, attrs, ctx, ancestors);
  // wave-30 A1: `:dir()` is evaluated BEFORE the `!pos` bail for the same
  // reason `:not()` is — an element's directionality is an HTML tree fact
  // (HTML §3.2.6.4 / §15.3.4), not a sibling position, so requiring `pos`
  // here would leave every legacy call site's `:dir()` rule unmatched.
  // Selectors-4 §11.2: ":dir(ltr) matches elements whose directionality is
  // ltr". Note it is deliberately NOT the CSS `direction` property — that
  // distinction is load-bearing for dir-selector-change-001, whose stylesheet
  // sets `#outer { direction: ltr }` while the script sets `dir="rtl"`, and
  // whose ref proves the ATTRIBUTE wins for :dir().
  if (pseudo.name === 'dir') {
    // parseCompound already normalised the argument to 'ltr' | 'rtl' and
    // rejected everything else, so this is a plain string compare.
    // `ctx.documentDir` is the `<html dir>` / `<body dir>` rung the body-only
    // ancestor chain cannot carry (see resolveDirectionality rung 2b);
    // undefined on legacy direct callers, which keeps the old ladder.
    return resolveDirectionality(attrs, pos, ancestors, ctx?.documentDir ?? null)
      === pseudo.arg;
  }
  if (!pos) return null;
  // :root matches the document root; for our purposes that's the synthetic
  // body component (isRoot === true). Anywhere else it's false.
  if (pseudo.name === 'root') return pos.isRoot === true;
  // The empty-tree pseudo: matches an element with no children/text.
  if (pseudo.name === 'empty') return pos.isEmpty === true;
  // swarm-003 Bug 3 (:defined): matches built-in HTML elements always
  // (they have no hyphen in their tag name per HTML5 custom-elements
  // §4.13.1) plus any custom element whose tag name is in
  // ctx.definedTags. Returns false for unknown custom elements
  // (hyphen-containing tag, not in registry) so the
  // `:nth-child(odd of :defined)` filter skips them.
  if (pseudo.name === 'defined') {
    if (!tag) return false;
    // Custom elements per HTML5 §4.13.1 MUST contain a hyphen. Without
    // one, the tag is a built-in HTML element and `:defined` is true.
    if (!tag.includes('-')) return true;
    const defined = ctx.definedTags;
    return !!(defined && defined.has(tag));
  }
  // Selectors-4 §6.4.1 carve-out for the parentless root:
  //   - :first-child, :last-child, :only-child, :first-of-type,
  //     :last-of-type, :only-of-type ALWAYS match.
  //   - :nth-child(An+B) / :nth-of-type(An+B) and the *-last-* variants
  //     match ONLY when the An+B formula yields 1 for some non-negative n
  //     (i.e. the formula matches index 1). That's why the WPT test
  //     `:root:nth-last-child(2) #i` is intentionally NOT matching —
  //     `2` doesn't match index 1 on a parentless root.
  if (pos.isRoot) {
    switch (pseudo.name) {
      case 'first-child': case 'last-child': case 'only-child':
      case 'first-of-type': case 'last-of-type': case 'only-of-type':
        return true;
      case 'nth-child': case 'nth-last-child':
      case 'nth-of-type': case 'nth-last-of-type': {
        // Carve-out: the formula is evaluated against index 1 only. The
        // `of S` clause is ignored for the parentless-root case — the
        // root has no siblings so the filtered set is at most one element.
        const { anb } = splitAnBOfSelector(pseudo.arg);
        return evalAnB(anb, 1);
      }
      default: return false;
    }
  }
  // Sibling-position pseudos.
  const i1 = (pos.sibIndex ?? 0) + 1;          // 1-based for the An+B math
  const last1 = (pos.sibCount ?? 1) - (pos.sibIndex ?? 0);
  const ti1 = (pos.sibTypeIndex ?? 0) + 1;
  const tLast1 = (pos.sibTypeCount ?? 1) - (pos.sibTypeIndex ?? 0);
  switch (pseudo.name) {
    case 'first-child':       return i1 === 1;
    case 'last-child':        return last1 === 1;
    case 'only-child':        return pos.sibCount === 1;
    case 'first-of-type':     return ti1 === 1;
    case 'last-of-type':      return tLast1 === 1;
    case 'only-of-type':      return pos.sibTypeCount === 1;
    case 'nth-child':
    case 'nth-last-child':
    case 'nth-of-type':
    case 'nth-last-of-type': {
      // swarm-003 Bug 2: split off any `of <selector-list>` suffix
      // BEFORE running evalAnB on the An+B head. When a selector list
      // is present the index basis changes: we count only siblings that
      // match the selector list (per Selectors-4 §6.4.2 / §6.4.3).
      const { anb, ofSelectors } = splitAnBOfSelector(pseudo.arg);
      if (ofSelectors && ofSelectors.length > 0) {
        // Need full sibling metadata to renumber. Without `siblings` on
        // pos (legacy call sites that don't supply it), fall back to
        // false — better an honest miss than a mis-match against the
        // unfiltered sibling count.
        const sibs = pos.siblings;
        if (!sibs) return false;
        // First check the host itself matches at least one of the
        // `of S` selectors — if not, the rule is structurally inapplicable
        // (you can't be the Nth `p` among `:defined` siblings if you
        // yourself aren't `:defined`). Mirror the browser behaviour.
        const hostMatches = ofSelectors.some((s) => {
          // wave-30 A1: the host's own ancestor chain rides along so an
          // `of S` filter containing `:dir()` inherits directionality
          // exactly like the subject compound does.
          const r = compoundMatches(s, tag, attrs ?? {}, pos, ctx, ancestors);
          return r === true;
        });
        if (!hostMatches) return false;
        // Renumber. We need the host's position WITHIN the filtered
        // sibling list. Walk siblings in document order, counting the
        // ones that match any selector in ofSelectors; the host's
        // 1-based index = number of matches up to and including the host.
        const hostSibIndex = pos.sibIndex ?? 0;
        let filteredIndex = 0;
        let filteredTotal = 0;
        for (let s = 0; s < sibs.length; s++) {
          const sib = sibs[s];
          // Build a minimal `pos` for the sibling — only `tag`/`attrs`
          // are needed for `:defined` and tag/class matching; the
          // compound's own pseudo-classes (if any) are evaluated against
          // a fresh pos so we don't recurse into nth-child-of-S inside
          // the filter, just exercise simple `:defined` / type / class.
          // wave-34 lane R: the ONE field added to the minimal pos — the
          // relational handle. `:has()` is the first `of S` filter that asks
          // about the sibling's SUBTREE rather than its own identity, and
          // selectors/nth-child-of-has.html
          // (`div:nth-child(even of :has(span))`) is exactly that shape: with
          // no handle every sibling answers "no span", the filtered set is
          // empty, and the rule silently applies to nothing. Deliberately
          // NOT the full siblingPositionMeta — the comment above explains why
          // the positional fields stay minimal, and widening them would
          // change `of S` answers far outside `:has()`.
          const sibPos = { isRoot: false, sibIndex: s, sibCount: sibs.length, kids: sib?.kids };
          const matches = ofSelectors.some((selStr) => {
            // Siblings share the host's ancestor chain by definition, so the
            // same array resolves their inherited directionality (wave-30 A1).
            const r = compoundMatches(selStr, sib.tag, sib.attrs ?? {}, sibPos, ctx, ancestors);
            return r === true;
          });
          if (matches) {
            filteredTotal++;
            if (s === hostSibIndex) filteredIndex = filteredTotal;
          }
        }
        if (filteredIndex === 0) return false; // host not in filtered set
        // Resolve which index basis to use depending on the pseudo flavour.
        switch (pseudo.name) {
          case 'nth-child':        return evalAnB(anb, filteredIndex);
          case 'nth-last-child':   return evalAnB(anb, filteredTotal - filteredIndex + 1);
          // For `:nth-of-type(of S)` Selectors-4 §6.4.4 keeps the
          // type-filtering on top of the selector-list filter; the
          // intersection is small enough that we model it as
          // `match S AND share host's tag`. Same renumbering shape.
          case 'nth-of-type':
          case 'nth-last-of-type': {
            // Count only siblings sharing the host's tag AND matching S.
            let typeFilteredIndex = 0;
            let typeFilteredTotal = 0;
            for (let s = 0; s < sibs.length; s++) {
              const sib = sibs[s];
              if (sib.tag !== tag) continue;
              // wave-34 lane R: same one-field addition as the child-indexed
              // loop above — see its note for why only `kids` widens.
              const sibPos = { isRoot: false, sibIndex: s, sibCount: sibs.length, kids: sib?.kids };
              const matches = ofSelectors.some((selStr) => {
                // Same ancestor chain as the host — see the sibling filter above.
                const r = compoundMatches(selStr, sib.tag, sib.attrs ?? {}, sibPos, ctx, ancestors);
                return r === true;
              });
              if (matches) {
                typeFilteredTotal++;
                if (s === hostSibIndex) typeFilteredIndex = typeFilteredTotal;
              }
            }
            if (typeFilteredIndex === 0) return false;
            return pseudo.name === 'nth-of-type'
              ? evalAnB(anb, typeFilteredIndex)
              : evalAnB(anb, typeFilteredTotal - typeFilteredIndex + 1);
          }
        }
      }
      // Legacy An+B path — no `of` clause.
      switch (pseudo.name) {
        case 'nth-child':         return evalAnB(anb, i1);
        case 'nth-last-child':    return evalAnB(anb, last1);
        case 'nth-of-type':       return evalAnB(anb, ti1);
        case 'nth-last-of-type':  return evalAnB(anb, tLast1);
      }
      return false;
    }
    default:                  return null; // unknown — bail
  }
}

/**
 * Match a single compound selector (no whitespace) against tag/attrs and
 * (optionally) position metadata. Returns true iff the compound's tag, id,
 * all classes, AND all pseudos match. Returns null if the compound contains
 * unsupported syntax (attr selectors, pseudo-elements, unknown pseudos).
 *
 * `pos` is optional; when omitted, pseudo-checks degrade to "true if the
 * carve-out applies, false otherwise" — i.e. we only resolve `:root` /
 * child-indexed pseudos for the synthetic root. Callers that walk the body
 * tree pass full position data and get spec-accurate matching for nested
 * elements too.
 *
 * `ctx` (swarm-003 Bug 3): optional `{ definedTags?: Set<string> }` so
 * `:defined` can decide custom-element registration. Threaded through
 * from selectorMatches → propsForElement. Default empty object keeps
 * legacy call sites green.
 *
 * `ancestors` (wave-30 A1): the ancestor chain of the element BEING MATCHED
 * (document order, immediate parent last). Only `:dir()` reads it, because
 * directionality is inherited down the tree (HTML §3.2.6.4) and cannot be
 * decided from tag/attrs/pos alone. Every caller that has a chain must pass
 * the one belonging to THIS element — a sibling step passes the shared
 * parent chain, an ancestor step passes that ancestor's own prefix.
 *
 * NOTE: this function only checks the host-element side of the compound;
 * the `pseudoElement` field on parseCompound's output is observed by
 * selectorMatchesPseudoElement(), not here. Returning `true` here means
 * "the compound's host-side filters match"; the caller may still need to
 * dispatch the rule onto a pseudo-element bucket.
 */
function compoundMatches(compound, tag, attrs, pos = null, ctx = {}, ancestors = null) {
  const parsed = parseCompound(compound);
  if (parsed.unsupported) return null;
  if (parsed.needTag && parsed.needTag !== '*' && parsed.needTag !== tag) return false;
  if (parsed.needId && attrs.id !== parsed.needId) return false;
  if (parsed.needClasses.length) {
    const have = new Set((attrs.class || '').split(/\s+/).filter(Boolean));
    for (const c of parsed.needClasses) if (!have.has(c)) return false;
  }
  // Pseudo evaluation. Each pseudo must pass; first that fails → no match.
  // When `pos` is null we can still answer `:root` (false) and the
  // child-indexed ones (false unless we can prove otherwise) — the safe
  // default is "rule doesn't match this element", NOT "rule matches".
  for (const ps of parsed.pseudos) {
    const result = evalPseudo(ps, pos, tag, attrs, ctx, ancestors);
    if (result !== true) return false;
  }
  return true;
}

/**
 * Match a single rule's selector against an element's attrs (and optional
 * ancestor chain for descendant combinators).
 *
 * Supports:
 *   - tag selectors: `div`, `*`
 *   - class selectors: `.foo`, `.foo.bar` (must match all)
 *   - id selectors: `#bar`
 *   - tag.class / tag#id
 *   - descendant selectors with whitespace: `ol li`, `.parent .target`,
 *     `body p` — when `ancestors` is provided each non-rightmost compound
 *     must match SOME ancestor in document order (Bug 2 fix). When no
 *     ancestors are passed (legacy callers) we fall back to "rightmost
 *     compound only" so existing call sites stay green.
 *   - child combinators: `A > B` (wave-8 — `.flex > div` in the WPT
 *     css-flexbox abspos family). The compound left of `>` must match the
 *     IMMEDIATE parent (last `ancestors` entry); chains (`A > B > C`)
 *     consume ancestors right-to-left. No legacy fallback: without an
 *     ancestor chain a child-combinator rule never matches.
 *   - sibling combinators: `A + B` (next-sibling, Selectors-4 §15.4) and
 *     `A ~ B` (subsequent-sibling, §15.5) — wave-30 A2, resolved against
 *     the subject's `pos.siblings` list. Same no-legacy-fallback rule as
 *     `>`: without an ancestor chain the rule never matches.
 *
 * Attribute selectors are unsupported — when they appear anywhere in the
 * selector we skip the rule.
 *
 * Returns boolean. For pseudo-element-aware matching (`::before`/`::after`/
 * `::marker` rules that should attach to a synthetic child bucket) use
 * selectorMatchesPseudoElement() — selectorMatches stays a thin wrapper
 * over that so existing call sites keep returning the same booleans they
 * always did.
 */
export function selectorMatches(sel, tag, attrs, ancestors = null, pos = null, ctx = {}) {
  const r = selectorMatchesPseudoElement(sel, tag, attrs, ancestors, pos, ctx);
  // Empty string = host-element match (no pseudo-element). null = no match.
  // Any pseudo-element string ('before'/'after'/'marker') is treated as a
  // non-match here so legacy callers still get the boolean truth for the
  // host element specifically. buildComponents uses the
  // *PseudoElement variant directly to access the pe info.
  return r === '';
}

/**
 * swarm-003 Bug 1 (css-lists__counter-001, css-pseudo__before-*): the
 * pseudo-element-aware match function. Returns:
 *   - `''` (empty string) when the rule's rightmost compound matches the
 *     host element with no pseudo-element suffix (i.e. the rule attaches
 *     to the host's regular `properties` bucket).
 *   - `'before'` / `'after'` / `'marker'` when the rule's rightmost
 *     compound carries `::before` / `::after` / `::marker` and the host
 *     side of that compound matches; the rule attaches to
 *     `_pseudo.<name>.properties` on the host.
 *   - `null` when the rule doesn't match (or contains unsupported syntax).
 *
 * Semantics for the host-side check are IDENTICAL to selectorMatches —
 * the pseudo-element suffix is stripped off the rightmost compound by
 * parseCompound and the rest is matched normally. Ancestors are walked
 * exactly as before so `#test span::before` matches a span whose
 * ancestor chain contains `#test`.
 */
export function selectorMatchesPseudoElement(
  sel, tag, attrs, ancestors = null, pos = null, ctx = {},
) {
  // wave-8: tokenise into compounds + combinators. Sibling combinators
  // (`+`/`~`) and malformed chains return null from splitSelectorChain —
  // same "unsupported → rule dropped" contract the old `/[>+~]/` blanket
  // reject had, except `>` (child) is now a first-class combinator.
  const chain = splitSelectorChain(sel);
  if (chain === null) return null;
  const { compounds, combinators } = chain;
  if (compounds.length === 0) return null;
  const last = compounds[compounds.length - 1];
  // Inspect the rightmost compound for a pseudo-element suffix BEFORE
  // matching. parseCompound records `pseudoElement` when the compound
  // ends with `::before` / `::after` / `::marker`.
  const parsedLast = parseCompound(last);
  if (parsedLast.unsupported) return null;
  // Position metadata for the element itself drives rightmost-compound
  // pseudo evaluation (e.g. `.foo:first-child` against this very element).
  // wave-30 A1: the ancestor chain rides along so a subject-side `:dir()`
  // can inherit its directionality from an ancestor's `dir` attribute.
  const lastResult = compoundMatches(last, tag, attrs, pos, ctx, ancestors);
  if (lastResult !== true) return null;
  const pe = parsedLast.pseudoElement || '';
  // Single-compound selector — done.
  if (compounds.length === 1) return pe;
  // Pre-flight every non-rightmost compound for unsupported syntax so the
  // rule bails uniformly (legacy behaviour: unsupported ANYWHERE in the
  // chain → null, never a partial match).
  for (let ci = 0; ci < compounds.length - 1; ci++) {
    // wave-34 lane R: `isSubject = false` — these are the ancestor/sibling
    // compounds, where `:has()` is declined (see its branch in parseCompound).
    // Because this pre-flight bails FIRST, matchPrefix below never reaches a
    // relational ancestor compound, which is why its own compoundMatches
    // calls can keep the permissive default.
    if (parseCompound(compounds[ci], false).unsupported) return null;
  }
  // Without ancestors, preserve the legacy "rightmost compound only"
  // fallback for pure-DESCENDANT chains so old call sites (and the
  // existing test 'descendant — applies last compound') keep passing.
  // Structural chains get NO such degrade: `.flex > div` matching any bare
  // `<div>` would be a structural over-match, and so would `.a + div`, so we
  // bail honestly (wave-30 A2 extends the wave-8 rule to `+`/`~`).
  if (!ancestors) return combinators.some((c) => EXPLICIT_COMBINATORS.has(c)) ? null : pe;
  /**
   * Right-to-left chain matcher with backtracking (Selectors-4 §16 match
   * semantics, evaluated right-to-left like real engines):
   *   - compounds[ci] must match an ancestor at index ≤ maxIdx;
   *   - combinators[ci] ('>' = child) pins compounds[ci] to EXACTLY
   *     ancestors[maxIdx] (the immediate parent of whatever matched
   *     compounds[ci+1] — the element itself for the rightmost step,
   *     since `ancestors` is in document order with the immediate parent
   *     LAST);
   *   - ' ' (descendant) lets compounds[ci] match ANY ancestor at ≤ maxIdx,
   *     with backtracking so mixed chains like `.a > .b .c` don't false-
   *     negative when the greedy nearest `.b` candidate has the wrong
   *     parent. Chains in the WPT corpus are ≤3 compounds, so the
   *     backtracking cost is negligible.
   *   - wave-30 A2: '+' (Selectors-4 §15.4) pins compounds[ci] to the
   *     IMMEDIATELY PRECEDING sibling of whatever matched compounds[ci+1];
   *     '~' (§15.5) lets it match ANY preceding sibling, with the same
   *     backtracking. Sibling steps do NOT consume an ancestor — siblings
   *     share one parent, so `maxIdx` is passed through unchanged.
   *
   * `curPos` is the position metadata of the element compounds[ci+1] matched
   * (the subject itself at the first step). Sibling steps read its
   * `siblings` / `sibIndex`; ancestor steps replace it with the matched
   * ancestor's own pos as they climb.
   *
   * Each ancestor entry carries its own position metadata so pseudos on
   * non-rightmost compounds (e.g. `:root:first-child .target`) evaluate
   * against the right element. The synthetic `:root` sentinel ancestor
   * prepended in propsForElement has `isRoot: true` so the Selectors-4
   * §6.4.1 carve-out fires.
   */
  const matchPrefix = (ci, maxIdx, curPos) => {
    if (ci < 0) return true; // whole chain consumed — match
    const rel = combinators[ci]; // relates compounds[ci] → compounds[ci+1]
    if (rel === '+' || rel === '~') {
      // Sibling combinators. The candidate set is the entries of the
      // CURRENT element's own sibling list that precede it: exactly one for
      // '+' (adjacency), all of them for '~'.
      const sibs = curPos?.siblings;
      const here = curPos?.sibIndex;
      // No sibling metadata ⇒ adjacency is unprovable. Answer "no match"
      // rather than degrading to a descendant/any-element match, which
      // would apply the rule to boxes the browser never styles.
      if (!Array.isArray(sibs) || typeof here !== 'number') return false;
      // Both flavours scan leftwards from the nearest preceding sibling;
      // '+' simply stops after that first candidate.
      const stop = rel === '+' ? here - 1 : 0;
      for (let k = here - 1; k >= stop; k--) {
        const sib = sibs[k];
        if (!sib) continue;
        const sibPos = siblingPositionMeta(sibs, k);
        // A sibling's ancestor chain is the current element's chain down to
        // its parent — `ancestors[0…maxIdx]` — which is what `:dir()` on the
        // sibling compound needs to inherit from (wave-30 A1).
        if (compoundMatches(compounds[ci], sib.tag, sib.attrs ?? {}, sibPos, ctx,
          ancestors.slice(0, maxIdx + 1)) === true
            && matchPrefix(ci - 1, maxIdx, sibPos)) return true;
      }
      return false;
    }
    if (rel === '>') {
      // Child combinator: compounds[ci] must match the IMMEDIATE parent
      // (the highest ancestor index still available). No scan, no
      // backtracking at this step — the child relation is exact.
      if (maxIdx < 0) return false; // ran out of ancestors
      const a = ancestors[maxIdx];
      if (compoundMatches(compounds[ci], a.tag, a.attrs, a.pos ?? null, ctx,
        ancestors.slice(0, maxIdx)) !== true) return false;
      return matchPrefix(ci - 1, maxIdx - 1, a.pos ?? null);
    }
    // Descendant combinator: try every remaining ancestor from nearest to
    // farthest, backtracking into the rest of the chain on each candidate.
    for (let j = maxIdx; j >= 0; j--) {
      const a = ancestors[j];
      if (compoundMatches(compounds[ci], a.tag, a.attrs, a.pos ?? null, ctx,
        ancestors.slice(0, j)) === true
          && matchPrefix(ci - 1, j - 1, a.pos ?? null)) return true;
    }
    return false;
  };
  return matchPrefix(compounds.length - 2, ancestors.length - 1, pos) ? pe : null;
}

/**
 * wave-30 A3: how many of these rules can the static matcher NEVER apply?
 *
 * A rule is counted when its selector cannot be reduced to a matchable
 * chain — either splitSelectorChain refuses it (malformed) or ANY compound
 * comes back `unsupported` from parseCompound (attribute selector, an
 * unmodelled pseudo like `:has()` / `:hover`, an invalid `:dir()` argument,
 * a pseudo-element we do not generate). Those rules are silently absent from
 * every component's cascade, so the static fixture is a document the browser
 * would never paint.
 *
 * NOT a lossy marker and NOT an error: a dropped rule is often perfectly
 * correct (an INVALID selector — `:dir(ltrr)` — SHOULD drop its rule, and a
 * `:hover` rule genuinely does not apply to a static capture). The number is
 * a TRIGGER INPUT: post-load-extract.mjs reads it to decide that a live
 * browser — which understands every selector we do not — is worth consulting
 * for this test. See shouldPostLoadExtract there for the trigger contract.
 *
 * Counts RULES, not selectors: parseCss has already exploded each
 * comma-separated selector list into one rule per selector, so
 * `p:hover, div { … }` contributes exactly 1 here (the `p:hover` half),
 * matching the fact that only that half is missing from the cascade.
 *
 * Exported so the unit tests and post-load-extract share ONE definition of
 * "the matcher dropped this".
 */
export function countUnsupportedRules(rules) {
  let n = 0;
  for (const r of rules ?? []) {
    const chain = splitSelectorChain(r.selector);
    // Malformed / untokenisable selector — nothing to match against.
    if (!chain || chain.compounds.length === 0) { n++; continue; }
    // Any unsupported compound kills the WHOLE rule in our matcher
    // (selectorMatchesPseudoElement pre-flights every compound), so the
    // count must use the same all-compounds test, not just the rightmost.
    // wave-34 lane R: the rightmost compound is the SUBJECT, every other one
    // is not — the identical split selectorMatchesPseudoElement applies, so
    // the counter and the matcher can never disagree about which rules the
    // static pass really applies (that agreement is the whole contract of
    // this function's "ONE definition" docstring).
    const lastIdx = chain.compounds.length - 1;
    if (chain.compounds.some((c, ci) => parseCompound(c, ci === lastIdx).unsupported)) n++;
  }
  return n;
}

/** Compute IR property dict for a body element by collecting every CSS
 *  rule whose selector matches it, plus its inline `style="…"`.
 *  Last-write-wins matches CSS cascade for same-specificity rules.
 *
 *  `ancestors` (Bug 2): when provided, descendant selectors like `ol li`
 *  can match this element by checking that each non-rightmost compound
 *  matches some entry in the ancestor chain (in document order). Legacy
 *  callers omit the arg and get the old single-compound behaviour.
 *
 *  `ctx` (swarm-003 Bug 3): optional `{ definedTags?: Set<string> }`
 *  consulted by `:defined`. When omitted, `:defined` only answers true
 *  for built-in HTML tags.
 *
 *  swarm-003 Bug 1: rules whose rightmost compound carries
 *  `::before` / `::after` / `::marker` are routed into a separate
 *  `pseudo` bucket (`{ before?: {...}, after?: {...}, marker?: {...} }`)
 *  so buildComponents can attach them to `cmp._pseudo` instead of the
 *  host's flat properties. CSS Generated Content L3 §3.2 only honours
 *  `content` on pseudo-elements; mixing the rule into the host's
 *  declarations would mis-apply it everywhere.
 */
export function propsForElement(rules, tag, attrs, ancestors = null, pos = null, ctx = {}) {
  const props = {};
  const matchedRules = [];
  // swarm-003 Bug 1: per-pseudo-element bucket. Key is 'before'/'after'/
  // 'marker'; value is the merged property dict from every rule matching
  // that pseudo on this host. Last-write-wins per CSS cascade order.
  const pseudo = {};
  // Bug 2 fix: inject the synthetic document scaffolding at the head of the
  // chain so selectors like `:root:first-child #a` find a `:root` to
  // match against. We only do this when an ancestor chain was supplied
  // (callers that pass `null` are the legacy direct-call test surface
  // which wouldn't expect the sentinel to appear in their fixtures).
  // wave-36 M7: the scaffolding is `html` THEN `body` (see
  // DOCUMENT_SENTINEL_ANCESTORS) — the walker's chain starts inside body, so
  // without the body entry no `body …` / `body > …` rule could ever match.
  const chain = ancestors ? [...DOCUMENT_SENTINEL_ANCESTORS, ...ancestors] : null;
  // wave-37 lane W8: the matched bags are collected FIRST so the layered
  // path can see all of them at once. `buckets['']` is the host, the rest
  // are pseudo-element names.
  const buckets = { '': [] };
  for (const r of rules) {
    const m = selectorMatchesPseudoElement(r.selector, tag, attrs, chain, pos, ctx);
    if (m === null) continue;
    matchedRules.push(r);
    (buckets[m] ??= []).push(r);
  }
  // Inline style="..." — parsed once, importance kept for the layered path.
  const inlineProps = {};
  const inlineImportant = {};
  if (attrs.style) {
    for (const decl of attrs.style.split(';')) {
      const colon = decl.indexOf(':');
      if (colon < 0) continue;
      const k = decl.slice(0, colon).trim();
      const raw = decl.slice(colon + 1).trim();
      const v = raw.replace(/\s*!important\s*$/i, '').trim();
      if (k && v && !k.startsWith('--')) {
        inlineProps[k] = v;
        if (/\s*!important\s*$/i.test(raw)) inlineImportant[k] = true;
      }
    }
  }
  // THE GATE. Unless a `@layer` rule matched, or some declaration says
  // `revert-layer`, nothing about this element's cascade is layered — take
  // the historical last-write-wins path so every non-layer fixture in the
  // corpus stays byte-identical.
  const layered = matchedRules.some((r) => typeof r.layerIdx === 'number')
    || matchedRules.some((r) => hasRevertLayer(r.props))
    || hasRevertLayer(inlineProps);
  if (!layered) {
    for (const r of buckets['']) Object.assign(props, r.props);
    for (const [m, rs] of Object.entries(buckets)) {
      if (m === '') continue;
      pseudo[m] = {};
      for (const r of rs) Object.assign(pseudo[m], r.props);
    }
    // Inline style="..." trumps everything.
    Object.assign(props, inlineProps);
    return { props, matchedRules: matchedRules.length, pseudo };
  }
  // Layered path — css-cascade-5 §6.4.4 sort order, `revert-layer` resolved.
  const layerCount = layerCountOf(rules);
  const toGroup = (r) => ({
    rank: typeof r.layerIdx === 'number' ? r.layerIdx : layerCount,
    props: r.props,
    important: r.important,
  });
  Object.assign(props, resolveLayeredCascade(
    [...buckets[''].map(toGroup),
      { rank: layerCount + 1, props: inlineProps, important: inlineImportant, inline: true }],
    layerCount,
  ));
  for (const [m, rs] of Object.entries(buckets)) {
    if (m === '') continue;
    // A pseudo-element bucket has no style attribute of its own.
    pseudo[m] = resolveLayeredCascade(rs.map(toGroup), layerCount);
  }
  return { props, matchedRules: matchedRules.length, pseudo };
}

/**
 * Bug 3 + Bug 4 (pilot-001 / css-contain/contain-body-bg-001 +
 * css-backgrounds/background-color-animation-in-body):
 *
 * The extractor previously only emitted body's CHILDREN as components, so
 * any rule scoped to the body itself (`body { background: red }`,
 * `body { contain: layout }`, `body { font-family: serif }`,
 * `html { color: blue }`) was parsed by parseCss but never matched against
 * any component — silently lost. The blast radius is large: every
 * css-contain/contain-body-* test, every background test that paints on
 * body, every typography test that sets the document base font.
 *
 * This helper picks out the rules whose rightmost compound is `body`,
 * `html`, or `*` (root-level scopes) and returns the merged property bag.
 * The fixture builder uses this to emit a synthetic root component.
 *
 * Returns `{ props, matchedRules, pseudo }`. Cascade order matches parseCss
 * output (last write wins for same-specificity).
 *
 * wave-27 A-RC2 — PSEUDO-ELEMENT RULES NO LONGER LEAK INTO THE FLAT BAG.
 * `parseCompound` accepts `html::before` (needTag 'html', pseudoElement
 * 'before'), and every acceptance test below passed on the TAG alone, so a
 * rule like css-contain/contain-body-dir-001's
 * `html::before { content:""; width:100px; height:100px; background:orange;
 * display:block }` was `Object.assign`-ed straight into the BODY's own
 * declarations. Two wrongs at once, both MEASURED on that family:
 *   1. the generated box was LOST — no `_pseudo` bucket was ever built for
 *      the root, so nothing rendered the orange 100×100 square; and
 *   2. its declarations CLOBBERED the body's (background:orange became the
 *      body's own background, `content:""`/`display:block` rode along),
 *      which then flooded the whole canvas through the body→canvas
 *      background-propagation channel A-RC1 gates.
 * Per CSS Generated Content L3 §3.2 a `content` declaration is only honoured
 * ON a pseudo-element, and per Selectors-4 §3.3 a `::pseudo` rule styles the
 * generated box, never its originating element. So pseudo-element rules are
 * routed into a `pseudo` bucket — `{ before?: {...}, after?: {...} }`,
 * EXACTLY the shape `propsForElement` returns — which buildComponents turns
 * into the body-root's `cmp._pseudo`, so the generated box renders as a real
 * pseudo box on the root instead of vandalising the body's bag.
 */
export function propsForBodyRoot(rules) {
  const props = {};
  // wave-27 A-RC2: per-pseudo-element bucket for root-scope `::before` /
  // `::after` / `::marker` rules. Key is the pe name, value the merged
  // declaration dict — the same shape (and the same last-write-wins cascade)
  // `propsForElement` builds, so the emit site can treat both identically.
  const pseudo = {};
  let matchedRules = 0;
  for (const r of rules) {
    // We treat body / html / * / :root (and combinations like `html, body`)
    // as root-scope. The parser already split comma-lists into one rule per
    // selector, so each `r.selector` is a single selector.
    const sel = r.selector.trim();
    // Reject combinators anywhere. NOTE: selectorMatches now supports the
    // child combinator (wave-8), but a combinator NEVER qualifies as root
    // scope — `body > div` styles the <div>, not the body root — so this
    // reject stays a flat regex regardless.
    if (/[>+~]/.test(sel)) continue;
    // Bug 2c (selectors__child-indexed-no-parent): the prior `[\[:]` reject
    // also dropped `:root`, the spec's pseudo-class for the document root.
    // Refuse attribute syntax (`[…]`) outright, but for pseudos parse the
    // compound and accept it iff every pseudo is in the supported set AND
    // every pseudo is one of the "always true on root" kinds (root + the
    // child-indexed family per Selectors-4 §6.4.1 carve-out). This lets
    // `:root`, `:root:first-child`, `html:nth-of-type(1)` etc. land on the
    // synthetic body component without leaking unsupported syntax.
    if (sel.includes('[')) continue;
    // Only single-compound selectors qualify as "root scope". A descendant
    // selector like `body p` is for the <p>, not for the body root.
    const compounds = splitCompounds(sel);
    if (compounds.length !== 1) continue;
    const parsed = parseCompound(compounds[0]);
    if (parsed.unsupported) continue;
    // Tag / id / class must be one of the root-scope shapes. We accept:
    //   - bare tag in {body, html, *}
    //   - bare `:root` (no needTag at all)
    //   - `:root` combined with bare tags (e.g. `html:root`)
    const tagOk = !parsed.needTag ||
                  parsed.needTag === 'body' ||
                  parsed.needTag === 'html' ||
                  parsed.needTag === '*';
    if (!tagOk) continue;
    // Disallow id/class — those imply a specific element instance, not the
    // root scope (`html.foo` would be misleading at this scope).
    if (parsed.needId || parsed.needClasses.length) continue;
    // Pseudos: every one must be in SUPPORTED_PSEUDOS (or functional set)
    // AND must be true on the document root. For the parentless-root carve-
    // out every child-indexed pseudo passes; `:root` passes by definition;
    // `:empty` we leave conservative (false unless body really is empty,
    // which we can't tell here) — skip rules carrying `:empty` since that
    // would mean "body has no kids" and we'd need DOM access to confirm.
    let hasRootMarker = !parsed.needTag; // bare `:root` has no tag — derive
    let allOk = true;
    for (const ps of parsed.pseudos) {
      if (ps.name === 'root') { hasRootMarker = true; continue; }
      if (ps.name === 'empty') { allOk = false; break; }
      // Selectors-4 §6.4.1 carve-out evaluation for the root. Mirrors
      // evalPseudo()'s isRoot branch exactly so propsForBodyRoot agrees
      // with selectorMatches on what root-only-and-pseudo combos pass.
      switch (ps.name) {
        case 'first-child': case 'last-child': case 'only-child':
        case 'first-of-type': case 'last-of-type': case 'only-of-type':
          // Always-true on the parentless root.
          break;
        case 'nth-child': case 'nth-last-child':
        case 'nth-of-type': case 'nth-last-of-type':
          // Only matches when the An+B formula yields index 1.
          if (!evalAnB(ps.arg, 1)) { allOk = false; }
          break;
        default:
          allOk = false;
      }
      if (!allOk) break;
    }
    if (!allOk) continue;
    // Must end up looking like a body-scope rule: tag in {body,html,*} OR
    // a `:root` marker present.
    if (!hasRootMarker && !parsed.needTag) continue;
    matchedRules++;
    // wave-27 A-RC2 — THE SPLIT. A rule whose rightmost compound carries a
    // pseudo-element styles the GENERATED box (Selectors-4 §3.3), not the
    // root element, so its declarations go to the pe bucket; only true
    // host-element rules merge into the flat root bag as before. Without
    // this branch `html::before { background: orange }` became the BODY's
    // background (see the banner's measured contain-body-dir family).
    if (parsed.pseudoElement) {
      // Last write wins per pe, mirroring propsForElement's cascade.
      if (!pseudo[parsed.pseudoElement]) pseudo[parsed.pseudoElement] = {};
      Object.assign(pseudo[parsed.pseudoElement], r.props);
      continue;
    }
    Object.assign(props, r.props);
  }
  // css-lists-3 §3.1: a `::marker` box is generated ONLY by a box with
  // `display: list-item`. The acceptance rules above deliberately admit a
  // BARE `::marker { … }` (no tag ⇒ `hasRootMarker` true), which is a
  // UNIVERSAL marker rule — it belongs to every real list item, and
  // propsForElement already attaches it to each `<li>`'s own `_pseudo`.
  // Hanging it on the html/body root too would paint a phantom marker on
  // the page root, so it is dropped HERE, once the merged bag is complete
  // and `display` is knowable. Not a silent loss: the identical rule still
  // reaches every list item through propsForElement — this scope is the
  // only place it does not apply.
  if (pseudo.marker && !isListItemDisplay(props.display)) delete pseudo.marker;
  return { props, matchedRules, pseudo };
}

/**
 * Is a `display` declaration one that generates a ::marker box? css-lists-3
 * §3.1 ties markers to `display: list-item`, which the css-display-3 §2
 * two-value grammar also spells as `<display-outside>? list-item`
 * (`block flow list-item`, `inline list-item`, …). Case-insensitive, and a
 * missing declaration is NOT a list item (the html/body initial `display`
 * is `block`). Exported for the unit suite.
 */
export function isListItemDisplay(value) {
  if (typeof value !== 'string') return false;         // undeclared ⇒ not list-item
  // Token scan rather than equality so the two-value syntax passes too.
  return value.toLowerCase().split(/\s+/).includes('list-item');
}

// ── wave-21 A-RC6: sibling-index() extract-time baking ───────────────────────
//
// CSS Values 5 §5.1 tree-counting functions: `sibling-index()` resolves to
// the element's 1-based index among its element siblings. The converter's
// value parsers cannot know the document position, so a declaration like
// `background: conic-gradient(hsl(calc(50deg * sibling-index()) 100% 50%), …)`
// was unparseable → forwarded raw → dropped by ALL three engines (blank on
// every platform). The extractor is the one place that DOES know the
// position, so we bake the index at extract time — same precedent as the
// wave-15 `dir=auto` first-strong resolution and the wave-13 keyframe
// sampler: a statically-knowable runtime value is resolved into the fixture
// and LOUDLY marked ('baked-sibling-index' in _lossyReasons). The marker is
// informational only — lossyReasons never gates scoring (scoreEligible is
// driven by the notApplicable tag channel in inject-wpt-block.mjs), it just
// keeps the dashboard's provenance honest.

/**
 * Constant-fold trivial `calc(A * B)` products where at most one factor
 * carries a unit — the shape sibling-index() substitution leaves behind
 * (`calc(50deg * 2)` → `100deg`, `calc(2 * 30px)` → `60px`). Anything
 * else (nested parens, +/-/÷, two units, extra terms) is left VERBATIM:
 * the generic calc() lossy lane already covers those, and partial folds
 * would risk changing meaning. Exported so unit tests can pin the fold.
 */
export function foldTrivialCalcProducts(value) {
  // One regex pass; each match is an ENTIRE calc(...) whose body is exactly
  // `<num><unit?> * <num><unit?>` (css-values-4 §10.1 product). The body
  // charset excludes '(' so nested functions can never half-match.
  return value.replace(
    /calc\(\s*(-?(?:\d+\.?\d*|\.\d+))([a-z%]*)\s*\*\s*(-?(?:\d+\.?\d*|\.\d+))([a-z%]*)\s*\)/gi,
    (m, a, au, b, bu) => {
      // Two units (e.g. 2px * 3px) is invalid CSS — don't "fix" it, keep
      // the raw calc so the failure stays visible downstream.
      if (au && bu) return m;
      const prod = parseFloat(a) * parseFloat(b);
      // Guard non-finite results (overflow) — keep the original text.
      if (!Number.isFinite(prod)) return m;
      // Trim float noise (0.1*3 → 0.3, not 0.30000000000000004) while
      // keeping integers clean; 6 decimals is beyond CSS render precision.
      const num = Number.isInteger(prod) ? String(prod) : String(+prod.toFixed(6));
      return `${num}${au || bu}`;
    },
  );
}

/**
 * Substitute `sibling-index()` with the element's 1-based renderable-sibling
 * index (pos.domSibIndex + 1 — see extractBodyTreeNested's A-RC6 note) in
 * every string prop, then constant-fold the trivial products the
 * substitution exposes. MUTATES the bag in place (same contract as
 * bakeSampledAnimation). Returns true when any value was rewritten so the
 * caller can add the 'baked-sibling-index' lossy note. Exported for tests.
 */
export function bakeSiblingIndex(props, index1) {
  let baked = false;
  for (const [k, v] of Object.entries(props)) {
    // Only string values can carry the function; the () suffix keeps the
    // match away from any future `sibling-index` keyword usage.
    if (typeof v !== 'string' || !/sibling-index\(\)/i.test(v)) continue;
    const substituted = v.replace(/sibling-index\(\)/gi, String(index1));
    props[k] = foldTrivialCalcProducts(substituted);
    baked = true;
  }
  return baked;
}

// ── Lossy detection ──────────────────────────────────────────────────────────
//
// Per Section 4.3, properties whose VALUES can't be pre-resolved get a
// `_lossy: true` marker. We don't rewrite the value — the IR pipeline still
// gets a best-effort string — but we record the reason so the dashboard can
// present results in a separate "expected gap" lane.
const LOSSY_RE = {
  vw:    /\b\d+(?:\.\d+)?(?:vw|vh|vmin|vmax|dvw|dvh|svw|svh|lvw|lvh)\b/i,
  varRef:/\bvar\(\s*--/i,
  calcN: /\bcalc\([^)]*?\d+(?:em|rem|ex|ch|vw|vh|vmin|vmax|dvw|dvh|%|fr)/i,
  ems:   /\b\d+(?:\.\d+)?(?:em|rem)\b/i,
  pct:   /\b\d+(?:\.\d+)?%/,
};
function lossyReasonsFor(props) {
  const reasons = new Set();
  for (const v of Object.values(props)) {
    if (typeof v !== 'string') continue;
    if (LOSSY_RE.vw.test(v))     reasons.add('viewport-units');
    if (LOSSY_RE.varRef.test(v)) reasons.add('var()-reference');
    if (LOSSY_RE.calcN.test(v))  reasons.add('calc()-non-px');
    if (LOSSY_RE.ems.test(v))    reasons.add('em/rem');
    if (LOSSY_RE.pct.test(v))    reasons.add('percentage');
  }
  return [...reasons];
}

// ── Static @keyframes sampler (wave-13 KEYFRAMES-SAMPLER) ────────────────────
//
// CONFIRMED wave-13 audit finding: the extractor never evaluated @keyframes,
// so WPT's *time-stable* animation tests — the negative-delay + slope-zero-
// easing engineering used across css-backgrounds/animations/ (e.g.
// background-color-animation-in-body.html: `animation: bgcolor 1000000s
// cubic-bezier(0,1,1,0) -500000s`, screenshot expected to be a uniform
// rgb(100, 100, 0) at 50% progress) — extracted to a bare `animation`
// shorthand no runtime executes, and scored 0.62 against the browser-ref as
// FAKE renderer divergence, while alpha-0 siblings passed vacuously.
//
// These tests are deliberately engineered so the animated value is CONSTANT
// at screenshot time: a huge duration, a negative delay placing t=0 at a
// fixed progress, and a timing function whose slope is zero there. That
// makes the value statically computable — no timeline execution needed:
//
//     progress = -delay / duration          (Web Animations §4.8.3.1, the
//                                            "current iteration progress"
//                                            at local time 0)
//
// We therefore parse @keyframes blocks, and when a component's animation
// declaration resolves to a NEGATIVE delay strictly inside the first
// iteration (0 < progress < 1 — inside the ACTIVE phase, where
// animation-fill-mode can never matter, css-animations-1 §5.4), we
// interpolate every keyframed property at that progress and BAKE the
// sampled values into the component's extracted properties. The
// animation-* declarations themselves are dropped (nothing downstream can
// execute them) and the component is marked `_lossy` with the
// 'sampled-animation' reason so the dashboard sees an honest static
// approximation, not a silent rewrite.
//
// HARD SCOPE BOUNDARY (bounded by design — everything below falls back to
// extracting the declarations verbatim exactly as before this wave, and any
// pre-existing bucketer tags, e.g. wpt-not-applicable.mjs Rule 3's
// 'requires-animation-runtime', stay untouched):
//   - zero or positive delay — SUPERSEDED by wave-35 B7 (see the
//     STABILITY-WINDOW section immediately below); the wave-13 rule read
//     "value at t=0 is the pre-animation state or depends on fill-mode
//     backwards — timeline-dependent", which is true only when the value
//     actually drifts inside the capture window;
//   - progress landing exactly on 0/1 or outside the first iteration
//     (fill-mode / iteration composite dependent);
//   - animation-direction other than 'normal' (directed-progress mapping
//     not needed by the time-stable corpus);
//   - comma-separated multi-animation lists (composite ordering);
//   - keyframes missing a bracketing frame for a property (the fallback is
//     the element's UNDERLYING computed value — css-animations-1 §4 — which
//     static extraction cannot know);
//   - value pairs that are neither sRGB-parsable colors nor same-unit
//     numerics (no discrete/50% flip, no list interpolation).
//
// Interpolation model (mirrors css-animations-1 §4 keyframe selection):
// pick the property-specific bracketing keyframes around `progress`,
// compute the segment-local progress, ease it with the PREVIOUS keyframe's
// own animation-timing-function when declared there (css-animations-1
// "Timing functions for keyframes" — a tf declared ON a keyframe governs
// the segment FROM that keyframe) falling back to the element-level one,
// then lerp. Colors lerp per-component in sRGB — css-color-4 §13
// (Interpolation) specifies premultiplied-alpha interpolation, and its
// legacy-behavior caveat is exactly what Chromium does for these legacy
// rgb()/rgba() tests: straight sRGB component space, premultiplied by
// alpha. We reproduce that (premultiplied sRGB lerp), which the pinned
// test confirms: rgb(0,200,0) → rgb(200,0,0) at eased 0.5 = rgb(100,100,0).

// ── wave-35 B7: the STABILITY WINDOW (non-negative-delay sampling) ───────────
//
// MEASURED wave-35 finding: the wave-13 "delay must be strictly negative"
// boundary is not the real determinism criterion — it is one *sufficient*
// condition among several. What actually makes a WPT animation statically
// renderable is that the effect value does not CHANGE across the window in
// which the reference screenshot is taken. WPT's own -ref files say so out
// loud: background-color-animation-with-images.html declares
// `animation: blue-anim 100s` (delay ZERO) over
// `@keyframes blue-anim { 0% {…rgb(0,0,199)} 100% {…rgb(0,0,200)} }`, and its
// committed reference background-color-animation-with-images-ref.html bakes
// the literal static declaration `background-color: rgb(0, 199)`… i.e.
// `rgb(0, 0, 199)` — the progress-ZERO keyframe value. The reftest asserts
// the t=0 value precisely because the endpoints are engineered one 8-bit
// step apart, so no realistic capture latency can move the rendered pixel.
//
// So the wave-35 extension replaces "delay < 0" (kept intact as the frozen
// path) with a PROOF obligation for the newly admitted delay ≥ 0 case:
//
//   sample the effect value at BOTH ends of the capture window and admit
//   the bake only when the two serialize IDENTICALLY.
//
// Window size. The reference capture is `capture-browser-ref.mjs`, which
// waits `page.goto(…, {waitUntil:'load'})`, then document.fonts.ready, then
// 50 ms + 50 ms of settle, then two requestAnimationFrames — i.e. the
// screenshot lands ~100–200 ms after the document's animation timeline
// starts. CAPTURE_WINDOW_MS is set to 1000 ms: a ≥5× margin over that
// measured latency, and the same order of magnitude WPT itself budgets when
// it writes "we accommodate lengthy delays in running the test … especially
// on debug builds" (background-color-animation-in-body.html's own comment).
// Anything whose rendered value survives a full second of timeline advance
// unchanged is static for our purposes; anything that does not is REFUSED
// and rides the verbatim path exactly as before, keeping the
// 'requires-animation-runtime' wall honest instead of guessing.
//
// The window check is evaluated on the SERIALIZED bake (the same strings
// that land in the fixture), so it is quantization-aware for free: the
// images test's blue channel moves 199 → 199.0045 across the window, which
// rounds to the same `rgb(0, 0, 199)` byte string, while a genuinely dynamic
// `bgcolor 100s` over rgb(0,200,0) → rgb(200,0,0) moves to `rgb(1, 199, 0)`
// and is refused. No tolerance constant, no fudge factor — byte identity.
//
// What the window admits, all with a spec citation:
//   - delay 0 + a piecewise-constant timing function: css-easing-1 §2.4's
//     steps() holds one output value for a whole 1/n slice of the duration.
//     css-color/animation/contrast-color-interpolation.html is the measured
//     case: `steps(2, start)` over 2000s holds output progress 0.5 for the
//     first 1000 SECONDS (css-easing-1 §3.9.2 step arithmetic — the
//     jump-start rise makes even input progress 0 evaluate to 0.5).
//   - delay 0 + endpoints closer than one rendering quantum (the images
//     family above).
//   - delay 0 + identical endpoints (css-transforms
//     rotate-animation-with-will-change-transform-001.html interpolates
//     `0 1 0 44deg` → `0 1 0 44deg`; constant by inspection).
//   - `animation-play-state: paused` at ANY delay: css-animations-1 §4.2 +
//     Web Animations §4.8.3.1 fix the hold time at the animation's start, so
//     the effect value is time-INDEPENDENT and both window ends coincide by
//     construction (this is the same argument the shorthand parser's
//     play-state slot already documents).
//   - fill-mode arithmetic: Web Animations §4.8.4 phase boundaries. In the
//     BEFORE phase only `backwards`/`both` produce an effect value (progress
//     0); in the AFTER phase only `forwards`/`both` do (progress 1). Without
//     a fill the effect value is the element's UNDERLYING value, which static
//     extraction cannot know — refuse, exactly like a missing bracket frame.
//
// What it still refuses (the honest capability wall): any animation whose
// rendered value differs between the two window ends. Those keep
// wpt-not-applicable.mjs Rule 3's 'requires-animation-runtime' tag and
// extract verbatim.

// Upper bound on how long after the animation timeline's t=0 the reference
// screenshot can plausibly be taken. See the STABILITY WINDOW banner for the
// measured derivation (capture-browser-ref.mjs waits load + fonts + 50 + 50 ms
// + 2 rAF ≈ 100–200 ms; this is a ≥5× margin). Exported so tests can pin the
// arithmetic to a named constant instead of a magic literal.
export const CAPTURE_WINDOW_MS = 1000;

// The animation longhands the sampler understands + drops after baking.
// animation-composition/range are listed as DROP-ON-SAMPLE too: if present
// they'd have parsed to non-default composite behaviour we don't model, so
// parseAnimationDecl refuses to sample when they carry non-initial values.
const ANIMATION_LONGHANDS = new Set([
  'animation', 'animation-name', 'animation-duration', 'animation-delay',
  'animation-timing-function', 'animation-iteration-count',
  'animation-direction', 'animation-fill-mode', 'animation-play-state',
]);

// ── wave-27 A-RC3: properties a @keyframes block may NOT animate ────────────
//
// css-animations-1 §4 (Keyframes) is explicit: "Properties that aren't
// animatable are ignored in these rules, with the exception of
// `animation-timing-function`, the behavior of which is described below."
// parseKeyframeBody already implements the animation-timing-function carve-out
// (it lifts the declaration into the frame's `easing`); this set implements the
// other half — the declarations that must be IGNORED outright.
//
// MEASURED (wave-27 gate, css-contain/contain-animation-001): the test declares
// `div { contain: strict; animation: … paused }` with `@keyframes bad { from {
// contain: none } }`, and its own `<meta name=assert>` reads "the contain
// property is not animatable". The correct render is the STATIC cascade —
// `contain: strict` — and its ref is a plain 100px green square. That test
// survives today only by accident: `animation-delay` is 0, so the sampler's
// strictly-negative-delay boundary rejects it before any property is read. Give
// the same test a negative delay (or let a future wave widen that boundary) and
// the sampler would happily bake `contain: none` into the fixture, silently
// deleting the containment under test. This set closes that hole at the spec
// level rather than relying on an unrelated guard.
//
// Membership rule: ONLY properties whose defining spec states "Animation type:
// not animatable". Each entry carries its citation. Deliberately conservative —
// a property listed here can never be sampled, so guessing would silently drop
// legitimate bakes; anything whose animation type is `discrete` (e.g.
// `display`, `visibility`) stays OUT and keeps riding the normal path.
const KEYFRAME_NON_ANIMATABLE = new Set([
  // css-contain-2 §1.1 `contain` — "Animation type: not animatable".
  // The measured case above.
  'contain',
  // css-will-change-1 §2 `will-change` — "Animation type: not animatable".
  'will-change',
  // css-writing-modes-4: `direction` §2.1, `unicode-bidi` §2.2,
  // `writing-mode` §3.1, `text-orientation` §5.1, `text-combine-upright`
  // §9.1 — every one is "Animation type: not animatable" (they change the
  // box's inline/block axes, which has no interpolable midpoint).
  'direction', 'unicode-bidi', 'writing-mode', 'text-orientation',
  'text-combine-upright',
  // css-animations-1 §4 again, second sentence of the same paragraph: the
  // `animation-*` properties themselves are ignored inside a keyframe (the
  // spec's ONE exception, animation-timing-function, never reaches this set
  // — parseKeyframeBody consumes it into the frame's `easing`). Without
  // these a `@keyframes x { to { animation-duration: 2s } }` would be
  // "sampled" into a bogus static declaration.
  'animation', 'animation-name', 'animation-duration', 'animation-delay',
  'animation-iteration-count', 'animation-direction', 'animation-fill-mode',
  'animation-play-state',
]);

// Offset comparison epsilon: keyframe offsets and progress are exact
// decimals in the corpus (0.5, 0.05, …) but float division can wobble in
// the last bits, so "same offset" means within 1e-9.
const KF_EPS = 1e-9;

/** Split a CSS value on `sep` (',' or null = whitespace) at paren depth 0,
 *  so `cubic-bezier(0,1,1,0)` survives as ONE token inside an `animation`
 *  shorthand. Trimmed, empties dropped. */
function splitTopLevel(s, sep) {
  const out = [];            // collected tokens
  let depth = 0;             // '(' nesting depth — separators inside parens are literal
  let cur = '';              // token under construction
  for (const ch of s) {
    if (ch === '(') depth++;                       // entering a function — separators become literal
    else if (ch === ')') depth = Math.max(0, depth - 1); // leaving (clamp for stray ')')
    // A separator only splits at depth 0; ',' mode splits on commas,
    // whitespace mode on any /\s/ char.
    const isSep = depth === 0 && (sep === ',' ? ch === ',' : /\s/.test(ch));
    if (isSep) { if (cur.trim()) out.push(cur.trim()); cur = ''; }
    else cur += ch;                                // accumulate non-separator chars
  }
  if (cur.trim()) out.push(cur.trim());            // flush the trailing token
  return out;
}

/** Parse a CSS <time> token to milliseconds (css-values-4 §6.2: `s` and
 *  `ms` only). Returns null for anything else — the caller must refuse to
 *  sample rather than guess. */
function parseTimeMs(tok) {
  // Sign + decimal number + unit; unit is case-insensitive per CSS.
  const m = /^([+-]?(?:\d+\.?\d*|\.\d+))(s|ms)$/i.exec(String(tok).trim());
  if (!m) return null;                             // not a <time> — refuse
  // 1s = 1000ms (css-values-4 §6.2 canonicalizes to seconds; we use ms to
  // match the IR's time normalization table in schema/spec/02-values.md).
  return Number(m[1]) * (m[2].toLowerCase() === 's' ? 1000 : 1);
}

/**
 * Parse every `@keyframes <name> { … }` block out of a comment-stripped
 * stylesheet. parseCss deliberately skips ALL @-rules (its "tiny parser"
 * scope note), so this is a separate scan over the same text.
 *
 * Returns `{ [name]: Frame[] }` where Frame is
 * `{ offset: 0..1, props: {prop: value}, easing?: string }`, sorted by
 * offset ascending. Contract details, each mirroring css-animations-1:
 *   - `from` = 0%, `to` = 100% (§4 keyframe-selector grammar);
 *   - comma key lists (`0%, 100% { … }`) fan out to one frame per key;
 *   - duplicate offsets cascade per property, later wins (§4: "the last
 *     keyframe … wins" per property);
 *   - duplicate @keyframes NAMES: the last block in document order wins
 *     wholesale (§3: "the last one … overrides");
 *   - `animation-timing-function` inside a frame is captured as the
 *     frame's `easing` (it governs the segment FROM this frame — §4
 *     "Timing functions for keyframes") and excluded from props;
 *   - `!important` declarations inside keyframes are IGNORED entirely
 *     (§4: "important rules in a keyframe … are ignored");
 *   - custom properties (`--x`) are skipped like parseCss does.
 * Out of scope (documented, not silent): quoted keyframe names, prefixed
 * `@-webkit-keyframes`, and conditional nesting awareness — a @keyframes
 * inside @media is collected unconditionally (reftest corpus never gates
 * keyframes on media queries).
 */
export function parseKeyframes(css) {
  const out = {};                                  // name → sorted Frame[]
  // Unquoted <custom-ident> name then the opening brace. /g so we walk
  // every block in document order (last-wins is Object assignment order).
  //
  // wave-35 B7: the ident grammar now covers the LEADING-HYPHEN forms.
  // css-animations-1 §4 types the keyframes name as <keyframes-name> =
  // <custom-ident> | <string>, and css-values-4 §3.2 defines <custom-ident>
  // on the CSS Syntax 3 §4.3.11 identifier production — which admits an
  // optional '-' before the first name-start code point, and the `--`
  // dashed-ident prefix in full. The pre-wave-35 class `[A-Za-z_]` silently
  // skipped BOTH, so `@keyframes --anim { … }` produced an empty keyframes
  // map and every animation naming it fell out of the sampler for a reason
  // that had nothing to do with time. MEASURED case:
  // css-color/animation/contrast-color-interpolation.html, whose whole test
  // is `@keyframes --anim`. Quoted <string> names stay out of scope (still
  // documented above); a bare digit run is correctly still rejected, since
  // `123` is not a valid identifier.
  const re = /@keyframes\s+((?:--|-?[A-Za-z_])[\w-]*)\s*\{/g;
  let m;
  while ((m = re.exec(css)) !== null) {
    // Find the matching close brace by depth-counting from just after '{'.
    let depth = 1;                                 // we're inside the block's '{'
    let i = re.lastIndex;                          // first char of the block body
    while (i < css.length && depth > 0) {
      if (css[i] === '{') depth++;                 // nested frame block opens
      else if (css[i] === '}') depth--;            // frame block / @keyframes closes
      i++;
    }
    const body = css.slice(re.lastIndex, i - 1);   // body between the outer braces
    re.lastIndex = i;                              // resume scanning after this block
    out[m[1]] = parseKeyframeBody(body);           // last block with a name wins (§3)
  }
  return out;
}

/** Parse ONE @keyframes body into the sorted Frame[] contract described on
 *  parseKeyframes. Internal — exported behaviour is pinned through
 *  parseKeyframes itself. */
function parseKeyframeBody(body) {
  const byOffset = new Map();                      // offset → Frame (merged per §4 cascade)
  let i = 0;                                       // scan cursor
  const n = body.length;                           // body length
  while (i < n) {
    while (i < n && /\s/.test(body[i])) i++;       // skip inter-frame whitespace
    if (i >= n) break;                             // done
    const selStart = i;                            // keyframe-selector list starts here
    while (i < n && body[i] !== '{') i++;          // selector runs to the frame's '{'
    if (i >= n) break;                             // malformed tail — stop, keep what we have
    const keyList = body.slice(selStart, i).trim();// e.g. "0%, 100%" or "from"
    i++;                                           // consume '{'
    const declStart = i;                           // declarations start
    let depth = 1;                                 // inside the frame block
    while (i < n && depth > 0) {                   // find the frame's matching '}'
      if (body[i] === '{') depth++;                // defensive (frames never nest, but stay balanced)
      else if (body[i] === '}') depth--;           // frame closes
      i++;
    }
    const decls = body.slice(declStart, i - 1);    // raw declaration text
    const props = {};                              // animatable declarations of this frame
    let easing;                                    // frame-level animation-timing-function, if any
    for (const decl of decls.split(';')) {         // same ';'-split model as parseCss
      const colon = decl.indexOf(':');             // property/value divider
      if (colon < 0) continue;                     // not a declaration
      const k = decl.slice(0, colon).trim().toLowerCase(); // property name (CSS is ci)
      const v = decl.slice(colon + 1).trim();      // raw value
      if (!k || !v) continue;                      // empty side — skip
      if (/!important\s*$/i.test(v)) continue;     // §4: !important in keyframes is IGNORED
      if (k.startsWith('--')) continue;            // custom props — same skip as parseCss
      if (k === 'animation-timing-function') { easing = v; continue; } // segment tf (§4)
      props[k] = v;                                // last write wins within the frame
    }
    for (const key of keyList.split(',')) {        // fan a comma key list out per key
      const kk = key.trim().toLowerCase();         // selector keyword / percentage
      let off = null;                              // resolved 0..1 offset
      if (kk === 'from') off = 0;                  // §4: from = 0%
      else if (kk === 'to') off = 1;               // §4: to = 100%
      else {
        const pm = /^(\d+(?:\.\d+)?)%$/.exec(kk);  // "<number>%" selector
        if (pm) off = Number(pm[1]) / 100;         // percent → fraction
      }
      // §4: selectors outside 0–100% (or non-percentage junk) make the
      // KEYFRAME invalid — drop just this frame, keep the rest.
      if (off === null || off < 0 || off > 1) continue;
      const existing = byOffset.get(off);          // cascade target at this offset
      if (existing) {                              // §4 same-offset cascade:
        Object.assign(existing.props, props);      //   later declarations win per property
        if (easing !== undefined) existing.easing = easing; // later frame tf wins too
      } else {
        // Fresh frame: copy props (the decl bag is shared across the
        // comma-fanned keys and must not alias between offsets).
        byOffset.set(off, { offset: off, props: { ...props }, ...(easing !== undefined ? { easing } : {}) });
      }
    }
  }
  // Sorted ascending so bracketing-frame lookups are simple linear scans.
  return [...byOffset.values()].sort((a, b) => a.offset - b.offset);
}

// css-easing-1 §2.1: every easing keyword is defined as an equivalent
// cubic-bezier (or steps) — these are the spec's exact control points.
const EASING_KEYWORDS = {
  // linear = cubic-bezier(0,0,1,1); evalTimingFunction shortcuts the
  // identity case so no solve runs for it.
  'linear':      { type: 'bezier', x1: 0,    y1: 0,   x2: 1,    y2: 1 },
  'ease':        { type: 'bezier', x1: 0.25, y1: 0.1, x2: 0.25, y2: 1 },   // §2.1 ease
  'ease-in':     { type: 'bezier', x1: 0.42, y1: 0,   x2: 1,    y2: 1 },   // §2.1 ease-in
  'ease-out':    { type: 'bezier', x1: 0,    y1: 0,   x2: 0.58, y2: 1 },   // §2.1 ease-out
  'ease-in-out': { type: 'bezier', x1: 0.42, y1: 0,   x2: 0.58, y2: 1 },   // §2.1 ease-in-out
  'step-start':  { type: 'steps', n: 1, pos: 'jump-start' },               // §2.1 = steps(1, jump-start)
  'step-end':    { type: 'steps', n: 1, pos: 'jump-end' },                 // §2.1 = steps(1, jump-end)
};

/** Evaluate the y of the css-easing-1 §2.3 cubic Bézier — endpoints pinned
 *  at (0,0)/(1,1), control points (x1,y1)/(x2,y2) — at horizontal input
 *  `x`. x(t) is monotone non-decreasing whenever x1,x2 ∈ [0,1] (the spec's
 *  validity constraint, enforced by parseTimingFunction), so a plain
 *  bisection on t converges; 60 halvings ≈ 2⁻⁶⁰ ≫ the 8-bit color
 *  precision the fixtures need. Exported for the bezier pin tests. */
export function cubicBezierY(x1, y1, x2, y2, x) {
  // One-axis cubic Bézier polynomial with P0=0, P3=1:
  // B(t) = 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³ (standard Bernstein expansion).
  const axis = (t, p1, p2) => 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t;
  let lo = 0;                                      // bisection lower bound on t
  let hi = 1;                                      // bisection upper bound on t
  for (let k = 0; k < 60; k++) {                   // 60 halvings — see doc comment
    const mid = (lo + hi) / 2;                     // candidate parameter
    if (axis(mid, x1, x2) < x) lo = mid;           // x(mid) left of target → go right
    else hi = mid;                                 // else close in from the right
  }
  const t = (lo + hi) / 2;                         // converged parameter
  return axis(t, y1, y2);                          // vertical value at that parameter
}

/** Parse an <easing-function> (css-easing-1 §2) into an evaluatable record:
 *  `{type:'bezier',x1,y1,x2,y2}` or `{type:'steps',n,pos}`. Returns null
 *  for anything unsupported (e.g. the `linear(…)` function) — the sampler
 *  then refuses to sample rather than mis-easing. Exported for tests. */
export function parseTimingFunction(raw) {
  const s = String(raw).trim().toLowerCase();      // easing keywords are case-insensitive
  if (EASING_KEYWORDS[s]) return EASING_KEYWORDS[s]; // keyword → its spec-defined equivalent
  // cubic-bezier(x1, y1, x2, y2) — four <number>s (§2.3 grammar).
  let m = /^cubic-bezier\(\s*([^,\s]+)\s*,\s*([^,\s]+)\s*,\s*([^,\s]+)\s*,\s*([^)\s]+)\s*\)$/.exec(s);
  if (m) {
    const [x1, y1, x2, y2] = m.slice(1).map(Number); // numeric control points
    if (![x1, y1, x2, y2].every(Number.isFinite)) return null; // non-numeric → invalid
    // §2.3: x1/x2 MUST be in [0,1] or the function is invalid (y's are
    // unrestricted — overshoot beziers like (0,9,1,9) are legal).
    if (x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) return null;
    return { type: 'bezier', x1, y1, x2, y2 };
  }
  // steps(n[, position]) — §2.4 grammar; position defaults to end.
  m = /^steps\(\s*(\d+)\s*(?:,\s*([a-z-]+)\s*)?\)$/.exec(s);
  if (m) {
    const n = Number(m[1]);                        // step count
    // §2.4: start/end are aliases of jump-start/jump-end.
    const POS = { 'start': 'jump-start', 'end': 'jump-end', 'jump-start': 'jump-start',
                  'jump-end': 'jump-end', 'jump-none': 'jump-none', 'jump-both': 'jump-both' };
    const pos = POS[m[2] ?? 'end'];                // resolved jump keyword
    // §2.4 validity: n ≥ 1, and jump-none needs n ≥ 2 (n-1 jumps).
    if (!pos || n < 1 || (pos === 'jump-none' && n < 2)) return null;
    return { type: 'steps', n, pos };
  }
  return null;                                     // unsupported easing — caller refuses
}

/** Evaluate a parsed timing function at input progress `p`. Callers only
 *  pass p strictly inside (0,1) (exact-frame hits shortcut before easing),
 *  so the css-easing-1 §3.9.2 before-flag edge cases never arise; the
 *  steps output is still clamped to [0,1] defensively. Exported for the
 *  pinned-point tests. */
export function evalTimingFunction(tf, p) {
  if (tf.type === 'bezier') {
    // Identity control points (linear keyword) — skip the solve, exact.
    if (tf.x1 === tf.y1 && tf.x2 === tf.y2) return p;
    return cubicBezierY(tf.x1, tf.y1, tf.x2, tf.y2, p); // general bezier eval
  }
  // steps() per css-easing-1 §3.9.2: current step = ⌊p·n⌋ …
  let step = Math.floor(p * tf.n);
  // … +1 when the position starts with a rise (jump-start / jump-both).
  if (tf.pos === 'jump-start' || tf.pos === 'jump-both') step += 1;
  // Divisor = number of jumps: n for start/end, n-1 for jump-none, n+1 for
  // jump-both (§2.4's jump-count table).
  const jumps = tf.pos === 'jump-none' ? tf.n - 1 : tf.pos === 'jump-both' ? tf.n + 1 : tf.n;
  return Math.min(1, Math.max(0, step / jumps));   // clamp — outputs are progress values
}

// The CSS2/css-color-4 §6.1 basic named colors (+ orange, the one HTML4
// addition WPT reftests actually use, + transparent = rgba(0,0,0,0) per
// css-color-4 §7.4). Deliberately tiny: the sampler only needs the names
// the animation corpus interpolates; unknown names simply refuse to lerp.
const NAMED_SRGB = {
  transparent: [0, 0, 0, 0],       black: [0, 0, 0, 1],       silver: [192, 192, 192, 1],
  gray: [128, 128, 128, 1],        grey: [128, 128, 128, 1],  white: [255, 255, 255, 1],
  maroon: [128, 0, 0, 1],          red: [255, 0, 0, 1],       purple: [128, 0, 128, 1],
  fuchsia: [255, 0, 255, 1],       green: [0, 128, 0, 1],     lime: [0, 255, 0, 1],
  olive: [128, 128, 0, 1],         yellow: [255, 255, 0, 1],  navy: [0, 0, 128, 1],
  blue: [0, 0, 255, 1],            teal: [0, 128, 128, 1],    aqua: [0, 255, 255, 1],
  orange: [255, 165, 0, 1],
};

/** Parse a color the animation corpus interpolates — rgb()/rgba() (legacy
 *  comma AND modern space/slash syntax, 3 or 4 components, css-color-4
 *  §4.1), #hex (3/4/6/8 digits), or a NAMED_SRGB keyword — into
 *  `{r,g,b,a}` (channels 0–255, alpha 0–1). Returns null for every other
 *  color syntax (hsl/oklch/color() …) so the sampler refuses instead of
 *  mis-lerping. Exported for tests. */
export function parseSrgbColor(raw) {
  const s = String(raw).trim().toLowerCase();      // color syntax is case-insensitive
  if (NAMED_SRGB[s]) {                             // keyword hit
    const [r, g, b, a] = NAMED_SRGB[s];            // unpack the table row
    return { r, g, b, a };
  }
  let m = /^#([0-9a-f]{3,8})$/.exec(s);            // hex forms (css-color-4 §5)
  if (m) {
    const h = m[1];                                // digit run
    // 3/4-digit shorthand doubles each digit (§5); 5/7 digit runs are invalid.
    if (h.length === 3 || h.length === 4) {
      const d = (i) => parseInt(h[i] + h[i], 16);  // expand one shorthand digit
      return { r: d(0), g: d(1), b: d(2), a: h.length === 4 ? d(3) / 255 : 1 };
    }
    if (h.length === 6 || h.length === 8) {
      const d = (i) => parseInt(h.slice(i, i + 2), 16); // one full byte pair
      return { r: d(0), g: d(2), b: d(4), a: h.length === 8 ? d(6) / 255 : 1 };
    }
    return null;                                   // 5- or 7-digit — invalid hex
  }
  m = /^rgba?\(([^)]*)\)$/.exec(s);                // rgb()/rgba() payload
  if (m) {
    // Legacy commas, modern spaces, and the modern `/ alpha` divider all
    // normalize to plain token separation (css-color-4 §4.1 both grammars).
    const parts = m[1].split(/[,\s/]+/).filter(Boolean);
    if (parts.length < 3 || parts.length > 4) return null; // must be rgb or rgb+alpha
    // A channel is a <number> or <percentage> (100% = 255, §4.1).
    const chan = (v) => v.endsWith('%') ? Number(v.slice(0, -1)) * 2.55 : Number(v);
    const r = chan(parts[0]), g = chan(parts[1]), b = chan(parts[2]); // three channels
    // Alpha is a 0–1 <number> or a <percentage> (§4.1); absent = opaque.
    const a = parts.length === 4
      ? (parts[3].endsWith('%') ? Number(parts[3].slice(0, -1)) / 100 : Number(parts[3]))
      : 1;
    if (![r, g, b, a].every(Number.isFinite)) return null; // any junk token → refuse
    return { r, g, b, a };
  }
  // wave-35 B7: contrast-color(<color>) — css-color-5 §5 "Selecting the Most
  // Contrasting Color". The function resolves, at computed-value time, to
  // whichever of BLACK or WHITE has the greater WCAG 2.1 contrast ratio
  // against its argument; it carries no runtime state, so resolving it here
  // is a static substitution, not an animation guess. MEASURED case:
  // css-color/animation/contrast-color-interpolation.html interpolates
  // `contrast-color(white)` → `lime` and its ref paints plain `green`
  // (rgb(0,128,0)) — i.e. the midpoint of BLACK → lime, confirming that
  // contrast-color(white) must resolve to black.
  m = /^contrast-color\(\s*(.+?)\s*\)$/.exec(s);
  if (m) {
    const base = parseSrgbColor(m[1]);             // the argument must itself be sRGB-parsable
    if (!base) return null;                        // unsupported argument colour → refuse
    // WCAG 2.1 relative luminance: linearize each 0–1 sRGB channel through
    // the piecewise transfer function, then weight 0.2126/0.7152/0.0722.
    const lin = (v8) => {
      const v = v8 / 255;                          // 8-bit channel → 0–1
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    };
    const L = 0.2126 * lin(base.r) + 0.7152 * lin(base.g) + 0.0722 * lin(base.b);
    // WCAG contrast ratio (Llight+0.05)/(Ldark+0.05); white L=1, black L=0.
    const vsWhite = 1.05 / (L + 0.05);             // ratio if we pick white
    const vsBlack = (L + 0.05) / 0.05;             // ratio if we pick black
    // css-color-5 §5: ties go to the FIRST of the implicit white/black pair
    // the UA considers; the corpus never hits the exact tie, and picking
    // black on a tie matches Chromium's shipped behaviour.
    return vsBlack >= vsWhite ? { r: 0, g: 0, b: 0, a: 1 } : { r: 255, g: 255, b: 255, a: 1 };
  }
  return null;                                     // unsupported color syntax
}

/** Lerp two parsed sRGB colors at (possibly overshooting) progress `t` and
 *  serialize back to CSS. Interpolation is PREMULTIPLIED-alpha in straight
 *  sRGB — css-color-4 §13 mandates premultiplication, and its legacy
 *  caveat (interpolation of legacy rgb()/rgba() forms happens in gamma-
 *  encoded sRGB, which is exactly Chromium's behavior on these WPT
 *  time-stable tests) fixes the space. Overshoot easings (y outside
 *  [0,1], e.g. cubic-bezier(0,9,1,9)) are legal, so alpha clamps to [0,1]
 *  and channels to [0,255] AFTER the lerp, matching rgb() serialization
 *  clamping. Exported for tests. */
export function lerpSrgbColors(c1, c2, t) {
  // Alpha lerps straight, then clamps (overshoot protection).
  const a = Math.min(1, Math.max(0, c1.a + (c2.a - c1.a) * t));
  // Channel lerp in premultiplied space: pm = c·α lerped, then un-premultiplied.
  const ch = (k) => {
    if (a === 0) return 0;                         // fully transparent → channels defined as 0
    const pm = c1[k] * c1.a + (c2[k] * c2.a - c1[k] * c1.a) * t; // premultiplied lerp
    return Math.min(255, Math.max(0, Math.round(pm / a))); // un-premultiply, clamp, 8-bit round
  };
  const r = ch('r'), g = ch('g'), b = ch('b');     // the three channels
  // Serialize like the browser-ref fixtures do: `rgb(R, G, B)` when opaque,
  // `rgba(R, G, B, A)` otherwise (alpha to ≤4 decimals, float noise cut).
  return a === 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${Math.round(a * 10000) / 10000})`;
}

/** Interpolate ONE property value pair at eased progress `t`. Supported
 *  families (the documented sampler boundary): equal strings (static
 *  segment), sRGB-parsable colors, and same-unit scalar numerics (numeric
 *  lerp for lengths/numbers per css-values). Returns the CSS string or
 *  null when the pair is out of scope — the caller then aborts the WHOLE
 *  sample so no fixture ever carries a half-baked animation. Exported for
 *  tests. */
export function lerpCssValue(aRaw, bRaw, t) {
  const av = String(aRaw).trim();                  // normalized endpoints
  const bv = String(bRaw).trim();
  if (av === bv) return av;                        // static segment — value is constant
  const c1 = parseSrgbColor(av);                   // try the color family first
  const c2 = parseSrgbColor(bv);
  if (c1 && c2) return lerpSrgbColors(c1, c2, t);  // both colors → sRGB lerp (§13 caveat above)
  // Scalar numeric family: <number> with one optional unit token.
  const NUM = /^([+-]?(?:\d+\.?\d*|\.\d+))([a-z%]*)$/i;
  const m1 = NUM.exec(av);                         // parse both endpoints
  const m2 = NUM.exec(bv);
  if (m1 && m2 && m1[2].toLowerCase() === m2[2].toLowerCase()) { // units must MATCH (no calc-mixing)
    const v = Number(m1[1]) + (Number(m2[1]) - Number(m1[1])) * t; // plain numeric lerp
    // 4-decimal round strips float noise; unit rides through verbatim.
    return `${Math.round(v * 10000) / 10000}${m1[2]}`;
  }
  // wave-35 B7 — COMPOSITE family: a value made of several whitespace-
  // separated components interpolates COMPONENT-WISE. css-values-4 §7
  // ("Combining Values: Interpolation…") defines interpolation of a
  // multi-component value as the componentwise interpolation of its parts,
  // and css-backgrounds-3 §5.4 types `box-shadow` exactly that way (each
  // shadow's colour and its <length>s interpolate independently). MEASURED
  // case: css-color/animation/contrast-color-interpolation.html interpolates
  // `contrast-color(white) 100px 0px` → `lime 100px 0px`, which no scalar
  // family can express.
  //
  // Deliberately bounded, in the sampler's house style:
  //   - both sides must split (at paren depth 0, so `rgb(0, 128, 0)` stays
  //     ONE component) into the SAME number of components — a differing
  //     count means a component was added/omitted and the spec's fallback is
  //     the by-computed-value/discrete path, which we refuse rather than
  //     guess;
  //   - at least TWO components, so this never shadows the scalar branch;
  //   - every component pair must itself be interpolable by this function —
  //     one refusal refuses the whole value (no half-interpolated composites);
  //   - comma-separated LISTS (`box-shadow: a, b`) are out of scope: list
  //     interpolation needs per-item padding rules we do not model. The
  //     recursion below only ever sees whitespace components because a comma
  //     survives inside a component and then fails its own pair check.
  const p1 = splitTopLevel(av, null);              // depth-0 whitespace components
  const p2 = splitTopLevel(bv, null);
  if (p1.length >= 2 && p1.length === p2.length) {
    const parts = [];                              // interpolated components, in order
    for (let i = 0; i < p1.length; i++) {
      const c = lerpCssValue(p1[i], p2[i], t);     // recurse into the leaf families
      if (c === null) return null;                 // one uninterpolable component → refuse all
      parts.push(c);
    }
    return parts.join(' ');                        // re-serialize with single spaces
  }
  return null;                                     // out-of-scope value family — refuse
}

/**
 * Resolve a component's animation declaration (shorthand + longhands) to a
 * single-animation spec `{ name, durationMs, delayMs, easing, iterations,
 * direction }` or null when parsing is out of the sampler's scope. The
 * shorthand grammar (css-animations-1 §7 <single-animation>) is order-free
 * except: the FIRST <time> is the duration and the SECOND is the delay.
 * Keyword slots are claimed greedily in spec order (easing → iteration →
 * direction → fill → play-state → name), which matches how browsers
 * disambiguate `none`/`forwards`-style keywords vs the keyframes name.
 * Longhands override their shorthand slot unconditionally — true cascade
 * order between `animation` and a later longhand isn't reconstructable
 * from the merged props bag, and the corpus always writes longhands after
 * the shorthand (documented approximation). Exported for tests.
 */
export function parseAnimationDecl(props) {
  // Case-insensitive property lookup — parseCss preserves author case but
  // CSS property names compare case-insensitively.
  const getProp = (name) => {
    for (const k of Object.keys(props)) if (k.toLowerCase() === name) return props[k];
    return undefined;                              // longhand absent
  };
  // Spec defaults per css-animations-1 §7 initial values.
  // wave-35 B7 adds `fill` and `paused`: the wave-13 sampler could discard
  // both (a strictly-negative delay always lands in the ACTIVE phase, where
  // fill is irrelevant, and a paused animation samples identically at t=0),
  // but the stability-window path evaluates the effect at two DIFFERENT
  // times, so the phase arithmetic in iterationProgressAt needs them.
  // Initial values: animation-fill-mode: none, animation-play-state: running
  // (css-animations-1 §5, §6).
  const spec = { name: null, durationMs: 0, delayMs: 0, easing: 'ease',
                 iterations: 1, direction: 'normal', fill: 'none', paused: false };
  let sawAny = false;                              // did ANY animation declaration exist?
  const sh = getProp('animation');                 // the shorthand, if present
  if (sh !== undefined) {
    sawAny = true;                                 // shorthand counts as a declaration
    // Comma at depth 0 = a multi-animation list — composite ordering is
    // out of the bounded scope; refuse.
    const list = splitTopLevel(sh, ',');
    if (list.length !== 1) return null;
    let times = 0;                                 // how many <time> tokens consumed (1st=duration, 2nd=delay)
    // One-shot flags per keyword slot so a second easing/direction/… token
    // falls through to the name slot exactly like the browser grammar.
    let gotEasing = false, gotIter = false, gotDir = false, gotFill = false, gotPlay = false, gotName = false;
    for (const tok of splitTopLevel(list[0], null)) { // whitespace tokens, parens kept intact
      const ms = parseTimeMs(tok);                 // is it a <time>?
      if (ms !== null && times < 2) {              // §7: first time=duration, second=delay
        if (times === 0) spec.durationMs = ms; else spec.delayMs = ms;
        times++;
        continue;
      }
      const low = tok.toLowerCase();               // keyword compare is ci
      // <easing-function>: a spec keyword or an easing function token.
      if (!gotEasing && (EASING_KEYWORDS[low] || /^(?:cubic-bezier|steps|linear)\(/.test(low))) {
        spec.easing = tok; gotEasing = true; continue;
      }
      // <single-animation-iteration-count>: `infinite` or a plain number.
      if (!gotIter && (low === 'infinite' || /^\d+\.?\d*$/.test(low))) {
        spec.iterations = low === 'infinite' ? Infinity : Number(low); gotIter = true; continue;
      }
      // <single-animation-direction> keywords.
      if (!gotDir && ['normal', 'reverse', 'alternate', 'alternate-reverse'].includes(low)) {
        spec.direction = low; gotDir = true; continue;
      }
      // <single-animation-fill-mode>: irrelevant inside the active phase
      // (css-animations-1 §5.4), which is the only place the frozen
      // negative-delay path samples — but wave-35 B7's stability window can
      // land a window end in the BEFORE or AFTER phase, where fill decides
      // whether an effect value exists at all, so the value is now recorded.
      if (!gotFill && ['none', 'forwards', 'backwards', 'both'].includes(low)) {
        spec.fill = low; gotFill = true; continue;
      }
      // <single-animation-play-state>: paused samples identically at t=0
      // (the WPT engineering makes the value time-stable either way), so
      // like fill-mode it's consumed but not recorded.
      // wave-27 A-RC3 — why that is SOUND, not lucky: css-animations-1 §4.2
      // says an animation that is `paused` from the start has its hold time
      // fixed at the animation's start, and Web-Animations §4.8.3.1 puts
      // that start at local time 0 ⇒ iteration progress (-delay)/duration —
      // the EXACT progress this sampler computes for the running case. A
      // paused animation therefore needs no separate branch; what it does
      // need is that non-animatable keyframe declarations never reach the
      // sample at all (KEYFRAME_NON_ANIMATABLE, contain-animation-001).
      // wave-35 B7 records the flag rather than dropping it, because the
      // stability window evaluates two DISTINCT times: `paused` collapses
      // both to local time 0 (the hold-time argument above), which is what
      // makes a paused animation provably static at any delay.
      if (!gotPlay && ['running', 'paused'].includes(low)) {
        spec.paused = low === 'paused'; gotPlay = true; continue;
      }
      // Anything else is the <keyframes-name> — claim once.
      if (!gotName) { spec.name = tok; gotName = true; continue; }
      return null;                                 // second unclassifiable token — refuse to guess
    }
  }
  // Longhand overrides. Each returns early with null on multi-animation
  // comma lists or unparseable values (no silent fallthrough to defaults).
  const single = (v) => {                          // enforce a single-animation value
    const parts = splitTopLevel(v, ',');
    return parts.length === 1 ? parts[0] : null;
  };
  const lhName = getProp('animation-name');        // animation-name longhand
  if (lhName !== undefined) {
    sawAny = true;
    const v = single(lhName); if (v === null) return null;
    spec.name = v;                                 // may be 'none' — rejected below
  }
  const lhDur = getProp('animation-duration');     // animation-duration longhand
  if (lhDur !== undefined) {
    sawAny = true;
    const v = single(lhDur); if (v === null) return null;
    const ms = parseTimeMs(v); if (ms === null) return null; // e.g. `auto` — refuse
    spec.durationMs = ms;
  }
  const lhDelay = getProp('animation-delay');      // animation-delay longhand
  if (lhDelay !== undefined) {
    sawAny = true;
    const v = single(lhDelay); if (v === null) return null;
    const ms = parseTimeMs(v); if (ms === null) return null;
    spec.delayMs = ms;
  }
  const lhTf = getProp('animation-timing-function'); // element-level easing longhand
  if (lhTf !== undefined) {
    sawAny = true;
    const v = single(lhTf); if (v === null) return null;
    spec.easing = v;                               // validated by parseTimingFunction later
  }
  const lhIter = getProp('animation-iteration-count'); // iteration-count longhand
  if (lhIter !== undefined) {
    sawAny = true;
    const v = single(lhIter); if (v === null) return null;
    if (v.toLowerCase() === 'infinite') spec.iterations = Infinity;
    else if (/^\d+\.?\d*$/.test(v)) spec.iterations = Number(v);
    else return null;                              // junk count — refuse
  }
  const lhDir = getProp('animation-direction');    // direction longhand
  if (lhDir !== undefined) {
    sawAny = true;
    const v = single(lhDir); if (v === null) return null;
    if (!['normal', 'reverse', 'alternate', 'alternate-reverse'].includes(v.toLowerCase())) return null;
    spec.direction = v.toLowerCase();
  }
  // fill-mode / play-state longhands. wave-13 consumed these for the sawAny
  // signal only; wave-35 B7 records their values for the same reason the
  // shorthand slots now do (phase arithmetic + the paused hold-time
  // collapse). Junk values refuse rather than silently defaulting.
  const lhFill = getProp('animation-fill-mode');   // fill-mode longhand
  if (lhFill !== undefined) {
    sawAny = true;
    const v = single(lhFill); if (v === null) return null;
    if (!['none', 'forwards', 'backwards', 'both'].includes(v.toLowerCase())) return null;
    spec.fill = v.toLowerCase();
  }
  const lhPlay = getProp('animation-play-state');  // play-state longhand
  if (lhPlay !== undefined) {
    sawAny = true;
    const v = single(lhPlay); if (v === null) return null;
    if (!['running', 'paused'].includes(v.toLowerCase())) return null;
    spec.paused = v.toLowerCase() === 'paused';
  }
  if (!sawAny) return null;                        // no animation declarations at all
  return spec;
}

/**
 * Iteration progress of a single-animation `anim` at wall time `tMs`
 * measured from the document's animation timeline origin, or null when no
 * effect value exists at that instant (the UNDERLYING value applies, which
 * static extraction cannot know). Web Animations §4.8.4 phase boundaries +
 * css-animations-1 §5 (animation-fill-mode) / §6 (animation-play-state).
 * Direction is assumed `normal` — the caller rejects everything else before
 * getting here, so no directed-progress mapping is needed. Exported so the
 * phase arithmetic can be pinned directly. wave-35 B7.
 */
export function iterationProgressAt(anim, tMs) {
  // css-animations-1 §6 + Web Animations §4.8.3.1: an animation that is
  // `paused` from the start never advances — its hold time is fixed at the
  // animation's start, i.e. local time 0 — so EVERY wall time samples the
  // same effect value. Collapsing t to 0 is what makes a paused animation
  // provably static regardless of how wide the capture window is.
  const t = anim.paused ? 0 : tMs;
  // Active duration = iteration duration × iteration count (Web Animations
  // §4.8.2); `infinite` never ends, so the after phase is unreachable.
  const activeDur = anim.iterations === Infinity ? Infinity : anim.durationMs * anim.iterations;
  if (t < anim.delayMs) {
    // BEFORE phase (§4.8.4.1). Only a backwards-filling animation produces
    // an effect value here, and it is the first iteration's start value.
    return (anim.fill === 'backwards' || anim.fill === 'both') ? 0 : null;
  }
  const local = t - anim.delayMs;                  // local time inside the active interval
  if (Number.isFinite(activeDur) && local >= activeDur) {
    // AFTER phase (§4.8.4.3). Only a forwards-filling animation produces an
    // effect value, and (direction normal) it is the final progress 1.
    return (anim.fill === 'forwards' || anim.fill === 'both') ? 1 : null;
  }
  const p = local / anim.durationMs;               // overall progress in iterations
  // Iterations beyond the first composite differently per direction/count;
  // the sampler's bounded scope stops at iteration 0 (same rule the frozen
  // negative-delay path enforced via `progress < 1`).
  if (p >= 1) return null;
  return p;
}

/**
 * Evaluate every animatable keyframed property at iteration `progress` and
 * return `{prop: cssValue}`, or null when the sample is out of scope (no
 * animatable declarations, a missing bracketing keyframe, an unsupported
 * easing, or an uninterpolable value pair). Split out of
 * sampleKeyframesAnimation by wave-35 B7 so the stability window can call it
 * twice — once per window end — without duplicating the §4 keyframe
 * selection. Behaviour on the frozen negative-delay path is unchanged.
 */
function sampleFramesAt(frames, progress, elementTf) {
  const baked = {};                                // sampled property → CSS value
  // Union of properties any frame declares = the animated property set.
  // wave-27 A-RC3: MINUS the properties css-animations-1 §4 says a keyframe
  // may not carry ("Properties that aren't animatable are ignored in these
  // rules"). See KEYFRAME_NON_ANIMATABLE for the list + the measured case.
  const animatable = new Set();
  for (const f of frames) {
    for (const k of Object.keys(f.props)) {
      if (KEYFRAME_NON_ANIMATABLE.has(k)) continue; // §4: ignored, not sampled
      animatable.add(k);
    }
  }
  // Empty here now covers TWO honest cases, both meaning "this @keyframes
  // block cannot change the render": an all-invalid body, and a body whose
  // every declaration is non-animatable (contain-animation-001). Returning
  // null leaves the STATIC cascade untouched — which is exactly what the
  // browser paints.
  if (animatable.size === 0) return null;          // nothing animatable to sample
  for (const prop of animatable) {
    // css-animations-1 §4 keyframe selection is PER PROPERTY: only frames
    // declaring this property participate in its segment lookup.
    const pf = frames.filter((f) => prop in f.props);
    // Web Animations §4.10.2 (Calculating the interval endpoints): endpoint
    // A is the property-specific keyframe with the largest offset ≤ progress,
    // endpoint B the one with the smallest offset STRICTLY GREATER than
    // progress. wave-35 B7 fixed B: it used to be "smallest offset ≥
    // progress", which collapses A and B whenever progress lands exactly on
    // a keyframe and therefore skipped the interval's timing function
    // entirely. That is invisible for every bezier easing (they all map
    // local 0 → 0) but WRONG for a step function that rises at its start:
    // css-color/animation/contrast-color-interpolation.html samples at
    // progress 0 under `steps(2, start)`, whose output at input 0 is 0.5
    // (css-easing-1 §3.9.2), so the correct value is the interval MIDPOINT,
    // not the `from` keyframe. The shortcut below keeps the frozen path
    // byte-identical for the bezier case.
    let prev = null, next = null;                  // interval endpoints A and B
    for (const f of pf) {                          // frames are offset-sorted ascending
      if (f.offset <= progress + KF_EPS) prev = f; // keeps advancing → last one ≤ progress
      if (next === null && f.offset > progress + KF_EPS) next = f; // first one strictly after
    }
    // Missing endpoint A ⇒ the §4 fallback is the element's UNDERLYING
    // value, which static extraction can't know — abort the whole sample.
    if (!prev) return null;
    if (!next) {
      // No keyframe after `progress`. Spec-correct ONLY when the animation
      // has reached its final 100% keyframe (progress ≥ 1 under a forwards
      // fill, or a keyframe list that ends exactly at progress): the value
      // is that keyframe's. A list that stops SHORT of 100% leaves the tail
      // to the underlying value (§4) — unknowable, so refuse.
      if (prev.offset >= 1 - KF_EPS || progress >= 1 - KF_EPS) {
        baked[prop] = prev.props[prop];
        continue;
      }
      return null;
    }
    // Segment-local progress in [0,1] between the interval endpoints.
    const local = (progress - prev.offset) / (next.offset - prev.offset);
    // css-animations-1 "Timing functions for keyframes": a tf declared ON
    // the previous keyframe governs this segment; else the element's.
    const tf = prev.easing !== undefined ? parseTimingFunction(prev.easing) : elementTf;
    if (!tf) return null;                          // unsupported easing — refuse, don't mis-ease
    const eased = evalTimingFunction(tf, local);   // eased (possibly overshooting) progress
    // Spelling-preserving shortcut: when the segment sits exactly on
    // endpoint A and the governing timing function maps 0 → 0 (true of every
    // <cubic-bezier>, whose P0 is pinned at (0,0) by css-easing-1 §2.3, and
    // of steps() with jump-end/jump-none), the effect value IS endpoint A's
    // declared value. Taking it verbatim rather than through the lerp keeps
    // the author's spelling (`#00c800` stays `#00c800` instead of
    // re-serializing as `rgb(0, 200, 0)`), which is what makes this refactor
    // byte-identical on the frozen wave-13 path. The comparison uses KF_EPS,
    // not `=== 0`: cubicBezierY bisects, so it returns ~1e-19 rather than a
    // hard zero at x=0 — mathematically 0, numerically not.
    if (local <= KF_EPS && eased <= KF_EPS) {
      baked[prop] = prev.props[prop];
      continue;
    }
    const v = lerpCssValue(prev.props[prop], next.props[prop], eased); // interpolate the pair
    if (v === null) return null;                   // uninterpolable value family — abort whole sample
    baked[prop] = v;                               // record the sampled value
  }
  return baked;
}

/**
 * THE SAMPLER. Given a component's merged props bag and the stylesheet's
 * parsed @keyframes map, decide whether the declared animation renders a
 * TIME-STABLE value at screenshot time and, if so, statically compute every
 * keyframed property at that value.
 *
 * Two admission paths, both documented in the section banners above:
 *   - wave-13 (frozen): a strictly NEGATIVE delay places local time 0 at
 *     `progress = -delay / duration` strictly inside the first iteration.
 *   - wave-35 B7: a non-negative delay, admitted only when the bake is
 *     byte-identical at both ends of the measured CAPTURE_WINDOW_MS.
 *
 * Returns `{ baked: {prop: cssValue}, dropped: [animPropKeys] }` on
 * success, or null when ANY scope-boundary rule fires — null means "extract
 * verbatim exactly as before"; partial bakes never happen (one
 * uninterpolable property aborts the whole sample, keeping the fixture
 * honest). Exported for tests.
 */
export function sampleKeyframesAnimation(props, keyframesMap) {
  if (!keyframesMap) return null;                  // no @keyframes were parsed at all
  // Fast path + the drop list: every animation-* key present (original
  // spelling preserved for deletion by the caller).
  const animKeys = Object.keys(props).filter((k) => ANIMATION_LONGHANDS.has(k.toLowerCase()));
  if (animKeys.length === 0) return null;          // nothing animation-related on this component
  const anim = parseAnimationDecl(props);          // resolve shorthand+longhands
  if (!anim) return null;                          // unparseable / multi-animation — boundary
  if (!anim.name || anim.name.toLowerCase() === 'none') return null; // no keyframes to run
  const frames = keyframesMap[anim.name];          // the referenced @keyframes block
  if (!frames || frames.length === 0) return null; // undeclared / empty name — boundary
  if (!(anim.durationMs > 0)) return null;         // zero/negative duration never advances
  if (anim.direction !== 'normal') return null;    // directed-progress mapping — boundary
  const elementTf = parseTimingFunction(anim.easing); // element-level easing (may be null)

  if (anim.delayMs < 0) {
    // ── FROZEN wave-13 path — deliberately left byte-for-byte intact ──────
    // Web-Animations §4.8.3.1: at local time 0, iteration progress is
    // (-delay)/duration for the first iteration.
    const progress = -anim.delayMs / anim.durationMs;
    // Must land strictly INSIDE the active phase's first iteration: at 0/1
    // or beyond, the rendered value depends on fill-mode / iteration
    // compositing (css-animations-1 §5.4) — out of scope.
    if (!(progress > 0 && progress < 1)) return null;
    if (!(progress < anim.iterations)) return null; // count must cover the sample point
    const baked = sampleFramesAt(frames, progress, elementTf);
    return baked ? { baked, dropped: animKeys } : null;
  }

  // ── wave-35 B7 path: non-negative delay + a two-point stability proof ───
  // Evaluate the effect at both ends of the capture window. Both ends must
  // HAVE an effect value (no unfilled before/after phase) and the two bakes
  // must serialize identically — see the STABILITY WINDOW banner for why
  // byte identity is the right criterion and where the window comes from.
  const pStart = iterationProgressAt(anim, 0);                  // timeline origin
  const pEnd   = iterationProgressAt(anim, CAPTURE_WINDOW_MS);  // latest plausible screenshot
  if (pStart === null || pEnd === null) return null;            // underlying value — unknowable
  const bakedStart = sampleFramesAt(frames, pStart, elementTf);
  if (!bakedStart) return null;                                 // out of scope at t=0
  const bakedEnd = sampleFramesAt(frames, pEnd, elementTf);
  if (!bakedEnd) return null;                                   // out of scope at the window edge
  const keys = Object.keys(bakedStart);
  // Same property set AND same serialized value for every one of them.
  if (keys.length !== Object.keys(bakedEnd).length) return null;
  for (const k of keys) if (bakedStart[k] !== bakedEnd[k]) return null; // drifts → genuinely dynamic
  return { baked: bakedStart, dropped: animKeys }; // proved static — bake it
}

/** In-place integration shim for buildComponents: run the sampler on one
 *  props bag; on success delete the animation-* declarations (nothing
 *  downstream can execute them) and merge the baked values in. Returns
 *  true iff a sample was baked so the caller can append the
 *  'sampled-animation' lossy note — the LOUD marker this change rides. */
function bakeSampledAnimation(props, keyframesMap) {
  const s = sampleKeyframesAnimation(props, keyframesMap); // scope-check + sample
  if (!s) return false;                            // out of scope — props untouched (verbatim path)
  for (const k of s.dropped) delete props[k];      // drop the un-executable animation-* decls
  Object.assign(props, s.baked);                   // bake the statically sampled values
  return true;                                     // tell the caller to add the lossy note
}

// ── Support-asset inlining (wave-8) ──────────────────────────────────────────
//
// WPT tests routinely paint via `url(../support/x.png)`. The extractor used
// to pass those relative paths into the IR VERBATIM, and every platform
// harness 404'd on them (css-break background-image-000/001/002 scored
// 0.14–0.38 on ALL platforms — a pure asset-delivery gap, not a renderer
// divergence). We now resolve url() references against the test file's
// directory under tools/wpt/ and inline small raster assets as data URIs.
//
// Encoding choice — PERCENT-ENCODED bytes, NOT base64: the converter
// lowercases CSS values on their way into the IR, which corrupts base64
// payloads (uppercase letters are data). Percent-encoding survives —
// RFC 3986 §2.1 makes the hex digits of a %xx escape case-INsensitive on
// decode, and we encode EVERY byte (no literal alphanumerics that a
// lowercase pass could flip). The `data:image/png,` prefix is lowercase
// already. Size cost is 3x raw (cat.png: 1,883 B → ~5.6 KB of fixture
// text), acceptable under the 8 KB raw-size cap below.
//
// Oversize / unresolvable / non-raster references stay verbatim and mark
// the owning component `_lossy` with reason 'requires-bundled-asset' (the
// same tag wpt-not-applicable.mjs Rule 20 uses), so the dashboard's lossy
// lane sees the honest delivery gap instead of a mystery 404.

// Raster formats we inline. SVG (text), fonts, CSS, etc. are excluded —
// the confirmed wave-8 scope is raster assets only; anything else stays a
// 'requires-bundled-asset' lossy marker.
const RASTER_MIME = {
  png:  'image/png',
  gif:  'image/gif',
  jpg:  'image/jpeg',
  jpeg: 'image/jpeg',
  webp: 'image/webp',
  bmp:  'image/bmp',
  ico:  'image/x-icon',
};

// Inline cap: raw asset size must be strictly < 8 KB. Bigger assets would
// bloat fixtures ~3x that in JSON text and slow every decode path.
export const MAX_INLINE_ASSET_BYTES = 8192;

/** Percent-encode EVERY byte of a Buffer as lowercase %xx escapes.
 *  Encoding all bytes (not just the reserved set) is deliberate: literal
 *  alphanumeric bytes would be corrupted by the converter's value
 *  lowercasing ('A' 0x41 → 'a' 0x61), while %xx hex digits decode
 *  case-insensitively per RFC 3986 §2.1. Exported for unit tests. */
export function percentEncodeBytes(buf) {
  let out = '';
  for (const b of buf) out += '%' + b.toString(16).padStart(2, '0');
  return out;
}

// url() token matcher: unquoted / single- / double-quoted payloads, per
// CSS Syntax 3 §4.3.5-6. Global flag — callers iterate matchAll-style.
const URL_TOKEN_RE = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)"'\s]+))\s*\)/gi;

/** Should a url() payload be left alone entirely (neither inlined nor
 *  lossy-marked)? data: is already inline; http(s):// + protocol-relative
 *  are the bucketer's cross-origin/remote-resource domain (Rule 37 /
 *  bucket-C), `#frag` is a same-document SVG reference, and `{{…}}` is a
 *  WPT sub-template token (Rule 36) — all out of the asset-inliner's
 *  scope. */
function urlPayloadOutOfScope(p) {
  return /^data:/i.test(p) || /^https?:\/\//i.test(p) || p.startsWith('//')
      || p.startsWith('#') || p.includes('{{');
}

/**
 * Rewrite every inlinable url() reference inside ONE CSS value string.
 * Returns `{ value, inlined, unresolved }`: `value` is the (possibly
 * rewritten) string; `inlined`/`unresolved` count how many url() tokens
 * were converted to data URIs vs left as un-deliverable file references
 * (the latter drive the 'requires-bundled-asset' lossy marker upstream).
 * Async because the asset bytes are read from disk.
 */
export async function inlineUrlsInValue(value, baseDir) {
  let inlined = 0;
  let unresolved = 0;
  // Collect matches first (regex is stateful/global), then rebuild the
  // string with replacements — async work inside a .replace callback
  // isn't possible, so we do a manual splice pass.
  const matches = [...value.matchAll(URL_TOKEN_RE)];
  if (matches.length === 0) return { value, inlined, unresolved };
  let out = '';
  let cursor = 0;
  for (const m of matches) {
    const payload = (m[1] ?? m[2] ?? m[3] ?? '').trim();
    out += value.slice(cursor, m.index);
    cursor = m.index + m[0].length;
    if (!payload || urlPayloadOutOfScope(payload)) {
      out += m[0]; // out of scope — keep the author's original token
      continue;
    }
    // Resolve relative to the test file's dir; a leading '/' is
    // WPT-server-root-relative (same convention extractFixture uses for
    // rel="match" and <link rel=stylesheet> hrefs).
    const abs = payload.startsWith('/')
      ? join(WPT_DIR, payload.slice(1))
      : resolve(baseDir, payload);
    // Only raster formats are inlined; the extension drives the mime type.
    const ext = /\.([A-Za-z0-9]+)$/.exec(abs)?.[1]?.toLowerCase();
    const mime = ext ? RASTER_MIME[ext] : null;
    if (!mime) {
      out += m[0];
      unresolved++; // non-raster (svg/font/css/…) — undeliverable as-is
      continue;
    }
    // Read + size-gate the asset. Missing file or ≥ 8 KB → lossy marker.
    let bytes = null;
    try {
      bytes = await fs.readFile(abs);
    } catch {
      bytes = null; // asset not present under tools/wpt/ — unresolvable
    }
    if (!bytes || bytes.length >= MAX_INLINE_ASSET_BYTES) {
      out += m[0];
      unresolved++;
      continue;
    }
    // Emit an UNQUOTED url() so whitespace-tokenising value parsers (e.g.
    // the converter's BackgroundExpander) see one token: the percent-
    // encoded payload contains only '%' + hex digits — no spaces, quotes,
    // parens, or commas. The single mandatory data-URI comma (RFC 2397)
    // sits inside the url() parens, which paren-aware top-level-comma
    // splitters already protect.
    out += `url(data:${mime},${percentEncodeBytes(bytes)})`;
    inlined++;
  }
  out += value.slice(cursor);
  return { value: out, inlined, unresolved };
}

/**
 * Walk a built fixture's component tree (top-level components, nested
 * `children` maps, and `_pseudo` buckets) rewriting url() references in
 * every string property value AND — wave-36 lane M1 — resolving the
 * replaced-element `_attrs.src` paths buildNode wrote verbatim. Both are
 * the same job (turn an author-written asset reference into something a
 * platform can actually fetch) against the same `baseDir`, so they share
 * one walk and one lossy roll-up rather than duplicating the traversal.
 * Components left with un-deliverable url() refs gain `_lossy: true` +
 * 'requires-bundled-asset' in `_lossyReasons`;
 * the fixture's `_wpt.lossy`/`_wpt.lossyReasons` roll up the same flag
 * when the `_wpt` block carries lossy fields (test fixtures do; ref
 * fixtures' minimal `_wpt` doesn't and is left untouched).
 *
 * Mutates `fixture` in place; returns `{ inlined, unresolved }` totals so
 * the CLI can log what happened. Exported for unit tests.
 */
export async function inlineFixtureAssets(fixture, baseDir) {
  let totalInlined = 0;
  let totalUnresolved = 0;
  // wave-36 lane M1 counters — kept SEPARATE from the url() lane's so the
  // two delivery gaps never blur into one number (and so the src lane can
  // carry its own, non-score-excluding, lossy reason; see below).
  let srcDelivered = 0;
  let totalUnresolvedSrc = 0;
  // Per-component visitor: rewrite properties, then recurse into _pseudo
  // buckets and nested children (both keyed maps of component-shaped
  // objects, per buildComponents' contract).
  async function visit(cmp) {
    if (!cmp || typeof cmp !== 'object') return;
    let unresolvedHere = 0;
    if (cmp.properties && typeof cmp.properties === 'object') {
      for (const [k, v] of Object.entries(cmp.properties)) {
        if (typeof v !== 'string' || !/url\(/i.test(v)) continue;
        const r = await inlineUrlsInValue(v, baseDir);
        cmp.properties[k] = r.value;
        totalInlined += r.inlined;
        unresolvedHere += r.unresolved;
      }
    }
    // Pseudo-element buckets carry their own properties dicts (swarm-003
    // Bug 1 shape: `_pseudo.<name>.properties`). Same rewrite applies —
    // `::before { content: url(...) }` is a real WPT pattern.
    if (cmp._pseudo && typeof cmp._pseudo === 'object') {
      for (const pe of Object.values(cmp._pseudo)) {
        if (!pe?.properties) continue;
        for (const [k, v] of Object.entries(pe.properties)) {
          if (typeof v !== 'string' || !/url\(/i.test(v)) continue;
          const r = await inlineUrlsInValue(v, baseDir);
          pe.properties[k] = r.value;
          totalInlined += r.inlined;
          unresolvedHere += r.unresolved;
        }
      }
    }
    // wave-36 lane M1: resolve the replaced element's source. buildNode put
    // the author's payload here VERBATIM (relative path / '/'-rooted path /
    // data: URI); this is where it becomes a corpus-relative path the
    // harness image route can serve. An undeliverable reference DROPS the
    // key — a fabricated path is worse than an honest absence, because the
    // harness then falls back to its documented placeholder instead of
    // painting a broken-image glyph.
    let unresolvedSrcHere = 0;
    if (cmp._attrs && typeof cmp._attrs.src === 'string') {
      const resolved = await resolveReplacedSrc(cmp._attrs.src, baseDir);
      if (resolved === null) {
        delete cmp._attrs.src;
        // Omit-when-empty all the way down: a component whose ONLY attr was
        // an undeliverable src carries no `_attrs` at all, so its bytes match
        // a component that never had one.
        if (Object.keys(cmp._attrs).length === 0) delete cmp._attrs;
        unresolvedSrcHere++;
      } else {
        cmp._attrs.src = resolved;
        srcDelivered++;
      }
    }
    if (unresolvedHere > 0) {
      // Honest lossy marker — no silent fallthrough: the value still
      // carries a path no platform can deliver, so the component (and the
      // fixture) must say so. Tag matches wpt-not-applicable.mjs Rule 20.
      totalUnresolved += unresolvedHere;
      cmp._lossy = true;
      const reasons = new Set(cmp._lossyReasons ?? []);
      reasons.add('requires-bundled-asset');
      cmp._lossyReasons = [...reasons];
    }
    if (unresolvedSrcHere > 0) {
      // Same honesty, DELIBERATELY A DIFFERENT TAG. `requires-bundled-asset`
      // is score-EXCLUDING: inject-wpt-block's applyNaScoreGate drops a test
      // from the scored set when wpt-not-applicable Rule 20's textual tag is
      // CORROBORATED by that reason (SCORE_EXCLUDED_TAGS). Rule 20's regex
      // already matches every `<img src="support/…">` in the corpus, so
      // reusing the tag here would silently move tests OUT of the scoring
      // denominator the moment one image failed to resolve — a scored-set
      // change dressed as an asset note. This lane reports its own delivery
      // gap on its own channel (the same open-string convention bidi-bake
      // uses for BIDI_BAKE_LOSSY_REASON) and cannot flip any scoreEligible.
      totalUnresolvedSrc += unresolvedSrcHere;
      cmp._lossy = true;
      const reasons = new Set(cmp._lossyReasons ?? []);
      reasons.add(REPLACED_SRC_LOSSY_REASON);
      cmp._lossyReasons = [...reasons];
    }
    // Children map: `{ childId: { id, properties, … } }` (NOT an array —
    // see buildComponents' Kotlin-parser note).
    if (cmp.children && typeof cmp.children === 'object') {
      for (const child of Object.values(cmp.children)) await visit(child);
    }
  }
  for (const cmp of Object.values(fixture.components ?? {})) await visit(cmp);
  // Roll the flag up to the test fixture's _wpt block (which carries
  // `lossy` + `lossyReasons`); ref fixtures' `_wpt` is `{ref, of,
  // specSection}` only and stays untouched.
  if (totalUnresolved > 0 && fixture._wpt && 'lossy' in fixture._wpt) {
    fixture._wpt.lossy = true;
    const reasons = new Set(fixture._wpt.lossyReasons ?? []);
    reasons.add('requires-bundled-asset');
    fixture._wpt.lossyReasons = [...reasons];
  }
  // wave-36 lane M1: same roll-up, own reason (see the per-component note).
  if (totalUnresolvedSrc > 0 && fixture._wpt && 'lossy' in fixture._wpt) {
    fixture._wpt.lossy = true;
    const reasons = new Set(fixture._wpt.lossyReasons ?? []);
    reasons.add(REPLACED_SRC_LOSSY_REASON);
    fixture._wpt.lossyReasons = [...reasons];
  }
  return {
    inlined: totalInlined,
    unresolved: totalUnresolved,
    // Additive fields — every pre-wave-36 caller reads the first two only.
    srcDelivered,
    srcUnresolved: totalUnresolvedSrc,
  };
}

// ── wave-34 lane F2 (F2a): the @font-face SCAN ───────────────────────────────
//
// THE WALL THIS OPENS. `@font-face` is the ONE at-rule that changes what
// glyphs a page paints, and until this wave the pipeline dropped it on the
// floor: parseCss skips every @-rule by design (see its banner), so a test
// declaring `@font-face { font-family: test; src: url(resources/X.woff) }`
// plus `body { font: 36px test }` reached the IR carrying the FAMILY NAME
// and no face. The ref — raw WPT HTML rendered by Chromium, which fetches
// the .woff off disk — painted the author's face; all three harnesses fell
// through the unknown family to the bundled Inter sans. COMMON-MODE by
// construction: every surface diverges from the ref in the same direction,
// so the three-way capture agreement looks healthy while every ref diff is
// typography-bound. Measured shape: css-text/boundary-shaping-001…010 (ten
// docs, all `font: 36px test` over a LinLibertine face chosen precisely
// because it carries the "fi"/"ffi" LIGATURES the tests assert on) —
// no ligature exists in Inter, so the assertion is unobservable on our side.
//
// WHAT THIS SCAN IS, AND WHAT IT IS NOT. It is a self-contained descriptor
// reader over the SAME comment-stripped stylesheet text parseCss already
// receives, plus a disk-resolution step for the `src` url() payload. It does
// NOT touch the rule walker, the element walker or the text scanner — the
// @font-face block is invisible to all three and stays that way. Its whole
// output is one DOCUMENT-level list (`fixture.fontFaces`, the authoring twin
// of the IR v2 `fontFaces` envelope key — schema/spec/01-envelope.md §5),
// because CSS scopes @font-face to the document exactly like @keyframes:
// css-fonts-4 §4.1 puts the rule in the document's font database, not on any
// element.
//
// DELIVERY IS ASYMMETRIC THIS WAVE, AND THE ASYMMETRY IS THE HONEST PART:
//   * WEB consumes it — apps/web-harness/src/sdui/useFontFaces.ts turns each
//     entry into a real `@font-face` rule whose src points at the corpus file
//     through the harness's /wpt-font/ static route (vite.config.ts).
//   * The NATIVES do not. Neither Compose nor SwiftUI has a runtime
//     face-registration hook yet (the same missing machinery Rule 43's
//     wave-31 note (e) measured for the non-Latin boundary). They DECODE the
//     key — all four readers do, so the wire is not a web-only dialect — and
//     ignore it. That is why the entry carries the font FILE PATH rather than
//     an inlined payload: a future native hop registers the file with
//     `Typeface.Builder` / `CTFontManagerRegisterFontsForURL` from exactly
//     this string, with no re-derivation.
// Rule 15 (`requires-font-face`, wpt-not-applicable.mjs) therefore still
// fires and still excludes these tests whole — see its banner for why
// narrowing it needs the native half first.
//
// WHY A PATH AND NOT A data: URI. The support-asset inliner above caps at
// 8 KB and percent-encodes (3x). A real webfont is 50–500 KB (LinLibertine
// is 261 KB → ~780 KB of fixture text each), and a combined section fixture
// carries tens of tests. The path costs ~70 bytes and every consumer that
// can reach the corpus can reach the file.

/** WPT-corpus-root-relative form of an absolute path under WPT_DIR, or null
 *  when the path escapes the corpus. Forward-slashed on every platform so the
 *  emitted fixture is byte-identical on macOS and Linux (the same
 *  normalisation extractFixture applies to `refRel`).
 *
 *  Containment is checked on the RESOLVED path, not on the author's token:
 *  `url(../../../../etc/passwd)` resolves outside the corpus and must yield
 *  null rather than a path a harness static route would then serve. */
export function wptRelativePath(abs) {
  const rel = relative(WPT_DIR, abs).split(sep).join('/');
  // `..` prefix (or an absolute leftover on Windows-style roots) means the
  // resolved target is not inside the corpus — decline, never clamp.
  if (!rel || rel === '..' || rel.startsWith('../')) return null;
  return rel;
}

/** Font file extensions the @font-face channel will deliver, mapped to the
 *  css-fonts-4 §4.3 `format()` keyword. Deliberately a CLOSED table and
 *  deliberately NOT the raster table above: a `src: url(x.png)` is not a
 *  font, and admitting one would hand the harness a resource the browser
 *  rejects with a console error instead of a loud decline here. */
const FONT_FORMATS = {
  woff2: 'woff2',
  woff:  'woff',
  ttf:   'truetype',
  otf:   'opentype',
  ttc:   'collection',
  otc:   'collection',
};

/** Strip the quotes off a CSS `<string>` token, or return an unquoted token
 *  unchanged. css-fonts-4 §4.2 lets `font-family` inside @font-face be either
 *  a `<string>` ("test") or a `<custom-ident>` sequence (test), and the two
 *  spellings name the SAME family — so both must normalise identically or the
 *  harness would inject a face nothing references. */
function unquoteCssString(tok) {
  const t = String(tok ?? '').trim();
  if (t.length >= 2 && (t[0] === '"' || t[0] === "'") && t[t.length - 1] === t[0]) {
    return t.slice(1, -1);
  }
  return t;
}

/** Split one @font-face body into its descriptor declarations on top-level
 *  `;`. Paren depth and quote state are tracked because CSS Syntax 3
 *  §4.3.5-6 make a ';' inside `url(…)` or inside a `<string>` a LITERAL —
 *  a naive `body.split(';')` would shred `src: url(a;b.woff)` into two
 *  unusable halves and silently lose the face. */
function splitDescriptors(body) {
  const out = [];
  let cur = '';
  let depth = 0;      // '(' nesting
  let quote = null;   // active quote char, or null
  for (const ch of String(body ?? '')) {
    if (quote) {
      cur += ch;
      if (ch === quote) quote = null;   // (no escape handling: CSS escapes
      continue;                          //  in a font path are not a WPT shape)
    }
    if (ch === '"' || ch === "'") { quote = ch; cur += ch; continue; }
    if (ch === '(') { depth++; cur += ch; continue; }
    if (ch === ')') { depth = Math.max(0, depth - 1); cur += ch; continue; }
    if (ch === ';' && depth === 0) { if (cur.trim()) out.push(cur.trim()); cur = ''; continue; }
    cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

/**
 * Scan a stylesheet for `@font-face` blocks and return one raw entry per
 * block, in document order.
 *
 * PURE — no disk access, no path resolution: the `src` field is the author's
 * FIRST url() payload verbatim (or null when the block declares only
 * `local()` / no usable src). Resolution is the caller's job
 * (resolveFontFaces below), which keeps this half unit-testable without a
 * corpus on disk.
 *
 * Returns `Array<{ family, src, weight, style }>` where family is the
 * unquoted family name, and weight/style are the descriptor strings as
 * authored (null when the block omits them — css-fonts-4 §4.4/§4.5 initial
 * values are `normal` for both, and letting the consumer apply the initial
 * keeps this function a reader, not an interpreter).
 *
 * Blocks with no `font-family` descriptor are DROPPED: css-fonts-4 §4.1
 * makes both `font-family` and `src` required, and a nameless face can never
 * be referenced, so emitting it would be inventing a font.
 */
export function scanFontFaces(css) {
  const out = [];
  // Same depth-counting block walk parseKeyframes uses — a regex alone
  // cannot find the matching '}' once a descriptor value contains braces.
  const re = /@font-face\s*\{/gi;
  let m;
  while ((m = re.exec(css)) !== null) {
    let depth = 1;                                  // we are inside the block's '{'
    let i = re.lastIndex;                           // first char of the block body
    while (i < css.length && depth > 0) {
      if (css[i] === '{') depth++;
      else if (css[i] === '}') depth--;
      i++;
    }
    const body = css.slice(re.lastIndex, i - 1);    // descriptors between the braces
    re.lastIndex = i;                               // resume after this block
    // Descriptor split. NOT splitTopLevel — that helper only implements
    // comma and whitespace modes. A ';' inside `url(...)` or a quoted
    // string is a literal (CSS Syntax 3 §4.3.5-6), so the split tracks
    // paren depth and quote state; everything else is a separator.
    const desc = {};
    for (const declRaw of splitDescriptors(body)) {
      const colon = declRaw.indexOf(':');
      if (colon < 0) continue;
      const k = declRaw.slice(0, colon).trim().toLowerCase();
      const v = declRaw.slice(colon + 1).trim().replace(/\s*!important\s*$/i, '').trim();
      if (!k || !v) continue;
      desc[k] = v;
    }
    const family = unquoteCssString(desc['font-family'] ?? '');
    // §4.1: a face with no family name is unreferenceable — drop, never guess.
    if (!family) continue;
    // First url() in the <font-src-list>. `local()` arms are SKIPPED rather
    // than recorded: a local() reference names an installed system face, and
    // this channel delivers FILES. A block that is local()-only yields
    // src: null and the caller drops it (Rule 15 keeps excluding the test —
    // the ref's system face is still unreachable from our side).
    let src = null;
    for (const arm of splitTopLevel(desc['src'] ?? '', ',')) {
      const um = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)"'\s]+))\s*\)/i.exec(arm);
      if (!um) continue;                            // local(…) or a malformed arm
      const payload = (um[1] ?? um[2] ?? um[3] ?? '').trim();
      if (!payload) continue;
      src = payload;
      break;                                        // §4.3: first usable arm wins
    }
    out.push({
      family,
      src,
      // As authored. `font-weight: 400 700` (a §4.4 RANGE) and `font-style:
      // oblique 20deg` both survive verbatim — this reader does not collapse
      // them, because the web consumer re-emits the same descriptor text.
      weight: desc['font-weight'] ?? null,
      style:  desc['font-style'] ?? null,
    });
  }
  return out;
}

/**
 * Resolve the raw entries from scanFontFaces against the corpus and return
 * the DELIVERABLE subset, in document order.
 *
 * An entry survives only when ALL of these hold, and each decline is a
 * DELIBERATE drop rather than a silent pass-through of an unusable string:
 *   1. it named a url() (local()-only faces carry no file);
 *   2. the payload is corpus-resolvable — not `data:` (already inline, and
 *      nothing to deliver), not http(s)/protocol-relative (Rule 37's
 *      remote-resource domain), not a `{{…}}` WPT sub-template (Rule 36);
 *   3. the resolved path stays INSIDE the corpus (wptRelativePath);
 *   4. the extension is a real font format (FONT_FORMATS);
 *   5. the file EXISTS on disk. Emitting a path to a missing file would
 *      hand the harness a guaranteed 404 and put a fabricated fact on the
 *      wire — the whole failure mode this channel exists to end.
 *
 * `baseDir` is the directory of the document the CSS came from; a leading
 * '/' is WPT-server-root-relative, matching the convention extractFixture
 * already uses for rel="match", <link rel=stylesheet> and inlineUrlsInValue.
 *
 * DE-DUPLICATED on (family, src, weight, style): WPT sheets routinely
 * declare the same face twice (once per @media arm), and a duplicate
 * @font-face rule is a no-op in CSS but a doubled payload on the wire.
 */
export async function resolveFontFaces(rawFaces, baseDir) {
  const out = [];
  const seen = new Set();
  for (const f of rawFaces) {
    if (!f?.src) continue;                                  // (1) local()-only
    if (urlPayloadOutOfScope(f.src)) continue;              // (2) data:/remote/template
    const abs = f.src.startsWith('/')
      ? join(WPT_DIR, f.src.slice(1))
      : resolve(baseDir, f.src);
    const rel = wptRelativePath(abs);
    if (!rel) continue;                                     // (3) escapes the corpus
    const ext = /\.([A-Za-z0-9]+)$/.exec(rel)?.[1]?.toLowerCase();
    if (!ext || !FONT_FORMATS[ext]) continue;               // (4) not a font file
    try {
      await fs.access(abs);
    } catch {
      continue;                                             // (5) not on disk
    }
    const entry = { family: f.family, src: rel };
    // Omit-when-absent, exactly the wire's omit-when-empty discipline: a
    // consumer applying the css-fonts-4 initial (`normal`) and a consumer
    // reading an explicit "normal" must land on the same face.
    if (f.weight) entry.weight = String(f.weight);
    if (f.style) entry.style = String(f.style);
    const key = `${entry.family} ${entry.src} ${entry.weight ?? ''} ${entry.style ?? ''}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(entry);
  }
  return out;
}

// ── Main extractor ───────────────────────────────────────────────────────────
//
// extractFixture(testRel) → { fixture, refFixture }
//   - testRel: repo-relative WPT path (matches bucket index entries)
//   - reads the ref file via rel="match"
//   - returns both the test fixture and its ref fixture so a single function
//     call gives the orchestrator both halves
//
// Pure-ish: file reads happen here. Returns objects (not paths). Caller
// writes via writeFixturePair() below for separation of concerns.
export async function extractFixture(testRel, opts = {}) {
  const idx = await bucketIndex();
  const inB = idx.buckets?.B?.includes(testRel);
  const inA = idx.buckets?.A?.includes(testRel);
  if (!inA && !inB) {
    // Not in our extractable set — likely bucket-C. Caller should have
    // pre-filtered, but we surface a clear error rather than producing
    // a misleading fixture.
    throw new Error(`extract-fixture: ${testRel} is not in bucket A or B`);
  }
  const bucket = inA ? 'A' : 'B';

  const testAbs = join(WPT_DIR, testRel);
  // wave-20 POST-LOAD STRUCTURE: `opts.htmlOverride` substitutes the INPUT
  // SOURCE only — post-load-extract.mjs feeds the SERIALIZED post-script DOM
  // (live body + original head) through this same pipeline so script-created
  // elements become first-class components. Every other step (stylesheet
  // resolution relative to testAbs, ref lookup, asset inlining, bucket check)
  // runs unchanged: the override is a different document, not a fork of the
  // extraction logic.
  const html = opts.htmlOverride ?? await fs.readFile(testAbs, 'utf8');
  const cleaned = stripComments(html);

  const inlineCss = extractInlineStyle(cleaned);
  const linkedHrefs = extractLinkedStylesheets(cleaned);
  // wave-38 lane N4: each sheet's `@import`s resolve against ITS OWN
  // directory (css-values-4 §4.5) while every downstream url() consumer
  // resolves against the DOCUMENT's — hence the two dir arguments. Only
  // provably-matching imports are inlined; see the resolveImports banner.
  const docDir = dirname(testAbs);
  let allCss = (await resolveImports(inlineCss, docDir, docDir)).css;
  for (const href of linkedHrefs) {
    // Skip http(s) — bucketer should have filtered already.
    if (/^https?:\/\//i.test(href)) continue;
    const cssAbs = href.startsWith('/')
      ? join(WPT_DIR, href.slice(1))
      : resolve(docDir, href);
    try {
      const cssRaw = await fs.readFile(cssAbs, 'utf8');
      const linked = await resolveImports(stripComments(cssRaw), dirname(cssAbs), docDir);
      allCss += '\n' + linked.css;
    } catch {
      // Missing linked CSS is expected on some WPT tests with optional
      // resources; we proceed with what we have rather than failing.
    }
  }
  const rules = parseCss(allCss);
  // wave-13 KEYFRAMES-SAMPLER: @keyframes blocks are invisible to parseCss
  // (it skips @-rules by design) — collect them in a separate pass over the
  // same comment-stripped sheet so buildComponents can statically sample
  // time-stable negative-delay animations (see the sampler section banner).
  const keyframes = parseKeyframes(allCss);

  const fuzzy = extractFuzzy(cleaned);
  const refHref = extractRefHref(cleaned);
  if (!refHref) throw new Error(`extract-fixture: ${testRel} has no rel="match" link`);
  const refAbs = refHref.startsWith('/')
    ? join(WPT_DIR, refHref.slice(1))
    : resolve(dirname(testAbs), refHref);
  const refRel = relative(WPT_DIR, refAbs).split(sep).join('/');

  // wave-21 collision fix: the stem is the SHARED subdir-encoding derivation
  // (safe-name.mjs fixtureStem), not a bare basename — nested tests with
  // equal basenames (css-break/flexbox vs css-break/grid monolithic-overflow
  // family, 52 colliding A+B pairs) previously flattened to ONE filename and
  // silently overwrote each other. The stem also seeds buildComponents'
  // idPrefix below, so component names (`<stem>__N`, `<stem>__body`, …) are
  // equally collision-free when tests are merged into a combined fixture.
  // Top-level tests keep the exact historical stem (zero corpus churn).
  const stem = fixtureStem(testRel);
  const section = specSectionOf(testRel);

  // wave-13: pass the keyframes map so the sampler can run (5th arg; the
  // 4th stays the ctx default — buildComponents harvests :defined itself).
  const built = buildComponents(cleaned, rules, stem, null, keyframes);

  // wave-37 lane W8: THE COUNTER TREE BAKE, a strict PRE-PASS to the
  // generated-content bake below. It rewrites `counter()` / `counters()`
  // inside a `content` declaration into the literal CSS <string> they
  // resolve to, so the next bake — which refuses every counter call today,
  // 3,151 of them corpus-wide — sees a pure <string> sequence and writes
  // `_text` through its existing path. Runs on `built.components` (before
  // the fixture object exists), so the lossy stamp is rolled up onto
  // `built` here rather than onto a `_wpt` that does not exist yet — the
  // same shape the generated-content bake below uses. See counter-bake.mjs
  // for the scope model and its reference derivation.
  {
    const ct = counterTreeBake.bakeCounters({ components: built.components }, cleaned);
    if (ct.resolved > 0) {
      built.lossyOverall = true;
      if (!built.lossyReasons.includes(counterTreeBake.COUNTER_BAKED_REASON)) {
        built.lossyReasons.push(counterTreeBake.COUNTER_BAKED_REASON);
      }
    }
  }

  // wave-36 lane M3: THE GENERATED-CONTENT TEXT BAKE. Runs here — after
  // buildComponents (which is where the `_pseudo` bags and their attr()
  // resolutions are finalised) and before the fixture object is assembled —
  // because the quote depth it maintains is a DOCUMENT-ORDER counter over
  // the whole tree (css-content-3 §2.1), which no per-node hook can own.
  // Refusals leave the bag byte-identical; see generated-content-bake.mjs.
  {
    // wave-37 lane W4: the document element chain's language is the third
    // argument — the PARENT language of every top-level component, which in
    // the common unslotted shape are SIBLINGS of the body-root and so have
    // no parent edge the walk could read it from. It is what resolves
    // `quotes: auto` (css-content-3 §2.2.1) into real CLDR marks.
    const gc = generatedContentBake.bakeGeneratedContentText(
      built.components, null, documentLanguage(cleaned),
    );
    if (gc.baked > 0) {
      // Same LOUD provenance contract as every other bake: the fixture-level
      // record gains the marker so the dashboard's lossy lane sees that this
      // document carries extractor-computed text, not runtime-read text.
      built.lossyOverall = true;
      if (!built.lossyReasons.includes(generatedContentBake.GENERATED_CONTENT_BAKED_REASON)) {
        built.lossyReasons.push(generatedContentBake.GENERATED_CONTENT_BAKED_REASON);
      }
    }
  }

  // wave-34 lane F2 (F2a): the document-level @font-face list. Scanned off
  // the SAME comment-stripped sheet parseCss and parseKeyframes read (all
  // three see inline <style> plus every resolved <link rel=stylesheet>), then
  // resolved against the test file's directory — the identical base the
  // support-asset inliner uses. Emitted omit-when-empty, so every fixture
  // without a webfont is byte-identical to before this wave. See the
  // "@font-face SCAN" section banner for the delivery asymmetry.
  const fontFaces = await resolveFontFaces(scanFontFaces(allCss), dirname(testAbs));

  const fixture = {
    _wpt: {
      test:   testRel,
      ref:    refRel,
      bucket,
      lossy:  built.lossyOverall || bucket === 'B',
      lossyReasons: built.lossyReasons,
      specSection: section,
      ...(fuzzy ? { fuzzy } : {}),
    },
    ...(fontFaces.length ? { fontFaces } : {}),
    components: built.components,
  };

  // wave-8: resolve + inline url() support assets relative to the test
  // file's directory (see the Support-asset-inlining section above). Runs
  // AFTER buildComponents because url() refs arrive through BOTH stylesheet
  // rules and inline style="…" attributes — the built component tree is the
  // one place both funnels have already merged. Oversize / unresolvable
  // refs mark the fixture lossy ('requires-bundled-asset').
  await inlineFixtureAssets(fixture, dirname(testAbs));

  // Ref fixture — same shape, same logic, so the dashboard can render it
  // alongside the test (Phase 4+ adds explicit ref columns; for Phase 1 we
  // capture refs separately via the browser-ref pipeline so this fixture
  // is mainly retained for diagnostic parity).
  let refFixture = null;
  try {
    const refHtml = await fs.readFile(refAbs, 'utf8');
    const refCleaned = stripComments(refHtml);
    const refInline = extractInlineStyle(refCleaned);
    const refLinks  = extractLinkedStylesheets(refCleaned);
    // wave-38 lane N4: the ref half of the @import resolution, resolved
    // against the REF file's dir. Symmetric with the keyframes / @font-face
    // passes below for the same reason — a test/ref pair must stay
    // comparable, and several corpus refs import the same support sheet
    // their test does.
    const refDir = dirname(refAbs);
    let refCss = (await resolveImports(refInline, refDir, refDir)).css;
    for (const href of refLinks) {
      if (/^https?:\/\//i.test(href)) continue;
      const cssAbs = href.startsWith('/')
        ? join(WPT_DIR, href.slice(1))
        : resolve(refDir, href);
      try {
        const linkedRef = await resolveImports(
          stripComments(await fs.readFile(cssAbs, 'utf8')), dirname(cssAbs), refDir,
        );
        refCss += '\n' + linkedRef.css;
      } catch { /* tolerate missing */ }
    }
    const refRules = parseCss(refCss);
    // wave-13: refs get the same sampler over THEIR stylesheet (refs of the
    // time-stable family are static paint, so this is normally a no-op —
    // but symmetric handling keeps the test/ref pair comparable if a ref
    // ever animates).
    const refKeyframes = parseKeyframes(refCss);
    const refBuilt = buildComponents(refCleaned, refRules, `${stem}__ref`, null, refKeyframes);
    // wave-34 lane F2: the ref half of the @font-face scan, resolved against
    // the REF file's dir. Symmetric with the keyframes sampler above for the
    // same reason — the ref is normally a static page with no webfont, but a
    // test/ref pair must stay comparable if one ever grows a face (the
    // boundary-shaping family's refs deliberately do NOT declare the test
    // font; that ASYMMETRY is the assertion, and dropping the key on one side
    // only would hide it).
    const refFontFaces = await resolveFontFaces(scanFontFaces(refCss), dirname(refAbs));
    refFixture = {
      _wpt: { ref: refRel, of: testRel, specSection: section },
      ...(refFontFaces.length ? { fontFaces: refFontFaces } : {}),
      components: refBuilt.components,
    };
    // wave-8: refs paint the same support assets (background-image-000-ref
    // uses cat.png too) — inline them relative to the REF file's dir so the
    // test/ref pair stays visually comparable. Component-level lossy
    // markers apply; the minimal ref `_wpt` block has no lossy field and
    // inlineFixtureAssets leaves it untouched by design.
    await inlineFixtureAssets(refFixture, dirname(refAbs));
  } catch {
    // Ref unavailable — leave null. The orchestrator skips ref-capture and
    // marks the test as ref-missing in the wpt block.
  }

  // wave-30 A3: surface how many of the test's own rules the static matcher
  // dropped. Deliberately OUTSIDE `fixture` — this is a fact about the
  // EXTRACTION, not part of the wire the converter and the three runtimes
  // consume (schema/spec/05-versioning.md: unknown envelope keys are an
  // error, so a diagnostic must not ride in the document). post-load-extract
  // reads it to widen its activation gate; see shouldPostLoadExtract.
  return {
    fixture, refFixture, refRel, section, stem,
    unsupportedRules: countUnsupportedRules(rules),
  };
}

/**
 * Shared body-tree → components builder. Used by both the test and ref
 * paths so they have identical shape and bug fixes. Returns
 * `{ components, lossyOverall, lossyReasons }`.
 *
 * Invariants enforced here:
 *  - Bug 3 + 4: when CSS rules scope to body/html/* and produce non-empty
 *    props, the FIRST emitted component is `<idPrefix>__body` carrying
 *    those props. Acts as the synthetic root for body-scope tests.
 *  - Bug 1: instruction <p>/<h1>/<noscript>… nodes are NOT pre-filtered;
 *    the matchedRules===0 + empty-props branch below handles them as
 *    100x100 placeholders (same cost we used to pay for the wrongly-
 *    filtered styled <p class="test"> nodes).
 *  - Bug 2: descendant elements are walked with their ancestor chain so
 *    descendant selectors match.
 *  - Bug 5: head-only elements never emit components.
 *  - wave-11 fix 2: LEADING anonymous body text (bare prose before the
 *    first element child, e.g. "There should be no red:") is emitted as a
 *    `<idPrefix>__text` component carrying `_text` — after `__body`,
 *    before the `__N` siblings — so the anonymous block box the browser
 *    lays out (CSS 2.1 §9.2.1.1) exists on our side of the diff too.
 *
 * EXTFIX-A (swarm-001 root-cause synthesis):
 *  - Top-level body elements become components keyed `<idPrefix>__N`.
 *  - Their DOM descendants (depth ≤ 5) are nested under each parent
 *    component's `children: [...]` array as IRComponent-shaped objects
 *    (`{ id, properties, _text?, children?, _lossy?, _lossyReasons? }`).
 *    Previously every descendant was flattened as a sibling under
 *    `components`, which silently destroyed parent-child relationships
 *    needed for overflow:clip / position:absolute / mix-blend-mode /
 *    container queries / static-position rectangles to be meaningful
 *    (~10 test families, 800–1500 tests).
 *  - Each element's OWN text nodes (not its descendants') become a
 *    `_text: "…"` field on its component when non-empty. Drives
 *    color / font / text-decor tests where the styled subject is text.
 */
// ── wave-15 BIDI-EXTRACT part 2: the HTML dir attribute ──────────────────────
//
// The css-text bidi family drives direction via `<div dir=rtl>` markup, not
// CSS — and the extractor dropped the attribute entirely, so the IR never
// carried a Direction property and every runtime rendered LTR. Per HTML
// §15.3.4 (the dir attribute presentational mapping), `dir=ltr|rtl` maps to
// the CSS `direction` property (plus `unicode-bidi: isolate`, which we do
// NOT emit — the runtimes have no unicode-bidi applier and a silent no-op
// property would muddy the fixture; documented scope cut). The mapping is a
// UA-origin presentational hint, so any author-origin `direction` (matched
// rule or inline style) must win — dirAttributeDirection is only consulted
// when the props bag has no `direction` key.

/**
 * First-strong direction scan — the UAX#9 P2/P3 approximation behind
 * `dir=auto`. Walks code points in order; the FIRST one that is strong
 * decides: strong R/AL → 'rtl', strong L → 'ltr'. No strong character at
 * all → 'ltr' (HTML §15.3.4's dir=auto fallback when no strong character
 * is found).
 *
 * Approximations vs the real UAX#9 tables (documented, corpus-safe):
 *  - strong-RTL is detected by BLOCK RANGES (Hebrew, Arabic + supplements,
 *    Syriac, Thaana, NKo, Samaritan/Mandaic, presentation forms, and the
 *    SMP historic-RTL + Arabic Math planes) rather than per-character
 *    Bidi_Class=R/AL lookup — the ranges contain a handful of non-strong
 *    code points (combining marks, digits) we'd misread as strong R, all
 *    unreachable as a FIRST character in real text;
 *  - strong-LTR is approximated as "any other \p{L} letter" — Bidi_Class=L
 *    covers most letters, and the exceptions (e.g. NSM-class letters) are
 *    again implausible first-strong candidates;
 *  - P2's isolate-skipping (ignore chars inside FSI…PDI) is not
 *    implemented — no isolate controls appear in the corpus.
 *
 * Exported so the unit tests can pin 'فارسی' → rtl / 'français' → ltr.
 */
export function firstStrongDirection(text) {
  if (!text) return 'ltr'; // empty → the no-strong-character fallback
  for (const ch of text) { // for…of iterates CODE POINTS, not UTF-16 units
    const cp = ch.codePointAt(0);
    // Strong-RTL block ranges (see doc comment): U+0590–08FF covers
    // Hebrew/Arabic/Syriac/Thaana/NKo/Samaritan/Mandaic + supplements;
    // FB1D–FDFD and FE70–FEFC are the presentation forms; 10800–10FFF the
    // SMP historic RTL scripts; 1E800–1EFFF Adlam + Arabic Math symbols.
    if ((cp >= 0x0590 && cp <= 0x08FF) ||
        (cp >= 0xFB1D && cp <= 0xFDFD) ||
        (cp >= 0xFE70 && cp <= 0xFEFC) ||
        (cp >= 0x10800 && cp <= 0x10FFF) ||
        (cp >= 0x1E800 && cp <= 0x1EFFF)) return 'rtl';
    // Any other letter approximates Bidi_Class=L → strong LTR.
    if (/\p{L}/u.test(ch)) return 'ltr';
    // Digits, punctuation, whitespace, controls: weak/neutral — keep going.
  }
  return 'ltr'; // scanned everything, nothing strong — HTML's ltr fallback
}

/**
 * Map an element's `dir` attribute to a resolved CSS `direction` value, or
 * null when no mapping applies. `textForAuto` is the text the dir=auto
 * first-strong scan runs over (HTML §15.3.4 walks the element's TEXT in
 * tree order; buildNode approximates that with ownText followed by the
 * descendants' ownText in document order — interleaving between own text
 * and child text is lost, a documented approximation since ownText itself
 * already concatenates around child elements).
 *
 * Invalid values (e.g. bidi-tab-001's intentional `dir=ltrl` typo) return
 * null — per HTML §15.3.4 an unrecognised dir value leaves the element in
 * "no directionality state", i.e. it inherits like the attribute were
 * absent. Exported so the unit tests can pin the mapping.
 */
export function dirAttributeDirection(attrs, textForAuto = '') {
  // No attrs bag or no dir attribute — nothing to map.
  const raw = attrs?.dir;
  if (raw === undefined) return null;
  const v = raw.trim().toLowerCase(); // dir values are ASCII case-insensitive
  if (v === 'ltr' || v === 'rtl') return v; // the two literal states
  // dir=auto: resolve NOW via the first-strong heuristic so the IR carries
  // a concrete direction (runtimes have no auto-resolution machinery).
  if (v === 'auto') return firstStrongDirection(textForAuto);
  return null; // invalid value — no directionality state (see doc comment)
}

/**
 * wave-30 A1: resolve the DIRECTIONALITY of an element for `:dir()`
 * (Selectors-4 §11.2). Returns 'ltr' | 'rtl' — never null, because the
 * pseudo-class is a total function: HTML §3.2.6.4 gives every element a
 * directionality, defaulting to the document's, which is ltr absent any
 * `dir` attribute.
 *
 * Resolution order, straight out of HTML §3.2.6.4 "the directionality of an
 * element":
 *   1. the element's OWN `dir` attribute (ltr / rtl literal, or `auto`
 *      resolved by the first-strong scan over its text — dirAttributeDirection
 *      does both, and returns null for an invalid value, which HTML says
 *      leaves the element in "no directionality state", i.e. inheriting);
 *   2. otherwise the NEAREST ANCESTOR carrying a directionality state
 *      (inheritance — walk the chain from the immediate parent upwards);
 *   3. otherwise 'ltr'.
 *
 * DELIBERATELY NOT CONSULTED: the CSS `direction` property. Selectors-4
 * §11.2 is explicit that :dir() matches on the HTML directionality, and the
 * two can disagree — dir-selector-change-001 sets `#outer { direction: ltr }`
 * in CSS while the script sets `dir="rtl"`, and its ref proves the attribute
 * decides. Reading the CSS property here would invert that test.
 *
 * `pos.subtreeText` / `ancestor.pos.subtreeText` is the dir=auto text corpus
 * stamped by extractBodyTreeNested's second pass (see there). When it is
 * absent — legacy direct callers that pass no `pos`, or the pre-recursion
 * white-space resolution inside the walker — a `dir=auto` element scans an
 * EMPTY string and therefore resolves to HTML's no-strong-character fallback
 * 'ltr'. Documented degradation, not a silent one: every production match
 * (propsForElement ← buildNode) runs after the stamp.
 *
 * Exported so the unit tests can pin each rung of the ladder.
 */
export function resolveDirectionality(attrs, pos = null, ancestors = null, documentDir = null) {
  // Rung 1 — the element's own attribute wins outright.
  const own = dirAttributeDirection(attrs, pos?.subtreeText ?? '');
  if (own) return own;
  // Rung 2 — nearest ancestor with a directionality state. The chain is in
  // document order, so walking DOWN the indices walks UP the tree; the first
  // hit is the nearest ancestor, exactly like CSS inheritance.
  for (let i = (ancestors?.length ?? 0) - 1; i >= 0; i--) {
    const a = ancestors[i];
    const d = dirAttributeDirection(a?.attrs, a?.pos?.subtreeText ?? '');
    if (d) return d;
  }
  // Rung 2b — the DOCUMENT-ELEMENT rung. `ancestors` is the BODY subtree
  // chain: extractBodyTreeNested walks body's descendants only, and the
  // synthetic `:root` sentinel propsForElement prepends carries `attrs: {}`.
  // So `<html dir="rtl">` / `<body dir="rtl">` — both genuine ancestors that
  // DO carry a directionality state per HTML §3.2.6.4 — were invisible here
  // and every body descendant fell to the ltr default.
  // MEASURED (skeptic-1, wave-30): selectors/dir-style-02a.html is
  // `<html dir="rtl">` with `:dir(ltr){color:blue} :dir(rtl){color:lime}`;
  // three of its six top-level divs (default-direction, inherit-default, and
  // the invalid `dir="foopy"` one) baked `blue` where the ref paints `lime`,
  // and post-load could NOT repair it because `color` is not in
  // POST_LOAD_COMPUTED_PROPERTIES. documentDirectionality() supplies the
  // missing rung; null (legacy direct callers) keeps the old ladder exactly.
  if (documentDir) return documentDir;
  // Rung 3 — the document default when nothing in the tree declares one.
  return 'ltr';
}

/**
 * The directionality declared by the DOCUMENT ELEMENT chain — the `dir`
 * attribute on `<body>` (nearer) else `<html>` (farther), per HTML §3.2.6.4's
 * inheritance walk. Returns 'ltr' | 'rtl', or null when neither declares a
 * valid one (so resolveDirectionality keeps its own default).
 *
 * `dir="auto"` on html/body resolves through dirAttributeDirection with an
 * `dir="auto"` on html/body is SKIPPED rather than answered: the first-strong
 * scan over a whole document is not something this string-scanning extractor
 * can do honestly, and `dirAttributeDirection` with an empty corpus would
 * silently answer 'ltr' — a guess dressed as a fact. Skipping lets the OUTER
 * candidate answer instead (`<html dir=rtl><body dir=auto>` → rtl), and when
 * nothing else declares one the caller's own 'ltr' default applies, so the
 * behaviour is never worse than before this rung existed. Same
 * documented-degradation contract as the `pos.subtreeText` note above.
 *
 * Exported for the unit pins.
 */
export function documentDirectionality(html) {
  if (typeof html !== 'string') return null;
  // <body> is the nearer ancestor of every component we emit, so it wins.
  for (const re of [/<body\b([^>]*)>/i, /<html\b([^>]*)>/i]) {
    const m = re.exec(html);
    if (!m) continue;
    // Lookbehind excludes a preceding word char or hyphen so `data-dir=…`,
    // `aria-dir=…` and any `*dir` custom attribute cannot be read as `dir`.
    const d = /(?<![\w-])dir\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(m[1]);
    if (!d) continue;
    const val = d[2] ?? d[3] ?? d[4] ?? '';
    // `auto` is unanswerable here (see the doc comment) — fall through to the
    // outer candidate instead of letting the empty-corpus scan guess 'ltr'.
    if (val.trim().toLowerCase() === 'auto') continue;
    const resolved = dirAttributeDirection({ dir: val }, '');
    if (resolved) return resolved;
  }
  return null;
}

/**
 * wave-39 lane A5 — THE dir-ATTRIBUTE BODY ROOT (N6's honest deferral).
 *
 * WHAT WAS MISSING, measured. Wave-38 lane N6 wired the PRINCIPAL DIRECTION:
 * css-writing-modes-4 §8 propagates the body's `direction` to the initial
 * containing block, and the web harness now hoists it onto the ICB div
 * (apps/web-harness resolveCanvasDirection). That channel reads ONE place —
 * the synthetic `meta.role: 'body-root'` component — and the extractor mints
 * that component only when a root-scope CSS RULE (`body {…}` / `html {…}` /
 * `:root {…}` / `* {…}`) matched. A document that declares its base direction
 * the HTML way, `<body dir=rtl>` or `<html dir=rtl>`, therefore had no
 * component for the propagation to ride and stayed left-to-right end to end.
 * The same hole starves the wave-30 A4 bake-down, whose ROOT_INHERITED_
 * TRIGGER_PROPS list names `direction` by name: with no root bag there is no
 * inheritance SOURCE, so not even the top-level children ran RTL.
 *
 * POPULATION: 9 bucket-A tests carry a `dir` attribute on html/body
 * (css-align self-align-safe-unsafe-{flex,grid}-003, css-ruby ruby-bidi-001/
 * -003, css-text text-align-match-parent-root-{ltr,rtl,logical}, selectors
 * dir-pseudo-update-document-element and dir-style-02a). Six of them resolve
 * to `rtl`; two of the nine sit inside the depth-48 gate.
 *
 * WHY THIS IS THE SAME MAPPING THE PER-ELEMENT PATH ALREADY MAKES. HTML
 * §15.3.4 defines `dir=ltr|rtl` as a presentational hint for the CSS
 * `direction` property; buildNode has mapped it onto every ordinary element
 * since wave-15 (see the BIDI-EXTRACT part 2 banner). This is that mapping
 * applied to the ONE element the element walker never visits — `<body>` —
 * routed to the bag that represents it. Two properties of that mapping are
 * inherited verbatim rather than re-decided here:
 *   * `unicode-bidi: isolate`, the other half of §15.3.4's hint, is NOT
 *     emitted (no runtime has a unicode-bidi applier — wave-15's documented
 *     scope cut);
 *   * `dir=auto` on html/body is not answered at all, because
 *     documentDirectionality refuses it rather than guessing (its own doc
 *     comment records why).
 *
 * PRECEDENCE. The hint is UA-origin (CSS Cascade 5 §6.1), so any author
 * `direction` already in the root bag — from `html {direction:ltr}`, from
 * `body {direction:rtl}` — wins and this writes nothing. Identical guard to
 * the per-element path's `('direction' in props) ? null : …`.
 *
 * WHY `rtl` ONLY, and why that is not an asymmetry. `ltr` is the INITIAL
 * value of `direction` (css-writing-modes-4 §2.1), so a minted `direction:
 * ltr` bag states a fact the cascade already holds: no box moves, the ICB
 * hoist refuses it by name (resolveCanvasDirection returns undefined for
 * `ltr`), and the only observable effects would be churn — a brand-new empty
 * component in the flat list of every LTR document plus a baked no-op
 * `direction: ltr` and a `body-inherited-baked` lossy marker on each of its
 * top-level children. That is the same reason resolveCanvasWritingMode and
 * resolveCanvasDirection both refuse their initial values. Refusing `ltr`
 * here keeps every LTR fixture in the ~10.7k-fixture corpus byte-identical,
 * including the currently-passing gate cell
 * selectors/dir-pseudo-update-document-element (`<html dir="ltr">`).
 *
 * MUTATES `root.props` in place (same contract as bakeSampledAnimation) and
 * returns true when it wrote, so the caller can open its emit gate for a
 * document whose ONLY root-scope declaration is this one. Exported for the
 * unit pins.
 *
 * @param {{props: Record<string,string>}} root  the propsForBodyRoot result
 * @param {'ltr'|'rtl'|null|undefined} documentDir  documentDirectionality()
 * @returns {boolean} true when a `direction` was minted onto the bag
 */
export function mintDocumentDirection(root, documentDir) {
  // Defensive: legacy direct callers may hand a bagless root or no dir at all.
  if (!root || !root.props || typeof root.props !== 'object') return false;
  // Only the non-initial keyword carries information (see the doc comment).
  if (documentDir !== 'rtl') return false;
  // UA-origin loses to any author declaration already in the root scope.
  if ('direction' in root.props) return false;
  root.props.direction = documentDir;
  return true;
}

// ── THE LANG WIRE (wave-37 lane W4) ─────────────────────────────────────────
//
// WHAT WAS MISSING, measured. The `lang` attribute is the ONE piece of source
// state that changes how the SAME declarations paint, and nothing in the
// pipeline carried it. Two mechanisms depend on it and both were silently
// running on the browser's default locale in every capture:
//
//   1. `quotes: auto` (css-content-3 §2.2.1) — the UA's `q::before {
//      content: open-quote }` picks a CLDR quote pair keyed by the content
//      language. With no lang attribute anywhere in the harness DOM every
//      `<q>` in the corpus painted the ROOT pair (“ ” / ‘ ’), so the whole
//      css-content `quotes-004…027` family (one test per language) captured
//      English marks against Amharic « ›, French « «, Japanese 「 『 refs.
//   2. Chromium's font FALLBACK for a generic family. `font: 32px serif`
//      under `<html lang="ja">` resolves to a Japanese serif face — for the
//      LATIN text too, which is why the quotes-016 reference wraps its
//      English sentence in three lines where our capture used two. The
//      bucketer already tagged those six tests `requires-bundled-font`; the
//      font was never missing, the LANGUAGE that selects it was.
//
// MEASURED before this lane (tools/titan/runs/wave37-W4-base, css-content
// --web-only full cap, 60 tests): 37 pass. The lang-family fails were
// quotes-014/016/018/025/026/027 (Devanagari + CJK, 0.776…0.812) and
// quotes-029…034 (mixed-language and explicit-pair-list variants).
//
// WIRE CONTRACT (`_lang` → IR v2 `meta.lang`, the additive meta key
// schema/spec/05-versioning.md sanctions — same extension point wave-20's
// `_attrs`, wave-22's `_decorations`, wave-27's `_markerText` and wave-32's
// `_runs` used):
//
//   * The value is the element's COMPUTED content language (HTML §3.2.6.2):
//     its own `lang` attribute, else the nearest ancestor's, else the
//     document element chain's (`<body lang>` nearer, `<html lang>` farther).
//     Already RESOLVED, so a consumer never has to walk anything — the flat
//     v2 component list has no parent edge to walk in the first place.
//   * VERBATIM as authored. `lang="eN-Us"` ships as `eN-Us`, not `en-US`:
//     BCP-47 matching is case-insensitive (RFC 4647 §2.1) and every consumer
//     lowercases at lookup, so canonicalising here would only destroy the
//     round-trip and make the fixture lie about the source.
//   * OMITTED when the document declares no language at all — absence means
//     "unknown", which is exactly what the browser's own default-locale
//     behaviour is, so a lang-free document's fixture stays byte-identical.
//   * `lang=""` is the HTML spelling of "explicitly unknown" (§3.2.6.2) and
//     therefore STOPS the walk without emitting a key — it must not fall
//     through to an outer `lang`, or the empty attribute would do nothing.
//
// The attribute-name regex uses the same `(?<![\w-])` lookbehind as the `dir`
// scan above so `data-lang=`, `xml:lang=`, `hreflang=` and any `*lang`
// custom attribute cannot be misread as `lang`. (`xml:lang` is deliberately
// out: in a text/html document it is a no-op for language determination —
// HTML §3.2.6.2 only honours it in XML documents — and no bucket-A test
// uses it.)

/** Read a `lang` attribute value off an attrs bag, or null when absent. */
function langAttrOf(attrs) {
  if (!attrs || typeof attrs !== 'object') return null;
  // The walkers lowercase attribute NAMES (parseAttrsFromTagOpen), so one
  // key lookup covers `lang`, `LANG` and `Lang`.
  const raw = attrs.lang;
  return typeof raw === 'string' ? raw.trim() : null;
}

/**
 * The language declared by the DOCUMENT ELEMENT chain — the `lang` attribute
 * on `<body>` (nearer) else `<html>` (farther), the twin of
 * documentDirectionality's ladder and for the identical reason: both are
 * genuine ancestors of every component we emit, but the walker's chain
 * starts INSIDE body, so the fact has to travel on the ctx.
 *
 * Returns the authored string, `''` for an explicit `lang=""` (the "unknown"
 * spelling, which the caller must not fall through), or null when neither
 * element declares one.
 *
 * Exported for the unit pins.
 */
export function documentLanguage(html) {
  if (typeof html !== 'string') return null;
  for (const re of [/<body\b([^>]*)>/i, /<html\b([^>]*)>/i]) {
    const m = re.exec(html);
    if (!m) continue;
    // `(?<![\w:-])` — the `dir` scan's lookbehind PLUS a colon, so `hreflang`,
    // `data-lang` and `xml:lang` are all excluded. The colon matters here and
    // not for `dir`: `xml:lang` is a real attribute that HTML §3.2.6.2
    // deliberately ignores in a text/html document, so reading it would be a
    // wrong answer rather than a nonexistent one.
    const l = /(?<![\w:-])lang\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(m[1]);
    if (!l) continue;
    return (l[2] ?? l[3] ?? l[4] ?? '').trim();
  }
  return null;
}

/**
 * The COMPUTED content language for one element (HTML §3.2.6.2): its own
 * `lang`, else the nearest ancestor that declares one, else the document
 * element chain's. Returns null when nothing declares a language, and null
 * for an explicit `lang=""` — see the wire contract above.
 *
 * `ancestors` is the walker's chain in OUTERMOST-FIRST order, so the nearest
 * declaring ancestor is found by scanning it backwards.
 *
 * Exported for the unit pins.
 */
export function resolveLanguage(attrs, ancestors = null, documentLang = null) {
  const own = langAttrOf(attrs);
  if (own !== null) return own === '' ? null : own;
  if (Array.isArray(ancestors)) {
    for (let i = ancestors.length - 1; i >= 0; i--) {
      const a = langAttrOf(ancestors[i]?.attrs);
      if (a !== null) return a === '' ? null : a;
    }
  }
  if (typeof documentLang === 'string') return documentLang === '' ? null : documentLang;
  return null;
}

/**
 * Concatenate a nested-tree node's own text with every descendant's own
 * text in document order — the text corpus the dir=auto first-strong scan
 * walks (HTML §15.3.4 descends the subtree; only the first STRONG
 * character matters, so the lost own-text/child-text interleaving noted on
 * dirAttributeDirection can only misorder the result when the deciding
 * strong character sits in a child that precedes part of the parent's own
 * text — not observed in the corpus). Separator spaces keep adjacent runs
 * from fusing into artefact tokens (harmless to the scan: space is
 * neutral).
 */
function collectSubtreeText(node) {
  // Own text first (the common carrier), then children in document order.
  let out = node.ownText || '';
  for (const child of node.children ?? []) {
    const t = collectSubtreeText(child);
    if (t) out += (out ? ' ' : '') + t;
  }
  return out;
}

// ── wave-17 BODY-HEIGHT SLOTTING (the twice-queued sibling-stacking fix) ─────
//
// DIAGNOSIS (wave-15, css-backgrounds/background-attachment-fixed-inside-
// transform-1): when the test styles `body { height: 4000px }`, the extractor
// emitted the body-root as a SIZED component and then stacked the body's
// element children as SIBLING roots BELOW it — so #outer, which overlaps the
// body in the real page (rotated band around y≈340), rendered at y≈4340 on
// ALL THREE platforms. With ~90% of both images white, the diff VACUOUSLY
// passed at 0.96 (flagged by lowContentDensity).
//
// DECISION — child-slotting over canvas-min-height (both candidates from the
// diagnosis were evaluated against the composed-canvas contract):
//  - Every composed canvas stacks ROOTS in document order in normal flow and
//    consults the body-root ONLY for the canvas background: web
//    (apps/web-harness/src/ui/ComposedCaptureGallery.tsx renders composeTree
//    roots in a block-flow div; resolveCanvasBackground finds meta.role ==
//    'body-root' in the FLAT v2 component list), Compose
//    (apps/android-harness/…/ScreenshotCaptureScreen.kt composed column;
//    resolveComposedCanvasBackground scans the ROOTS list), SwiftUI
//    (apps/ios-harness/…/CaptureCanvas.swift VStack over split.flow;
//    canvasBackground scans document.components UNSPLIT).
//  - There is NO fixture→canvas min-height channel: moving the body height to
//    a "canvas min-height" would need new IR meta plumbed through the
//    converter plus all three canvases (4 codebases touched).
//  - Slotting the body's element children as CHILDREN of the body-root is
//    (a) the source-truth DOM structure — they ARE <body>'s children, so they
//    stack from the body's top INSIDE its painted 4000px area exactly like
//    the real page; (b) extractor-only — the nested-children pipeline
//    (EXTFIX-A `children` maps → converter → v2 slot/parent → every
//    renderer's own child loop) already exists and is exercised by every
//    nested fixture; and (c) height-correct — the body-root child keeps its
//    explicit height, so the composed canvas's natural height matches the
//    ref capture's documentHeight (the ref page is 4000px tall too).
//    Canvas-background lookups keep working: the body-root stays a root
//    (Android) and stays in the flat v2 list (web/iOS).
//
// NARROW TRIGGER (blast-radius-conscious): slotting happens ONLY when the
// body-root bag declares an explicit, NONZERO, ABSOLUTE height — the only
// values that materialize as a sized sibling block on every platform (the IR
// normalizes absolute lengths to px; see schema/spec/02-values.md). Kept
// byte-for-byte OUTSIDE the trigger, each for a documented reason:
//  - no height / `auto` / CSS-wide keywords: no sized block, nothing to fix;
//  - zero heights (`0`, `0px` — the css-overflow body-propagation family):
//    a 0-height sibling block already stacks children at the canvas top,
//    identical geometry to nesting;
//  - percentages (`100%`): resolve against the auto-height composed canvas,
//    i.e. fall back to auto (CSS 2.2 §10.5) — no push today;
//  - viewport/font-relative units (`300vh`, `em`) and functions
//    (`calc-size(…)`): normalize to null in the IR ("runtime-dependent",
//    schema/spec/02-values.md), so no runtime sizes the sibling block —
//    byte-identical fixtures; widening to these is a documented follow-up.
//
// The absolute-<length> grammar: CSS Values §6.1 absolute units only
// (px/cm/mm/Q/in/pc/pt), one non-negative number, case-insensitive unit.
const ABSOLUTE_LENGTH_RE = /^(\d*\.?\d+)(px|cm|mm|q|in|pc|pt)$/i;

/**
 * True when a body-root property bag declares an explicit nonzero ABSOLUTE
 * height — the wave-17 slotting trigger (see the decision block above).
 * `block-size` is the logical alias of `height` in horizontal writing modes
 * (CSS Logical §4.1 — the corpus default; vertical writing modes would remap
 * it, an accepted approximation while no body-level vertical-wm test needs
 * slotting). `height` wins when both appear, mirroring cascade order-of-
 * appearance being irrelevant for distinct properties (physical beats
 * logical here only as a deterministic tie-break, documented not silent).
 * Exported so the unit tests can pin the trigger's edge set.
 */
export function bodyDeclaresAbsoluteHeight(props) {
  // Physical `height` first, then the logical alias (see doc comment).
  const raw = props?.height ?? props?.['block-size'];
  if (typeof raw !== 'string') return false; // absent → no trigger
  const m = ABSOLUTE_LENGTH_RE.exec(raw.trim()); // absolute <length> only
  // Nonzero magnitude required — zero-height bodies keep the legacy sibling
  // shape byte-for-byte (identical geometry either way; see trigger notes).
  return m !== null && parseFloat(m[1]) > 0;
}

// ── wave-30 A4: root-scope INHERITANCE, delivered by BAKE-DOWN ──────────────
//
// The wave-17 trigger above is about GEOMETRY (a sized body pushes its
// siblings down). This block is about INHERITANCE, and it is the same bug seen
// from the other side: while the body's element children are emitted as
// SIBLINGS of the body-root, they are not its descendants on any platform, so
// nothing the body-root declares inherits into them.
//
// MEASURED (wave-30, selectors__caret-color-visited-inheritance): the test's
// only root-scope rule is `:root { font-size: 50px; caret-color: orange }`.
// The wave29-final per-test IR
// (runs/wave29-final/sections/selectors/per-test-ir/…caret-color-visited-
// inheritance.json) shows FontSize 50px sitting on the `body-root` component
// and the `<main>` / `<a>` components carrying `properties: []` — so the
// 50px never reaches the text under test and every platform paints it at the
// 16px default.
//
// WHY NOT SLOTTING (the wave-30 gate finding, and the reason this block is a
// BAKE and not a second arm of shouldSlotBodyChildren). Reusing the wave-17
// nesting for this case looks free — the children really ARE <body>'s
// children — but the two cases differ in ONE load-bearing way: the wave-17
// body-root is SIZED (an explicit nonzero absolute height), and an
// inheritance-only body-root is NOT. Nesting children under an UNSIZED parent
// is a shape the natives render differently from the web:
//   text-decoration-inset-001  android 0.9742 → 0.8162 · ios 0.9576 → 0.8160
//   text-decoration-inset-002  android 0.9733 → 0.8097 · ios 0.9567 → 0.8098
// (tools/titan/runs/wave30-final vs wave29-final; web held 0.987 on both, so
// the divergence is the natives' unsized-container sizing, not a fixture bug
// they alone see). 342 corpus fixtures took that shape change — a latent mine
// under every future native run, in exchange for a repair that does not need
// the nesting at all.
//
// WHAT THE BAKE DOES INSTEAD: copy each declared trigger property from the
// body-root bag onto every TOP-LEVEL body-child component that does not
// itself declare it. That is exactly the hop that was missing — the runtimes
// already thread inherited properties from a component to its descendants
// (Compose ComponentRenderer.INHERITED_PROPERTY_TYPES + LocalInheritedProperties,
// SwiftUI InheritedText, web DOM inheritance), so children DEEPER in a
// subtree inherit naturally once the top-level hop carries the value. The
// body-root's own bag is left untouched: the composed canvases read its
// background/padding off it (resolveComposedCanvasBackground on Android, the
// flat-v2 body-root scan on web/iOS), and the component tree keeps the
// byte-for-byte wave-29 SIBLING shape every native renderer is scored on.
//
// SCOPE — only properties that INHERIT (CSS Cascade 5 §4.1: an inherited
// property's initial cascade step is the parent's computed value). A
// non-inherited root declaration (`background`, `margin`, `contain`, …)
// changes nothing for the children, so including it would rewrite bags for no
// gain. The list below is deliberately the closed set the corpus's root-scope
// rules actually use, not every inherited property in the catalogue: each
// entry is one we have SEEN at body/html/:root scope, and widening it further
// is a measurable follow-up rather than a guess.
//
// KNOWN, DELIBERATE GAP: an inherited property OUTSIDE this trigger set that
// the body-root declares (say `white-space`, which the runtimes DO carry in
// their inherited sets) is still not delivered to the children. That gap is
// identical to wave-29's and is the price of the closed list; widening the
// list is the follow-up, and it now costs a value copy rather than a
// corpus-wide restructure.
//
// `direction` is on the list because it is the one inherited property whose
// loss is silently invisible: an RTL body with LTR-rendered children looks
// like a renderer bug, not a fixture bug.
const ROOT_INHERITED_TRIGGER_PROPS = [
  'font-size',       // CSS Fonts 4 §3.5  — the measured caret-color case
  'font-family',     // CSS Fonts 4 §3.1
  'font-weight',     // CSS Fonts 4 §3.2
  'font-style',      // CSS Fonts 4 §3.4
  'color',           // CSS Color 4 §3.1
  'line-height',     // CSS Inline 3 §4.1
  'direction',       // CSS Writing Modes 4 §2.1 (see note above)
  'caret-color',     // CSS UI 4 §7.1
  'letter-spacing',  // CSS Text 4 §8.1
  'word-spacing',    // CSS Text 4 §8.2
  'text-align',      // CSS Text 4 §7.1
  'visibility',      // CSS Display / CSS 2.2 §11.2
  // wave-36 lane M3: `quotes` (css-content-3 §2.2 — inherited, initial
  // `auto`). This is the banner's "KNOWN, DELIBERATE GAP" being closed for
  // one measured property, not a widening on principle.
  //
  // MEASURED (wave36-M3-base, the css-content section re-run at full cap):
  // quotes-031 `body { quotes: "‹" "›" }` scored 0.8701 and quotes-032
  // `body { quotes: none }` scored 0.8822 — and the capture for BOTH paints
  // the DEFAULT curly marks. The declaration sat on the body-root bag, the
  // `<p>`/`<q>` components are the body's SIBLINGS on the wire, so nothing
  // delivered it: the web harness renders a real `<q>` and Blink's UA rule
  // `q::before { content: open-quote }` then read the initial `auto` instead
  // of the author's list. quotes-033 (`body { quotes:none }` +
  // `.inner { quotes:auto }`) is the same root declaration with an element
  // override that only becomes observable once the root value arrives.
  //
  // It qualifies on the banner's own SCOPE test — it inherits, so the root
  // declaration really is the children's cascade input — and it needs no
  // entry in INHERITED_COVERING_SHORTHANDS: no CSS shorthand sets `quotes`
  // (css-content-3 defines it as a standalone longhand), so no child
  // declaration can decide it other than by naming it, which the generic
  // `childProps[prop] !== undefined` guard already honours.
  'quotes',          // css-content-3 §2.2
];

/**
 * True when a body-root property bag declares at least one INHERITED
 * property the children need to receive (see the banner above for the scope
 * argument and the measured case). Exported so the unit tests can pin the
 * exact trigger set — a silent widening of this list changes what every
 * top-level component carries corpus-wide and must never land unreviewed.
 */
export function bodyDeclaresInheritedProperty(props) {
  if (!props) return false;
  // Presence is the trigger, not the value: even `font-size: inherit` at
  // root scope is a declaration the children must see resolved, and the
  // runtimes' own cascade is what interprets it.
  //
  // wave-35 lane FX: read the bag through the font-shorthand derivation, so a
  // root whose ONLY typography declaration is `font: 36px test` fires the
  // trigger it plainly is. Identity for every bag without a derivable `font`,
  // so no pre-FX fixture's trigger answer moves.
  const effective = rootPropsWithFontShorthand(props);
  return ROOT_INHERITED_TRIGGER_PROPS.some((p) => effective[p] !== undefined);
}

/**
 * The ONE slotting decision. GEOMETRY ONLY (wave-17): an explicit nonzero
 * absolute height on the body-root, because a SIZED body-root sibling pushes
 * the real content below the viewport and only nesting puts it back.
 *
 * The wave-30 inheritance case is deliberately NOT an arm here — see the
 * bake-down banner above for the measured native regressions an UNSIZED
 * slotted parent caused (text-decoration-inset-001/002, ~0.97 → ~0.81 on both
 * natives) and why a value copy delivers the same repair with no shape change.
 * Exported for the unit pins.
 */
export function shouldSlotBodyChildren(props) {
  return bodyDeclaresAbsoluteHeight(props);
}

// Shorthands whose presence in a child's own bag means the child ALREADY
// decides the longhand, so the root's value must not be baked over it
// (css-cascade-4 §3.2: a shorthand sets every longhand it covers, including
// the ones it does not name — `font: 12px serif` resets line-height to
// `normal`). Keyed by trigger longhand; only the shorthands that actually
// cover a member of the trigger set appear.
const INHERITED_COVERING_SHORTHANDS = {
  // css-fonts-4 §6: the `font` shorthand sets font-style/weight/size/
  // family AND resets line-height — all five are trigger props.
  'font-size': ['font'],
  'font-family': ['font'],
  'font-weight': ['font'],
  'font-style': ['font'],
  'line-height': ['font'],
  // css-ui-4 §7.2: `caret: <color> || <caret-shape>`.
  'caret-color': ['caret'],
};

// css-cascade-4 §3.2: `all` is the shorthand for EVERY property EXCEPT
// custom properties, `direction` and `unicode-bidi`. A child declaring `all`
// has therefore spoken about every trigger prop but that one, so this set
// names the carve-out rather than folding `all` into the per-longhand map
// above — where the exception would have been invisible.
const ALL_SHORTHAND_EXEMPT = new Set(['direction']);

// ── wave-35 lane FX: THE FONT-SHORTHAND BAKE ───────────────────────────────
//
// THE HOLE THIS CLOSES. Every trigger property above is a LONGHAND, and the
// bake reads the root bag by longhand name. But the single most common way a
// WPT document sets the document base font is the SHORTHAND:
//
//     body { font: 36px test }        (css/css-text/boundary-shaping/*, ×8)
//
// `bodyDeclaresInheritedProperty` saw no `font-size` key, no `font-family`
// key, and answered false — so the bake never engaged and the three text
// components of `of<span class=a>f</span>ice` shipped with EMPTY bags. Every
// platform then painted them at the harness's own 16px Inter default while
// the browser-ref painted 36px LinLibertine with the `ffi` ligature the test
// is actually about. Lane B2 delivered the FACE (fixture.fontFaces →
// useFontFaces → a real @font-face rule); this lane delivers the FAMILY NAME
// that selects it. Without both halves the downloaded face is registered and
// never referenced.
//
// WHY DERIVE LONGHANDS AND NOT COPY THE SHORTHAND. Baking the raw `font`
// string onto the child would be one line, and it would be wrong twice over:
//   * `font` is a RESETTING shorthand — dropping `font: 36px test` on a child
//     that declares its own `font-weight` silently resets that weight to
//     `normal`, so the child's own author declaration would lose to a value
//     handed down from its parent. That inverts css-cascade-4 §7.3, which is
//     the one rule this whole bake exists to respect.
//   * the per-longhand guards below (`childProps[prop] !== undefined`) could
//     no longer fire per property: a child declaring only `font-family`
//     would have to refuse the whole shorthand, losing the size too.
// Deriving the longhands keeps the bake PER-PROPERTY, so each one meets the
// same two guards every other trigger property already meets.
//
// PARITY WITH THE CONVERTER IS THE CONTRACT. The body-root component keeps
// its `font` shorthand in its own bag, and :converter expands it with
// FontExpander.kt. If this function derived a DIFFERENT longhand set than
// that expander, one document would carry two disagreeing readings of the
// same declaration — the root's and its children's. So the grammar,
// the token classes and the css-fonts-4 §4.3 `line-height` reset below are
// deliberately the same decisions FontExpander.kt makes, and the unit pins
// assert the overlap explicitly.
//
// WHAT IS REFUSED, LOUDLY AND ON PURPOSE (parseFontShorthand returns null,
// and the bake then behaves exactly as it did before this lane — no
// derivation, no marker, no drift):
//   * <system-family-name> — `font: menu`, `caption`, `icon`, `message-box`,
//     `small-caption`, `status-bar`. css-fonts-4 §3.7 resolves these from the
//     PLATFORM's font database, so there is no size and no family to derive:
//     any value we invented would be a guess about the capture host.
//   * the CSS-wide keywords (`inherit`, `initial`, `unset`, `revert`,
//     `revert-layer`). They set every longhand to a keyword whose meaning is
//     "ask the cascade again", and the fixture wire has no cascade to ask.
//   * anything carrying `var()` — unresolvable by construction (the same
//     rule the IR states as "null means runtime-dependent").
//   * a value that does not parse as the §3.7 size/family form at all: a
//     missing size, a missing family, an unrecognised token before the size.
//     CSS 2.2 §4.2 drops an invalid declaration whole; so do we, rather than
//     bake half of a value we did not understand.
const FONT_SH_SYSTEM_FAMILIES = new Set([
  'caption', 'icon', 'menu', 'message-box', 'small-caption', 'status-bar',
]);
// css-cascade-5 §7 — the CSS-wide keywords, valid on every property.
const FONT_SH_CSS_WIDE = new Set([
  'inherit', 'initial', 'unset', 'revert', 'revert-layer',
]);
// The pre-size modifier keywords, css-fonts-4 §3.7:
//   [ <'font-style'> || <font-variant-css2> || <'font-weight'> || <font-width-css3> ]?
// `normal` is legal in ALL FOUR slots and says nothing about which, so it is
// consumed generically rather than assigned — exactly what FontExpander.kt
// does (its `!= "normal"` guards on the variant/stretch arms).
const FONT_SH_STYLE_KEYWORDS = new Set(['italic', 'oblique']);
const FONT_SH_VARIANT_KEYWORDS = new Set(['small-caps']);
const FONT_SH_WEIGHT_KEYWORDS = new Set(['bold', 'bolder', 'lighter']);
const FONT_SH_STRETCH_KEYWORDS = new Set([
  'ultra-condensed', 'extra-condensed', 'condensed', 'semi-condensed',
  'semi-expanded', 'expanded', 'extra-expanded', 'ultra-expanded',
]);
// <absolute-size> | <relative-size>, css-fonts-4 §3.5. NOTE `small-caps` is a
// distinct token from `small`, so the variant keyword can never be mistaken
// for a size — tokens are compared whole, never by prefix.
const FONT_SH_SIZE_KEYWORDS = new Set([
  'xx-small', 'x-small', 'small', 'medium', 'large', 'x-large', 'xx-large',
  'xxx-large', 'larger', 'smaller',
]);
// A <length> or <percentage>. The unit list is CLOSED on purpose: the
// converter's isSizeValue uses `[a-z%]*`, which happily reads `20deg` as a
// size — harmless there because the angle arm runs first, but this function
// has to refuse unknown tokens rather than guess, so the units are spelled
// out. Font-relative and viewport units are ADMITTED, not resolved: the
// converter emits `null` for them (CLAUDE.md's "null means runtime-dependent"
// row), which is the same honest non-answer the root itself gets.
const FONT_SH_LENGTH_RX =
  /^[+-]?(?:\d+\.?\d*|\.\d+)(?:px|pt|pc|in|cm|mm|q|em|rem|ex|ch|cap|ic|lh|rlh|vw|vh|vi|vb|vmin|vmax|svw|svh|lvw|lvh|dvw|dvh|%)$/i;
// css-values-4 §8.4 <angle>, for the `oblique <angle>` form of font-style.
const FONT_SH_ANGLE_RX = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:deg|grad|rad|turn)$/i;
// A bare <number> in the pre-size run is a numeric font-weight (css-fonts-4
// §3.2 — 1 to 1000). It can never be a font-size: `font-size` has no unitless
// form, which is exactly what makes this position unambiguous.
const FONT_SH_NUMBER_RX = /^\d+\.?\d*$/;

/** Quote- and paren-aware whitespace tokeniser for a `font` value. Mirrors
 *  FontExpander.kt's `tokenize` so `"Lucida Grande"` and `url(a b)` survive
 *  as single tokens and a `/` inside either can never be seen as the
 *  size/line-height delimiter. */
function fontShorthandTokens(value) {
  const out = [];
  let cur = '';
  let quote = null;   // the open quote char, or null
  let depth = 0;      // paren nesting
  for (const ch of value) {
    if ((ch === '"' || ch === "'") && depth === 0) {
      if (quote === null) quote = ch;
      else if (ch === quote) quote = null;
      cur += ch;
    } else if (ch === '(') { depth++; cur += ch; }
    else if (ch === ')') { depth--; cur += ch; }
    else if (/\s/.test(ch) && depth === 0 && quote === null) {
      if (cur) { out.push(cur); cur = ''; }
    } else cur += ch;
  }
  if (cur) out.push(cur);
  return out;
}

/** Re-join a `<font-size> / <line-height>` run the tokeniser split apart.
 *  css-syntax-3 §5 allows whitespace on either side of the delimiter, so
 *  `16px/2`, `16px/ 2`, `16px /2` and `16px / 2` are the same declaration.
 *  Straight port of FontExpander.kt's joinSlashRuns, for the same reason it
 *  exists there: without it `16px / 2 Georgia` reads `/ 2 Georgia` as the
 *  family and confidently reports the wrong line-height. Merging happens on
 *  TOKEN BOUNDARIES only, so a quoted `"Foo/Bar"` family is untouchable. */
function joinFontSlashRuns(tokens) {
  const out = [];
  let i = 0;
  while (i < tokens.length) {
    let t = tokens[i];
    i++;
    // `16px /2` and `16px / 2`: a token STARTING with the delimiter belongs
    // to the size before it.
    if (t.startsWith('/') && out.length > 0) {
      out[out.length - 1] += t;
      // A lone `/` still needs its right-hand side pulled in.
      if (out[out.length - 1].endsWith('/') && i < tokens.length) {
        out[out.length - 1] += tokens[i];
        i++;
      }
      continue;
    }
    // `16px/ 2`: a token ENDING with the delimiter is missing its RHS.
    if (t.endsWith('/') && i < tokens.length) { t += tokens[i]; i++; }
    out.push(t);
  }
  return out;
}

/** True when `t` is a <font-size> the §3.7 grammar accepts in the size slot. */
function isFontShorthandSize(t) {
  const lower = t.toLowerCase();
  return FONT_SH_SIZE_KEYWORDS.has(lower) || FONT_SH_LENGTH_RX.test(lower);
}

/** True when `t` is a <'line-height'> (css-inline-3 §4.1): the `normal`
 *  keyword, a unitless <number>, a <length> or a <percentage>. */
function isFontShorthandLineHeight(t) {
  const lower = t.toLowerCase();
  return lower === 'normal' || FONT_SH_NUMBER_RX.test(lower)
      || FONT_SH_LENGTH_RX.test(lower);
}

/**
 * Derive the INHERITED longhands a body-root `font` shorthand declares, per
 * the css-fonts-4 §3.7 grammar
 *
 *   [ [ <'font-style'> || <font-variant-css2> || <'font-weight'> ||
 *       <font-width-css3> ]? <'font-size'> [ / <'line-height'> ]?
 *     <'font-family'># ] | <system-family-name>
 *
 * Returns a fresh bag of longhands, or `null` for every form listed in the
 * REFUSED paragraph of the banner above. Only longhands that are members of
 * ROOT_INHERITED_TRIGGER_PROPS are emitted — `font-variant` and
 * `font-stretch` are PARSED (they have to be, or the size slot cannot be
 * located) and then dropped, because nothing downstream would bake them and
 * emitting a key no consumer reads is how dead wire shape accumulates.
 *
 * The css-fonts-4 §4.3 `line-height` RESET is emitted: a shorthand with no
 * `/<line-height>` component sets line-height to `normal`, and saying so is
 * the whole difference between the ref (whose body resets, so its text sits
 * in the FACE's natural line box) and our capture (whose sibling text
 * components otherwise inherit the harness's `line-height: 1.25` calibration
 * from index.html / capture-browser-ref.mjs REF_LINE_HEIGHT). Omitting it
 * would also put the children in direct disagreement with their own
 * body-root, which the converter's FontExpander DOES reset.
 *
 * Exported for the unit pins — the refusal set is the correctness story.
 */
export function parseFontShorthand(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const lower = trimmed.toLowerCase();
  // Refusal 1+2: system families and the CSS-wide keywords, whole-value forms.
  if (FONT_SH_SYSTEM_FAMILIES.has(lower)) return null;
  if (FONT_SH_CSS_WIDE.has(lower)) return null;
  // Refusal 3: var() anywhere. A substitution we cannot perform makes every
  // slot after it unknowable, including which token IS the size.
  if (/(^|[^\w-])var\s*\(/i.test(trimmed)) return null;

  const tokens = joinFontSlashRuns(fontShorthandTokens(trimmed));
  if (tokens.length < 2) return null;   // §3.7 needs at least <size> <family>

  const out = {};
  let i = 0;
  let seenStyle = false, seenVariant = false, seenWeight = false, seenStretch = false;
  // ── the optional pre-size run ──────────────────────────────────────────
  while (i < tokens.length) {
    const raw = tokens[i];
    const t = raw.toLowerCase();
    // The size ENDS the run. Checked first so a size keyword can never be
    // mistaken for a modifier (they are disjoint sets, but the order makes
    // that independent of the sets staying disjoint).
    if (isFontShorthandSize(t.split('/')[0])) break;
    if (t === 'oblique' && !seenStyle) {
      seenStyle = true;
      // `oblique <angle>` (css-fonts-4 §3.4) — the angle is part of the value.
      if (i + 1 < tokens.length && FONT_SH_ANGLE_RX.test(tokens[i + 1])) {
        out['font-style'] = `${raw} ${tokens[i + 1]}`;
        i += 2;
      } else {
        out['font-style'] = raw;
        i += 1;
      }
      continue;
    }
    if (t === 'normal') {
      // Legal in all four slots and identifying none of them. Consumed and
      // dropped: `normal` is the initial value of every slot it could fill,
      // so emitting it would add a longhand that changes nothing.
      i += 1;
      continue;
    }
    if (FONT_SH_STYLE_KEYWORDS.has(t) && !seenStyle) {
      seenStyle = true; out['font-style'] = raw; i += 1; continue;
    }
    if (FONT_SH_WEIGHT_KEYWORDS.has(t) && !seenWeight) {
      seenWeight = true; out['font-weight'] = raw; i += 1; continue;
    }
    if (FONT_SH_NUMBER_RX.test(t) && !seenWeight) {
      // Numeric font-weight, css-fonts-4 §3.2 (1–1000). Out of range is not a
      // weight and not a size either, so it fails the whole parse below.
      const n = Number(t);
      if (!(n >= 1 && n <= 1000)) return null;
      seenWeight = true; out['font-weight'] = raw; i += 1; continue;
    }
    if (FONT_SH_VARIANT_KEYWORDS.has(t) && !seenVariant) {
      // Parsed to advance past it; NOT emitted (not a trigger property).
      seenVariant = true; i += 1; continue;
    }
    if (FONT_SH_STRETCH_KEYWORDS.has(t) && !seenStretch) {
      seenStretch = true; i += 1; continue;    // parsed, not emitted — as above
    }
    // Refusal 4: a token before the size that is none of the above. We do not
    // know what it means, so we do not know where the size is either.
    return null;
  }

  // ── the size, and the optional /line-height riding on it ───────────────
  if (i >= tokens.length) return null;                 // no size at all
  const sizeToken = tokens[i];
  i += 1;
  const slash = sizeToken.indexOf('/');
  const sizeRaw = slash < 0 ? sizeToken : sizeToken.slice(0, slash);
  const lhRaw   = slash < 0 ? null      : sizeToken.slice(slash + 1);
  if (!isFontShorthandSize(sizeRaw)) return null;
  out['font-size'] = sizeRaw;
  if (lhRaw !== null) {
    // A slash with nothing after it, or a value that is not a line-height,
    // makes the declaration invalid — dropped whole (CSS 2.2 §4.2).
    if (!lhRaw || !isFontShorthandLineHeight(lhRaw)) return null;
    out['line-height'] = lhRaw;
  } else {
    // css-fonts-4 §4.3: the shorthand RESETS line-height when the component
    // is absent. See the doc comment for why this matters on the WPT stage.
    out['line-height'] = 'normal';
  }

  // ── the family list — everything left, in the author's own spelling ────
  if (i >= tokens.length) return null;                 // no family
  // Re-joined with single spaces: the tokeniser only ever split on top-level
  // whitespace runs, so this reproduces the author's list (quoted names and
  // the `,` separators ride inside the tokens) modulo whitespace collapsing,
  // which is not significant between CSS component values.
  out['font-family'] = tokens.slice(i).join(' ');
  return out;
}

/**
 * `rootProps` as the bake should READ it: the author's own keys, plus any
 * longhands derivable from a `font` shorthand it declares.
 *
 * MERGE ORDER — the author's explicit longhand WINS over the derived one.
 * The flattened root bag has lost declaration order across rules
 * (rootScopeProps' `Object.assign` keeps a key's FIRST insertion position but
 * its LAST value), so "later declaration wins" is not recoverable here and
 * guessing it could silently flip an existing fixture's baked value. Letting
 * the explicit longhand win is the choice that cannot regress anything: every
 * root that already declared the longhand bakes exactly the byte it baked
 * before this lane, and the derivation only ever FILLS keys that were absent.
 *
 * Returns `rootProps` itself (not a copy) when there is nothing to derive, so
 * the overwhelmingly common no-`font` case allocates nothing.
 */
export function rootPropsWithFontShorthand(rootProps) {
  if (!rootProps || rootProps.font === undefined) return rootProps;
  const derived = parseFontShorthand(rootProps.font);
  if (!derived) return rootProps;            // refused — identical behaviour
  return { ...derived, ...rootProps };       // author longhands overwrite
}

/**
 * The bake-down (wave-30 A4, reworked after the gate finding). Returns the
 * subset of `rootProps`' trigger properties to copy onto ONE top-level
 * body-child bag, or `null` when the child needs nothing.
 *
 * AUTHOR BEATS INHERITED — css-cascade-4 §7.3: inheritance is the step that
 * runs when the cascade produced no value for the element, so a child's OWN
 * declaration of the same property always wins. Two guards implement that:
 *   1. the child declares the longhand itself;
 *   2. the child declares a shorthand that covers the longhand (see
 *      INHERITED_COVERING_SHORTHANDS / ALL_SHORTHAND_EXEMPT) — the longhand
 *      is set even though its name never appears in the bag.
 * Non-destructive: the returned object is fresh and `childProps` is only
 * read, so the caller decides ordering and marking.
 *
 * Exported for the unit pins (the guards are the whole correctness story).
 */
export function rootInheritedBakeProps(rootProps, childProps) {
  if (!rootProps || !childProps) return null;
  // wave-35 lane FX: the root's `font` shorthand contributes its longhands
  // HERE, not at the copy site, so every derived longhand meets the identical
  // per-property guards below. The child's own `font` shorthand keeps beating
  // all five of them through INHERITED_COVERING_SHORTHANDS — a child that
  // declares `font` has already spoken about size, family, weight, style and
  // line-height, whether or not it named them.
  const effectiveRoot = rootPropsWithFontShorthand(rootProps);
  const out = {};
  for (const prop of ROOT_INHERITED_TRIGGER_PROPS) {
    const value = effectiveRoot[prop];
    if (value === undefined) continue;            // root never declared it
    if (childProps[prop] !== undefined) continue; // guard 1: own longhand
    // guard 2: a shorthand the child declares already covers this longhand.
    const covers = INHERITED_COVERING_SHORTHANDS[prop] ?? [];
    if (covers.some((sh) => childProps[sh] !== undefined)) continue;
    if (!ALL_SHORTHAND_EXEMPT.has(prop) && childProps.all !== undefined) continue;
    out[prop] = value;
  }
  return Object.keys(out).length > 0 ? out : null;
}

// The LOUD marker. A component wearing it carries a value its own source
// element never declared — the root's, delivered by copy because the fixture
// wire has no body→child inheritance edge for an unslotted body. Reading a
// baked `font-size: 50px` as an author declaration would be wrong, so the
// reason says where it came from.
const ROOT_INHERITED_REASON = 'body-inherited-baked';

// ── wave-30 A7: UA LINK STYLING ────────────────────────────────────────────
//
// HTML Rendering §15.5.2 gives every hyperlink a UA-origin rule:
//   :link  { color: #0000EE; text-decoration: underline; cursor: pointer }
// The extractor models only AUTHOR declarations, so an `<a href>` with no
// author `color` shipped with an EMPTY properties bag and every platform
// painted it in the inherited/default black with no underline. That is a
// two-way visual miss against any browser-rendered ref containing a link.
//
// WHY IT IS SAFE TO BAKE, and why it is UA ORIGIN and not just "a default":
// CSS Cascade 5 §6.1 orders UA-origin declarations BELOW author-origin ones,
// so an author rule on the link must win — hence the presence guards below,
// which fill a key only when the element's own resolved bag has none. And
// because this is a rule on the ELEMENT (not inheritance), it correctly
// beats an inherited `color` from an ancestor: `body { color: green }` with
// `<a href>` still paints blue in every browser, which is exactly what an
// unconditional-on-absence fill reproduces.
//
// SCOPE — `<a>` WITH an `href` attribute, because HTML §4.6.1 makes href the
// thing that turns an <a> into a hyperlink, and Selectors-4 §11.1 matches
// `:link` on precisely that. A bare `<a name=…>` anchor gets nothing.
//
// DELIBERATELY NOT MODELLED — `:visited`. Selectors-4 §11.1 and the privacy
// carve-out around it mean a visited link paints #551A8B, and NOTHING in a
// static HTML source says whether the user has visited a URL; the browser
// refs were rasterised in a fresh profile, but "fresh profile" is an
// assumption about the capture environment, not a fact in the document. We
// bake the :link colour only and say so out loud via the marker below, so a
// residual purple-vs-blue divergence stays visible as OUR approximation
// rather than being silently papered over.
const UA_LINK_PROPS = {
  // HTML Rendering §15.5.2's `-webkit-link` system colour resolves to
  // #0000EE in the Chromium the browser refs were rasterised with.
  color: '#0000EE',
  // The LONGHAND, not the `text-decoration` shorthand: the shorthand also
  // resets text-decoration-color/style/thickness to their initials, which
  // would silently overwrite author longhands that the guard below cannot
  // see individually.
  'text-decoration-line': 'underline',
};

// Author declarations that mean "the UA default is not the used value here".
// Keyed per UA property so a link that declares only a colour still gets the
// underline (and vice versa) — the cascade is per-property, not per-rule.
const UA_LINK_GUARDS = {
  // Any spelling that sets the element's own colour.
  color: ['color'],
  // Both the longhand and the shorthand that contains it, plus the -webkit-
  // alias the corpus authors beside the standard one.
  'text-decoration-line': [
    'text-decoration-line', 'text-decoration', '-webkit-text-decoration',
  ],
};

// The LOUD marker. Named for what it is — a UA rule we baked into an
// author-origin fixture — so a reader of `_lossyReasons` can tell this
// component's blue from a declared blue, and so the :visited gap above has
// a visible home.
const UA_LINK_REASON = 'ua-link-styling-baked';

// Link-state pseudo-classes (Selectors-4 §11) — ANY of these in a selector
// means the rule is about hyperlinks, whether or not it also names the tag.
// `(?![\w-])` stops `:link` from swallowing a hypothetical `:linkish`.
const UA_LINK_STATE_PSEUDO_RX =
  /:(?:link|visited|any-link|local-link|target-current)(?![\w-])/i;
// A bare `a` TAG at the head of a compound: start-of-selector, or right after
// a combinator / comma / functional-pseudo paren. The leading-char class
// deliberately excludes `.` and `#`, so `.a` (class "a") and `#a` (id "a")
// do NOT read as the anchor element.
const UA_LINK_BARE_A_RX = /(?:^|[\s>+~,(])a(?![\w-])/i;

/**
 * wave-30 fix-T1: could this selector text style a hyperlink? Deliberately
 * TEXTUAL, not parsed — it is asked only about rules the matcher already
 * REFUSED to parse (see uaLinkSuppressedProps), so a structured answer is
 * not available and a conservative over-match is the safe direction.
 * Exported so the unit tests can pin both halves of the test.
 */
export function selectorCouldTargetLink(selector) {
  const sel = String(selector ?? '');
  return UA_LINK_STATE_PSEUDO_RX.test(sel) || UA_LINK_BARE_A_RX.test(sel);
}

/**
 * wave-30 fix-T1: the UA link properties this stylesheet forbids us to bake.
 *
 * The author-wins guard inside uaLinkProps consults the element's RESOLVED
 * bag — which by construction contains only rules the matcher could apply.
 * Every rule the matcher DROPPED (countUnsupportedRules counts exactly
 * these) is invisible there, so a sheet whose only colour declaration for
 * links is `a:link { color: red }` (css-color/color-mix-currentcolor-visited)
 * or `:visited, :link { color: black }` (selectors/is-where-visited) looked
 * to A7 like a sheet that declared nothing — and got UA `#0000EE` baked over
 * an author colour. That is a GUARANTEED-wrong pixel, not an approximation.
 *
 * So: a dropped rule whose selector could target links (link-state pseudo or
 * a bare `a` compound) and which declares one of the UA properties SUPPRESSES
 * that property's bake document-wide. Not "guess the author's value" — we
 * cannot resolve a selector we could not parse — but "decline to invent a UA
 * value we know is contradicted". Status quo ante (no bake, so the renderer
 * paints inherited/initial) is the honest fallback.
 *
 * Returns a Set of UA_LINK_PROPS keys. Document-scoped, not per element: a
 * dropped selector's subject set is exactly what we failed to compute.
 *
 * MEASURED blast radius over the pinned corpus (33,643 documents, 499 with an
 * `<a href>` and a <style> block): 52 documents suppress at least one UA
 * property — 50 `color` (the whole `:visited` family: css-cascade
 * all-prop-*-visited, css-color color-mix/relative-currentcolor-visited,
 * css-pseudo selection-link-001..003, the 13 css-overflow scroll-target-group
 * tests, …) and 2 `text-decoration-line`.
 *
 * STATED COST, not hidden: those 2 are css-text-decor/invalidation/
 * text-decoration-thickness{,-ref}, whose dropped `:link { text-decoration:
 * underline }` declares the SAME underline the UA would have baked — so for
 * them the decline loses a line the old bake got right by coincidence. We
 * take that trade knowingly: matching the value would mean pretending we
 * resolved a selector we could not parse, and the 50 colour cases it repairs
 * were each a guaranteed-wrong pixel.
 */
export function uaLinkSuppressedProps(rules) {
  const out = new Set();
  for (const r of rules ?? []) {
    // The SAME droppedness test countUnsupportedRules uses — one definition
    // of "the matcher never applies this rule", so the two can never drift.
    const chain = splitSelectorChain(r.selector);
    const dropped = !chain || chain.compounds.length === 0
      || chain.compounds.some((c) => parseCompound(c).unsupported);
    if (!dropped) continue;
    if (!selectorCouldTargetLink(r.selector)) continue;
    // Per-property, matching the per-property cascade: a dropped
    // `a:link { color: red }` must not stop the UA underline from baking.
    for (const [key, spellings] of Object.entries(UA_LINK_GUARDS)) {
      if (spellings.some((g) => r.props?.[g] !== undefined)) out.add(key);
    }
  }
  return out;
}

/**
 * wave-30 A7: the UA-origin declarations to fold into a hyperlink's bag, or
 * null when the element is not a hyperlink / the author already decided
 * every one of them. `props` is the element's RESOLVED author bag (matched
 * rules + inline style), which is what the cascade compares against.
 * `suppressed` (wave-30 fix-T1) is the uaLinkSuppressedProps set for the
 * sheet — UA properties contradicted by a rule the matcher dropped, which
 * the resolved bag can never reveal. Exported so the unit tests can pin the
 * fill, the guards and the suppression.
 */
export function uaLinkProps(tag, attrs, props, suppressed = null) {
  // Hyperlink identity per HTML §4.6.1 / Selectors-4 §11.1 — an <a> with an
  // href attribute, whatever its value (`href=""` is still a hyperlink).
  if (tag !== 'a' || attrs?.href === undefined) return null;
  const out = {};
  for (const [key, value] of Object.entries(UA_LINK_PROPS)) {
    // Author-origin beats UA-origin (CSS Cascade 5 §6.1): skip any UA
    // property the element already decides through ANY of its spellings.
    if (UA_LINK_GUARDS[key].some((g) => props?.[g] !== undefined)) continue;
    // …and skip any the sheet decides through a rule we could not parse.
    if (suppressed?.has(key)) continue;
    out[key] = value;
  }
  return Object.keys(out).length > 0 ? out : null;
}

// ── wave-36 M6: HTML TABLE PRESENTATIONAL ATTRIBUTES ────────────────────────
//
// THE GAP. Eight CSS2/pagination tests (row-page-break-inside-avoid-1/-2,
// rowgroup-page-break-inside-avoid-4..8, table-page-break-inside-avoid-5)
// fail the web-ref diff at ssim 0.899–0.906 with NO veto firing, and the
// wave-35 classification army root-caused all eight to ONE line of markup:
// `<table border="1">`. Their blue cells are pixel-identical to the ref
// (27,531 blue px on both sides of rowgroup-5) — the whole diff is the
// table's 1px frame, the cells' 1px rules, and a 3px origin shift.
//
// Measured decomposition (rowgroup-4, ref column x=16… at y=25):
//   x=16 grey  → the table's `border-left: 1px outset`
//   x=17,18    → `border-spacing: 2px`   (the browser UA sheet; we ALREADY
//                have this, because the web runtime renders a real <table>)
//   x=19 grey  → the cell's `border-left: 1px inset`
//   x=20 white → the cell's `padding-left: 1px`
//   x=21 blue  → content
// Our capture paints blue at x=18 — border-spacing only. Three of the five
// pixels are missing, and all three come from HTML's table presentational
// attribute machinery (HTML Standard §15.3.3 "Tables"), which the extractor
// never modelled: `meta.attrs` is emitted for form/widget tags only, so
// `border=` never reaches the DOM the web runtime builds.
//
// WHY THE HARNESS RESET CANNOT COVER IT. index.html's composed-mode rule
// already says `padding: revert` on every IR element precisely to restore UA
// padding — and it does NOT restore the cell's 1px. Probed in the ref's own
// Chromium: `td { padding: revert }` computes to 0px while a bare `<td>`
// computes to 1px, because Blink's cell padding is a PRESENTATION-ATTRIBUTE
// style (HTMLTableCellElement::AdditionalPresentationAttributeStyle reading
// the owner table's cellpadding, defaulting to 1) and not a UA-stylesheet
// declaration `revert` can roll back to. The only honest fix is to put the
// mapping in the IR, which is also the only fix the two NATIVE runtimes can
// ever see — they have no browser UA sheet at all.
//
// THE MAPPING, verified against the ref's own headless Chromium rather than
// read off the spec prose (probe: _diag36/M6/probe-attrs.mjs):
//   table[border]      → border-<side>-width: <n>px, border-<side>-style: outset
//   its cells          → border-<side>-width: 1px,   border-<side>-style: inset
//   table[cellpadding] → cell padding: <n>px      (DEFAULT 1px when absent)
//   table[cellspacing] → border-spacing: <n>px    (default 2px = UA sheet,
//                                                  see the scope cut below)
// `<n>` follows Blink's HTMLTableElement::ParseBorderWidthAttribute: the HTML
// "rules for parsing non-negative integers", with the attribute's PRESENCE
// (not its parseability) deciding — `border=""`, `border="border"`,
// `border="yes"`, `border="-3"` and `border="1.9"` all render 1px, `border="0"`
// renders none, `border="3px"` renders 3px. Every one of those spellings is in
// the corpus and every one is pinned in the unit tests.
//
// SCOPE CUTS — documented, never silent:
//   - `rules=` / `frame=` (3 corpus tests) additionally set
//     `border-collapse: collapse` and `border-style: hidden` on the table plus
//     per-group internal borders. Modelling half of that would make those
//     tests WORSE, so a table carrying either attribute declines the WHOLE
//     bake and is marked with the unmodelled reason below.
//   - `bordercolor=` turns outset/inset into SOLID in that colour. Zero
//     corpus occurrences, so it is not modelled; a table carrying it takes
//     the same decline path as rules/frame.
//   - the UA `border-spacing: 2px` DEFAULT is not emitted, only the
//     `cellspacing` override. On web the browser supplies it (the universal
//     reset touches margin/padding/box-sizing only), so emitting it would be
//     a no-op there while widening the corpus-wide byte diff; the natives
//     lose it, which is a known native-only gap, not a web one.
//   - the cascade guard is PER BOX and ALL-OR-NOTHING per family (any author
//     padding spelling on a cell suppresses the whole padding fill, any
//     author border spelling suppresses the whole border fill). Presentation
//     hints lose to every author declaration (HTML §15.2), so erring toward
//     "do not bake" is the direction that can only under-claim.
//
// The bake is UA-origin-shaped exactly like wave-30's UA_LINK_PROPS: folded
// only where the author bag is silent, and marked LOUDLY so `_lossyReasons`
// says where the pixels came from.

/** The bake fired on this component (table frame, cell rules, cell padding
 *  and/or border-spacing). Provenance only — the fixture is MORE faithful. */
const HTML_TABLE_PRESENTATION_REASON = 'html-table-presentation-baked';
/** The table carries a presentational attribute this module does not model
 *  (`rules` / `frame` / `bordercolor`), so NOTHING was baked for it or its
 *  cells. The loud half of the scope cut above. */
const HTML_TABLE_PRESENTATION_UNMODELLED_REASON = 'html-table-presentation-unmodelled';
export { HTML_TABLE_PRESENTATION_REASON, HTML_TABLE_PRESENTATION_UNMODELLED_REASON };

/** Attributes whose table-level effect (border-collapse / border-style:hidden
 *  / per-group rules / solid recolouring) this module does not model. Their
 *  presence disables the bake for the whole table subtree. */
const TABLE_UNMODELLED_ATTRS = ['rules', 'frame', 'bordercolor'];

/** Blink clamps a presentational border width at the LayoutUnit ceiling —
 *  `border="2026722966"` (css-tables/html-to-css-mapping-2) computes to
 *  33554400px, not two billion. Mirrored so one absurd corpus value cannot
 *  hand the converter a number no layout engine would honour. */
const TABLE_BORDER_MAX_PX = 33554400;

/** The four physical sides, in the order the CSS shorthand names them. */
const TABLE_BORDER_SIDES = ['top', 'right', 'bottom', 'left'];

/** Every author spelling that decides a box's PADDING. Any one of them on a
 *  cell suppresses the whole cellpadding fill (see the all-or-nothing scope
 *  cut). Logical spellings included: we cannot know the writing mode here,
 *  and over-guarding only ever declines to bake. */
const TABLE_PADDING_GUARDS = [
  'padding',
  'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
  'padding-block', 'padding-block-start', 'padding-block-end',
  'padding-inline', 'padding-inline-start', 'padding-inline-end',
];

/** Every author spelling that decides a box's BORDER width or style. Any one
 *  of them suppresses the whole border fill on that box. `border-color` is
 *  deliberately absent: a colour alone leaves the UA width/style in force. */
const TABLE_BORDER_GUARDS = [
  'border', 'border-width', 'border-style',
  'border-top', 'border-right', 'border-bottom', 'border-left',
  'border-block', 'border-block-start', 'border-block-end',
  'border-inline', 'border-inline-start', 'border-inline-end',
  ...TABLE_BORDER_SIDES.flatMap((s) => [`border-${s}-width`, `border-${s}-style`]),
  'border-block-start-width', 'border-block-start-style',
  'border-block-end-width', 'border-block-end-style',
  'border-inline-start-width', 'border-inline-start-style',
  'border-inline-end-width', 'border-inline-end-style',
];

/** Table-internal box tags. Row groups and rows are NOT cells: HTML's
 *  presentational border/padding hints land on `td`/`th` only (Blink:
 *  HTMLTableCellElement::AdditionalPresentationAttributeStyle). */
const TABLE_CELL_TAGS = new Set(['td', 'th']);

/**
 * The HTML "rules for parsing non-negative integers" (HTML §2.4.4.2),
 * reduced to what a presentational attribute needs: skip leading ASCII
 * whitespace, accept an optional `+`, then collect ASCII digits and IGNORE
 * whatever follows (so `3px` → 3 and `1.9` → 1). A leading `-`, a leading
 * non-digit, or an empty string is an ERROR and returns null — the caller
 * decides what an error means for its attribute.
 *
 * Exported so the unit tests can pin each spelling the corpus actually uses.
 */
export function parseHtmlNonNegativeInteger(raw) {
  if (raw === undefined || raw === null) return null;
  // HTML's ASCII whitespace set (tab, LF, FF, CR, space) — NOT \s, which
  // also eats NBSP and would parse a value the browser rejects.
  const s = String(raw).replace(/^[\t\n\f\r ]+/, '');
  const m = /^\+?(\d+)/.exec(s);
  if (!m) return null;
  const n = Number(m[1]);
  return Number.isFinite(n) ? n : null;
}

/**
 * The used `border` presentational width for a `<table>`, in CSS pixels, or
 * 0 when the table draws no presentational frame.
 *
 * Mirrors Blink's HTMLTableElement::ParseBorderWidthAttribute exactly:
 * absent attribute → 0; present-but-unparseable (including the empty string)
 * → 1; parseable → the parsed value, so `border="0"` legitimately means "no
 * border". Clamped at TABLE_BORDER_MAX_PX.
 *
 * Exported for unit pins — this function is the whole difference between the
 * 8 failing pagination tests and their refs.
 */
export function tableBorderAttrWidthPx(attrs) {
  const raw = attrs?.border;
  if (raw === undefined) return 0;              // no attribute → no frame
  const n = parseHtmlNonNegativeInteger(raw);
  if (n === null) return 1;                     // present but junk → 1px
  return Math.min(n, TABLE_BORDER_MAX_PX);
}

/**
 * The used cell padding for a table's cells, in CSS pixels. Blink seeds
 * HTMLTableElement's padding_ at 1 and only a parseable `cellpadding`
 * replaces it, so an absent OR unparseable attribute both mean 1px — which
 * is the pixel `padding: revert` cannot restore (see the banner).
 */
export function tableCellPaddingPx(attrs) {
  const n = parseHtmlNonNegativeInteger(attrs?.cellpadding);
  return n === null ? 1 : n;
}

/**
 * The nearest ancestor `<table>` of a node, given the walker's ancestor chain
 * (document order, immediate parent LAST), or null. Nearest-ancestor rather
 * than the chain head so a cell of an INNER table takes the inner table's
 * attributes — the same subject the UA rules `table[border] > … > td` select.
 */
export function nearestTableAncestor(ancestors) {
  for (let i = (ancestors?.length ?? 0) - 1; i >= 0; i--) {
    if (ancestors[i]?.tag === 'table') return ancestors[i];
  }
  return null;
}

/**
 * wave-36 M6: the HTML §15.3.3 presentational-attribute declarations to fold
 * into a table box's bag, or null when nothing applies.
 *
 * @param {string} tag        the element's tag name (already lowercased)
 * @param {object} attrs      the element's own attribute bag
 * @param {Array<{tag,attrs}>|null} ancestors  walker chain, parent LAST
 * @param {object} props      the element's RESOLVED author bag (matched
 *                            rules + inline style) — the cascade guard
 * @returns {{props: object, unmodelled: boolean}|null}
 *          `props` are the declarations to assign; `unmodelled` is true when
 *          an owning table carries rules/frame/bordercolor and the bake
 *          declined (in which case `props` is empty).
 */
export function htmlTablePresentationProps(tag, attrs, ancestors, props) {
  const isCell = TABLE_CELL_TAGS.has(tag);
  if (tag !== 'table' && !isCell) return null;
  // The table whose attributes decide this box: itself, or (for a cell) the
  // nearest enclosing table. A cell with no table ancestor in the chain —
  // the walker's depth cut, or a stray `<td>` — gets nothing rather than a
  // guessed default, because we cannot see the cellpadding that would apply.
  const table = tag === 'table' ? { tag, attrs } : nearestTableAncestor(ancestors);
  if (!table) return null;
  // Scope cut, LOUD: a presentational attribute we do not model changes the
  // table's border MODEL (collapse / hidden / solid recolour), so baking the
  // half we do model would be worse than baking nothing.
  if (TABLE_UNMODELLED_ATTRS.some((a) => table.attrs?.[a] !== undefined)) {
    return { props: {}, unmodelled: true };
  }
  const out = {};
  const borderPx = tableBorderAttrWidthPx(table.attrs);
  // ── the frame / rules half ────────────────────────────────────────────
  // Both halves are gated on the SAME non-zero table border, exactly like the
  // paired UA rules: `table[border]` outset on the table, `1px inset` on its
  // cells. A cell never carries a width other than 1px, whatever `border=`
  // said — that asymmetry is in the spec's own rule text.
  if (borderPx > 0 && !TABLE_BORDER_GUARDS.some((g) => props?.[g] !== undefined)) {
    const width = isCell ? 1 : borderPx;
    const style = isCell ? 'inset' : 'outset';
    for (const side of TABLE_BORDER_SIDES) {
      out[`border-${side}-width`] = `${width}px`;
      out[`border-${side}-style`] = style;
    }
  }
  // ── the cellpadding half (cells only) ─────────────────────────────────
  // Unconditional on the border attribute: a bare `<table>` still gives its
  // cells 1px, which is the pixel our captures have been missing corpus-wide.
  if (isCell && !TABLE_PADDING_GUARDS.some((g) => props?.[g] !== undefined)) {
    const pad = `${tableCellPaddingPx(table.attrs)}px`;
    out['padding-top'] = pad;
    out['padding-right'] = pad;
    out['padding-bottom'] = pad;
    out['padding-left'] = pad;
  }
  // ── the cellspacing half (table only) ─────────────────────────────────
  // Only the OVERRIDE is emitted; the 2px UA default is the browser's job on
  // web and a stated native gap (see the scope cuts).
  if (tag === 'table' && props?.['border-spacing'] === undefined) {
    const spacing = parseHtmlNonNegativeInteger(table.attrs?.cellspacing);
    if (spacing !== null) out['border-spacing'] = `${spacing}px`;
  }
  return Object.keys(out).length > 0 ? { props: out, unmodelled: false } : null;
}

// wave-13 KEYFRAMES-SAMPLER: `keyframes` is the parseKeyframes() map for the
// same stylesheet the `rules` came from (parseCss skips @-rules, so the two
// are complementary views of one sheet). Null/omitted = sampling disabled —
// the legacy byte-identical path for callers without keyframes (unit tests).
export function buildComponents(cleaned, rules, idPrefix, ctx = null, keyframes = null) {
  const components = {};
  let lossyOverall = false;
  const lossyReasonsOverall = new Set();
  // swarm-003 Bug 3: build a `:defined`-aware ctx on demand. Callers may
  // pre-supply one (so they can inject test-defined tags), otherwise we
  // harvest customElements.define() calls from the source HTML here.
  // The ctx is empty `{}` when neither a caller-provided one nor any
  // <script> registration exists, which is the byte-identical-fixture
  // path for the existing visual-test corpus.
  const baseCtx = ctx ?? { definedTags: collectDefinedTags(cleaned) };
  // The document-element directionality rung for `:dir()` (see
  // resolveDirectionality rung 2b): `<html dir>` / `<body dir>` are real
  // ancestors of every component, but the walker's chain starts INSIDE body,
  // so the fact has to travel on the ctx. A caller-supplied `documentDir`
  // wins (unit-test injection); otherwise it is read from the source here.
  // Copied, never mutated in place, so a caller's ctx object is untouched.
  const withDocDir = baseCtx.documentDir !== undefined ? baseCtx
    : { ...baseCtx, documentDir: documentDirectionality(cleaned) };
  // wave-37 lane W4 — the same ctx rung for the LANG wire. `<html lang>` /
  // `<body lang>` are ancestors of every component but sit outside the
  // body-subtree chain the walker builds, so resolveLanguage's last rung has
  // to be handed down here. Caller-supplied wins (unit-test injection); the
  // copy keeps a caller's ctx object untouched, exactly like the dir rung.
  const effectiveCtx = withDocDir.documentLang !== undefined ? withDocDir
    : { ...withDocDir, documentLang: documentLanguage(cleaned) };
  // wave-12 EXTRACTOR-INLINE: merge context for pure-inline run merging.
  // styledTags guards the merge — any tag a rule directly targets keeps
  // its child-component path so the declarations under test survive (see
  // collectStyledTags). Merging is ALWAYS on in production extraction;
  // only legacy direct calls to the walkers (unit tests) run without it.
  const mergeCtx = { styledTags: collectStyledTags(rules) };

  // wave-30 fix-T1: computed ONCE per sheet (it is a fact about the rule
  // list, not about any element) and handed to every uaLinkProps call below.
  const uaLinkSuppressed = uaLinkSuppressedProps(rules);

  // wave-36 THE WIDGET-APPEARANCE BAKE: the recognised generated script's
  // mutated element set, expressed as synthetic marker RULES so the real
  // selector matcher decides which elements it hit (see the module banner).
  // Computed once per document; `[]` for everything without the idiom, which
  // is the short-circuit that keeps the rest of the corpus byte-identical.
  const appearanceDisablingRules = widgetAppearanceBake.appearanceDisablingRules(cleaned);

  // Bug 3 + 4: body-root component for body-scope CSS. Stays as a flat
  // top-level entry (it represents <body> itself). Where the body's element
  // children land depends on the slotting trigger (shouldSlotBodyChildren —
  // see the decision block above buildComponents):
  //  - body declaring no nonzero absolute height (the overwhelmingly common
  //    case): children stay the __0/__1/... SIBLINGS in the components map
  //    (byte-for-byte the legacy shape — every renderer iterates the map at
  //    the top level and an unsized body-root occupies ~no flow space). If
  //    the root ALSO declares an inherited trigger property, each sibling
  //    receives that value by the wave-30 A4 bake-down (rootInheritedBakeProps
  //    — a value copy, not a shape change);
  //  - body WITH an explicit nonzero absolute height (wave-17): children nest
  //    under __body's `children` map so they stack from the body's top INSIDE
  //    its painted area, matching the real page instead of below a sized
  //    block.
  // The root bag itself is never rewritten by either path — the composed
  // canvases read background/padding straight off it.
  const root = propsForBodyRoot(rules);

  // wave-39 lane A5 — THE dir-ATTRIBUTE BODY ROOT. `<body dir=rtl>` /
  // `<html dir=rtl>` is HTML §15.3.4's presentational hint for `direction`,
  // and this is the ONE element the tree walker never visits, so the hint has
  // to be applied here or nowhere. Runs immediately after propsForBodyRoot so
  // every consumer below — the emit gate, shouldSlotBodyChildren, the wave-30
  // A4 inherited bake-down (whose trigger list names `direction`), and the
  // harness's ICB direction hoist — sees ONE bag with no ordering caveat.
  // Returns false (and writes nothing) for every document without an rtl
  // document-element dir attribute, which is what keeps the corpus still; see
  // mintDocumentDirection's banner for the population and the `ltr` refusal.
  const directionMinted = mintDocumentDirection(root, effectiveCtx.documentDir);

  // wave-15 BIDI-EXTRACT part 3: white-space resolver handed to the tree
  // walker via mergeCtx. `white-space` is an INHERITED property (CSS Text
  // §3), so the effective value for an element is its own matched value
  // (rules + inline style, via the same propsForElement the component
  // builder uses — the two can never disagree), else the NEAREST ancestor
  // that sets one, else the body-root bag, else 'normal'. Cost is
  // O(depth × rules) per element only when no own value exists — fine for
  // the ≤5-deep, tens-of-rules WPT corpus. Limits (documented, not
  // silent): ancestors ABOVE the maxDepth cut and `inherit`/`revert`
  // keyword indirection are not modelled; the corpus sets pre directly on
  // the container (`div { white-space: pre }`), which this resolves.
  // wave-22 EX2 B-RC4a: resolver handed to the tree walker so the inline-
  // chain collapse can inspect a descendant's cascaded declarations
  // through the EXACT propsForElement the component builder uses — the two
  // can never disagree about what a wrapper declares, which is what makes
  // "the link declares ONLY text-decoration state" a sound guard rather
  // than a guess. Returns the FULL result (`{props, matchedRules, pseudo}`)
  // so the collapse can also refuse a wrapper carrying ::before/::after
  // generated content, which flattening would silently drop.
  mergeCtx.resolveProps = (tag, attrs, ancestors, pos) =>
    propsForElement(rules, tag, attrs, ancestors, pos, effectiveCtx);

  mergeCtx.resolveWhiteSpace = (tag, attrs, ancestors, pos) => {
    // The element's own cascaded value wins (inline style already merged
    // last inside propsForElement, matching the cascade).
    const own = propsForElement(rules, tag, attrs, ancestors, pos, effectiveCtx)
      .props['white-space'];
    if (own) return own;
    // Walk ancestors nearest-first — CSS inheritance takes the closest
    // ancestor's computed value. Each ancestor's own chain is the slice
    // of the document-order array before it; its pos rides on the entry.
    for (let i = (ancestors?.length ?? 0) - 1; i >= 0; i--) {
      const a = ancestors[i];
      const av = propsForElement(rules, a.tag, a.attrs, ancestors.slice(0, i), a.pos ?? null, effectiveCtx)
        .props['white-space'];
      if (av) return av;
    }
    // No element in the chain sets it — fall back to the body-root scope
    // (body/html/:root/* rules), then the CSS initial value.
    return root.props['white-space'] ?? 'normal';
  };

  // wave-27 A-RC2: the root's pseudo-element buckets (`html::before`, …),
  // now that propsForBodyRoot keeps them OUT of the flat bag. Computed here
  // so the emit gate below can see them.
  const rootPseudo = root.pseudo ?? {};
  const rootPseudoNames = Object.keys(rootPseudo)
    .filter((pe) => Object.keys(rootPseudo[pe] ?? {}).length > 0);
  // wave-27 A-RC2: a document whose ONLY root-scope rule is a pseudo one
  // (`html::before { content:"" }` with no `body {}` rule at all) still needs
  // the synthetic root — it is the box the generated content hangs off. The
  // pre-A-RC2 gate looked at `root.props` alone, which was sound only while
  // pseudo declarations were (wrongly) merged into that same bag.
  // wave-39 lane A5: `|| directionMinted` is the second way this bag can be
  // real. `matchedRules` counts root-scope CSS RULES, and a document whose
  // base direction comes from `<body dir=rtl>` alone has none — yet its bag
  // now holds a genuine declaration (the §15.3.4 hint), so refusing to emit
  // would throw the value away one line after minting it. The second clause
  // is untouched and still holds by construction: a minted direction makes
  // `root.props` non-empty.
  if ((root.matchedRules > 0 || directionMinted) &&
      (Object.keys(root.props).length > 0 || rootPseudoNames.length > 0)) {
    // wave-13 KEYFRAMES-SAMPLER: the measured WPT test (background-color-
    // animation-in-body) animates <body> itself, so the body-root bag is a
    // primary sampling site. Runs BEFORE the lossy scan so the scan sees
    // the baked values (e.g. a %-bearing keyframe endpoint that got lerped
    // away no longer flags 'percentage').
    const rootSampled = bakeSampledAnimation(root.props, keyframes);
    const reasons = lossyReasonsFor(root.props);
    // The LOUD marker: baked fixtures always carry the sampled-animation
    // lossy note (see the sampler section banner).
    if (rootSampled) reasons.push('sampled-animation');
    // wave-25 ATTR BAKE — DELIBERATELY NOT BAKED at root scope. This bag is
    // the MERGE of every html / body / :root / * rule, so it has no single
    // originating element whose attributes attr() could read (css-values-5
    // §7 resolves against one element, not a scope). Rather than guess an
    // element, we leave the declaration verbatim and say so out loud.
    if (Object.values(root.props).some(hasAttrFunction)) {
      reasons.push(ATTR_UNRESOLVED_REASON);
    }
    if (reasons.length) {
      lossyOverall = true;
      reasons.forEach((r) => lossyReasonsOverall.add(r));
    }
    const cmp = { properties: root.props };
    if (reasons.length) {
      cmp._lossy = true;
      cmp._lossyReasons = reasons;
    }
    // wave-27 A-RC2 — the root's GENERATED boxes. Same `_pseudo` wire shape
    // the per-element path below emits (`{ before?: { properties, _lossy?,
    // _lossyReasons? } }`), so the converter's `_pseudo` → v2 `pseudos`
    // rename and all three renderers' pseudo-span paths consume the root's
    // generated content through the EXACT code that already renders every
    // `div::before` — nothing new downstream.
    //
    // The per-bag bakes deliberately mirror the ROOT bag above, not the
    // per-element bag below:
    //   * sampled-animation: yes — a `html::before` can carry the same
    //     time-stable negative-delay animation the root bag samples;
    //   * attr(): NOT baked, reported. css-values-5 §7 resolves attr()
    //     against the pseudo-element's ORIGINATING element, and this scope
    //     is a MERGE of html/body/:root/* rules with no single originating
    //     element — the identical reason the root bag refuses to bake it;
    //   * sibling-index(): not baked, for the same no-single-element reason
    //     (the root bag never bakes it either).
    const pseudoOut = {};
    for (const peName of rootPseudoNames) {
      const peProps = rootPseudo[peName];
      // Sample first so the lossy scan sees baked values (root-bag order).
      const peSampled = bakeSampledAnimation(peProps, keyframes);
      const peReasons = lossyReasonsFor(peProps);
      if (peSampled) peReasons.push('sampled-animation');
      if (Object.values(peProps).some(hasAttrFunction)) {
        peReasons.push(ATTR_UNRESOLVED_REASON);
      }
      if (peReasons.length) {
        lossyOverall = true;
        peReasons.forEach((x) => lossyReasonsOverall.add(x));
      }
      const peEntry = { properties: peProps };
      if (peReasons.length) {
        peEntry._lossy = true;
        peEntry._lossyReasons = peReasons;
      }
      pseudoOut[peName] = peEntry;
    }
    // Omit-when-empty, exactly like the per-element path: a root with no
    // generated content emits NO `_pseudo` key, so every pre-A-RC2 fixture
    // without root pseudo rules stays byte-identical.
    if (Object.keys(pseudoOut).length > 0) cmp._pseudo = pseudoOut;
    // Tag the synthetic root so downstream consumers can recognise it.
    cmp._role = 'body-root';
    // wave-37 lane W4 — the body-root IS the `<body>` element, so its computed
    // content language is the document chain's own answer (there is no nearer
    // declaration to find). Emitted for the same reason the per-element path
    // emits it: under the wave-17 SLOTTING arm the body's children are real
    // descendants of this component, and a renderer that puts `lang` on the
    // host is then the one thing that makes the subtree shape correctly.
    if (effectiveCtx.documentLang) cmp._lang = effectiveCtx.documentLang;
    components[`${idPrefix}__body`] = cmp;
  }

  // wave-17 BODY-HEIGHT SLOTTING: decide ONCE, before any sibling is emitted,
  // whether this document's body children nest under the body-root. Requires
  // BOTH an emitted body-root component (the trigger is moot without a parent
  // to slot into) and the SIZED-body geometry trigger pinned in
  // shouldSlotBodyChildren.
  const bodyCmp = components[`${idPrefix}__body`];
  const slotIntoBody = bodyCmp !== undefined && shouldSlotBodyChildren(root.props);

  // wave-30 A4 (reworked): the ROOT-INHERITANCE bake-down. Same decision
  // point, same once-per-document evaluation, but it delivers VALUES instead
  // of restructuring — see the rootInheritedBakeProps banner for the measured
  // native regressions that ruled out a second slotting arm.
  //
  // Gated on `bodyCmp !== undefined` for the same reason slotting is: a
  // document whose root-scope rules produced no emitted body-root has no
  // inheritance SOURCE in the fixture at all, so there is nothing to hand
  // down and inventing one would widen the blast radius past the trigger.
  //
  // Gated on `!slotIntoBody` because the two repairs are alternatives, not
  // partners: under a SIZED body the children are already real descendants of
  // the body-root, so every runtime's inherited-property merge hands them the
  // root's values through the wire's own parent edge. Baking on top would
  // duplicate the declaration onto the child's own bag, which is NOT the same
  // thing — an own declaration outranks the parent's for any deeper cascade
  // question — so the slotted path is left exactly as wave-17 shipped it.
  const bakeRootInherited = bodyCmp !== undefined && !slotIntoBody
    && bodyDeclaresInheritedProperty(root.props);

  /**
   * Emit one would-be top-level component: as a CHILD of the body-root when
   * slotting is active, else as the legacy top-level sibling. The child
   * entry carries its own `id` field and lives in a map keyed by id —
   * byte-identical to the shape buildNode's childMap emits (and to the
   * Kotlin parser's expectation at CssParsing.kt:96), so the converter and
   * every renderer consume slotted children through the EXACT pipeline the
   * EXTFIX-A nested descendants already exercise. Ids keep their legacy
   * `<idPrefix>__N` / `<idPrefix>__text` values in both branches so
   * per-test grouping (split-combined-ir's slot.parent climb) and diff
   * churn stay minimal.
   */
  const emitTopLevel = (id, cmp) => {
    if (slotIntoBody) {
      // Lazily create the map so a childless slotted body (tree.length===0)
      // emits NO empty `children` key — the omit-when-empty rule EXTFIX-A
      // pins for minimal fixture diffs.
      bodyCmp.children ??= {};
      bodyCmp.children[id] = { id, ...cmp }; // child shape: id + component
    } else {
      // wave-30 A4 bake-down: THIS is the missing hop. A top-level body child
      // is the one level CSS inheritance cannot reach on the sibling wire, so
      // the root's declared trigger properties are copied onto its own bag
      // here — after buildNode has finished (so the 100x100 empty-node
      // placeholder decision, the br sizing and the UA bakes all still see
      // the element's REAL declarations, never a handed-down one), and only
      // for keys the child does not already speak for.
      if (bakeRootInherited) {
        const baked = rootInheritedBakeProps(root.props, cmp.properties);
        if (baked) {
          // Appended, so an author declaration keeps its position in the bag
          // and a reader can see at a glance which keys arrived by copy.
          Object.assign(cmp.properties, baked);
          // LOUD, once per affected component (a bag is baked exactly once —
          // emitTopLevel runs once per top-level id).
          cmp._lossy = true;
          cmp._lossyReasons = [...(cmp._lossyReasons ?? []), ROOT_INHERITED_REASON];
          lossyOverall = true;
          lossyReasonsOverall.add(ROOT_INHERITED_REASON);
        }
      }
      components[id] = cmp; // legacy sibling shape, byte-for-byte
    }
  };

  // wave-11 TITAN fix 2: LEADING anonymous body text. Browsers wrap bare
  // text that is a direct <body> child in an anonymous block box (CSS 2.1
  // §9.2.1.1) occupying one line box before the first element — dropping
  // it shifted every capture ~18px up vs the browser-ref (the whole web
  // gap on the css-grid descendant-static-position family). Emit it as a
  // `_text`-bearing block component AFTER the synthetic body root but
  // BEFORE the `__N` element siblings (the components map preserves
  // insertion order, which is the renderers' document order). Empty
  // properties bag on purpose: like the per-element ownText case, the
  // renderer lays the text out at prose defaults and the capture is
  // meaningful without an explicit width/height (see buildNode's Bug 1
  // note). Leading-run-only scope + the interleaved-text gap are
  // documented on extractLeadingBodyText.
  // wave-12: the Info variant absorbs pure-inline elements into the
  // leading run (reading order preserved) and reports how many it merged,
  // so `<body>There should be <strong>no red</strong>: <div>…` yields ONE
  // `__text` component with the full sentence instead of a fragmented
  // prose + block-stacked <strong> pair.
  // wave-15 part 3: leading body prose is body-scope text, so its collapse
  // switches on the BODY's resolved white-space (the body-root rule bag —
  // no element chain exists above body-level anonymous text).
  const leadingPreserve = WHITESPACE_PRESERVING.has(root.props['white-space']);
  const leading = extractLeadingBodyTextInfo(cleaned, mergeCtx, leadingPreserve);
  if (leading.text) {
    const cmp = { properties: {}, _text: leading.text };
    if (leading.merged > 0) {
      // Honest lossy marker — the absorbed runs' default styling (bold
      // for <strong>, italic for <em>, …) is dropped by the merge.
      lossyOverall = true;
      lossyReasonsOverall.add('inline-run-merged');
      cmp._lossy = true;
      cmp._lossyReasons = ['inline-run-merged'];
    }
    // wave-17: leading anonymous body text is a <body> child like any
    // element, so it rides the same slotting switch — under a sized body it
    // nests (its line box paints INSIDE the body area, as in the real
    // page); otherwise it stays the legacy `__text` sibling byte-for-byte.
    emitTopLevel(`${idPrefix}__text`, cmp);
  }

  // EXTFIX-A: walk subtree as a NESTED tree (not flat). depth ≤ 5 covers
  // the css-grid abspos / css-overflow / css-contain patterns documented
  // in swarm-001 without unbounded blow-up. wave-12: the mergeCtx switches
  // on pure-inline run merging (see extractBodyTreeNested's doc comment).
  const tree = extractBodyTreeNested(cleaned, 5, mergeCtx);

  // wave-21 B-RC2: body-level bare text BETWEEN/AFTER elements — each run
  // becomes an ordered `__text<k>` component (run k renders after tree
  // element k-1; see extractBodyTextRuns' alignment contract). Same
  // white-space switch as the leading run: both are body-scope prose.
  const bodyRuns = extractBodyTextRuns(cleaned, mergeCtx, leadingPreserve);

  if (tree.length === 0) {
    // No renderable elements at all. Keep the legacy degenerate
    // placeholder so the capture pipeline still produces a row — unless
    // the body-root or the wave-11 leading-text component already gives
    // the canvas real content (a text-only body now yields its prose as
    // `__text` instead of a phantom 100x100 box).
    // (wave-17 note: a SLOTTED `__text` is absent from the top-level map,
    // but slotting requires the `__body` key to exist, so the first clause
    // already suppresses the placeholder — no slotting-awareness needed.)
    if (!components[`${idPrefix}__body`] && !components[`${idPrefix}__text`]) {
      components[`${idPrefix}__0`] = { properties: { width: '100px', height: '100px' } };
    }
    return {
      components,
      lossyOverall,
      lossyReasons: [...lossyReasonsOverall],
    };
  }

  /**
   * Recursively build an IRComponent-shaped object from a nested tree
   * node. The top-level call handles the `__N` siblings (recorded in the
   * `components` map); recursive calls produce the `{id, properties, …}`
   * entries that live inside parent.children.
   *
   * `id` is depth-aware: a depth-2 child of __0 is __0__0; a depth-3
   * grandchild is __0__0__0 (and so on). This keeps every descendant's
   * id stable and unique even when DOM trees are wide+deep, and matches
   * the IRComponent.id contract (each node has its own id).
   */
  // wave-22 BR-LINE-CONTEXT: `lineCtx` is a PER-SIBLING-SCOPE mutable
  // accumulator ({ hasInline: boolean }) threaded through each sibling loop
  // in document order (top-level tree.forEach + the children loop below).
  // It tracks whether the CURRENT line box already carries in-flow
  // inline-level content, which decides a <br>'s height (see the
  // INLINE_LEVEL_TAGS banner for the rule + the two measured wave-21
  // regressions it repairs). null (recursion-less callers, e.g. unit
  // fixtures) keeps the wave-21 B-RC1 behaviour for line-start brs.
  function buildNode(node, id, lineCtx = null) {
    const { props, matchedRules, pseudo } = propsForElement(
      rules, node.tag, node.attrs, node.ancestors, node.pos ?? null, effectiveCtx,
    );
    // wave-22 EX2 B-RC4a: fold a collapsed inline chain's declarations into
    // the flat bag FIRST, so every downstream step (sibling-index baking,
    // keyframe sampling, the lossy scan, the line-context rules) sees the
    // final bag. Root-wins precedence was already applied when the extras
    // were computed (collapseInlineRun skips any key the root declares),
    // so this spread can never override an author declaration on the root.
    if (node.collapsedProps) Object.assign(props, node.collapsedProps);
    // UA heading metrics for a collapsed <h1> wrapper (see UA_H1_PROPS) —
    // the guard list already proved the root declares none of them.
    if (node.uaHeadingProps) Object.assign(props, node.uaHeadingProps);
    // wave-30 A7: UA hyperlink styling (HTML Rendering §15.5.2). Folded
    // HERE, alongside the other UA bake and before the sibling-index /
    // attr() / keyframe passes and the lossy scan, so every later step sees
    // the final bag. uaLinkProps has already applied the author-wins guards,
    // so this assign can never overwrite a declaration.
    const uaLink = uaLinkProps(node.tag, node.attrs, props, uaLinkSuppressed);
    if (uaLink) Object.assign(props, uaLink);
    // wave-36 M6: HTML §15.3.3 table presentational attributes (see the
    // htmlTablePresentationProps banner). Folded in the SAME slot and for
    // the same reason as the two UA bakes above — before every later pass —
    // and with the same author-wins guarantee, so this assign can never
    // overwrite a declaration either.
    const tablePres = htmlTablePresentationProps(
      node.tag, node.attrs, node.ancestors, props,
    );
    if (tablePres) Object.assign(props, tablePres.props);
    // wave-36 THE WIDGET-APPEARANCE BAKE (the sixth bake) — same slot, same
    // reason as the three bakes above: the resolved bag is complete (matched
    // rules + inline style + the UA folds), so the element's own
    // `appearance` declaration is knowable, and every later pass sees the
    // final value. The `length` guard is the corpus-wide short-circuit: no
    // recognised script ⇒ not even a selector match is attempted.
    if (appearanceDisablingRules.length > 0) {
      // Matched through the REAL matcher (combinators, ids, :nth-child, …)
      // rather than a private selector reimplementation — only the match
      // COUNT is read, so the synthetic marker declaration can never leak
      // into this element's property bag.
      const hit = propsForElement(
        appearanceDisablingRules, node.tag, node.attrs,
        node.ancestors, node.pos ?? null, effectiveCtx,
      ).matchedRules > 0;
      const widgetAppearance = widgetAppearanceBake.widgetFallbackAppearance(
        node.tag, node.attrs, props.appearance, hit,
      );
      // Overrides an author `appearance` ON PURPOSE (the search-text
      // reference renders `none` over a declared `textfield`) — see the
      // module banner's ref-pinned table.
      if (widgetAppearance) props.appearance = widgetAppearance;
    }
    // wave-21 A-RC6: bake sibling-index() with this element's 1-based
    // renderable-sibling position (CSS Values 5 §5.1 — see the baking
    // section banner). BEFORE the sampler + lossy scan so those see the
    // folded value (e.g. `calc(50deg * 2)` no longer trips calc lanes).
    const sibBaked = node.pos
      ? bakeSiblingIndex(props, node.pos.domSibIndex + 1)
      : false;
    // wave-25 ATTR BAKE: substitute css-values-5 §7 attr() against THIS
    // element's own attribute bag (the originating element, per spec).
    // Ordered alongside the sibling-index bake and BEFORE the sampler +
    // lossy scan for the identical reason: every later step must see the
    // final value (a baked `width: 0` must not still read as `attr(…)`,
    // and a baked `2em` must trip the em/rem lane).
    const attrBaked = bakeAttr(props, node.attrs);
    // wave-13 KEYFRAMES-SAMPLER: element path (e.g. the `.container` divs
    // of the css-backgrounds animation family). Before the lossy scan for
    // the same reason as the body-root call site above.
    const sampled = bakeSampledAnimation(props, keyframes);
    const reasons = lossyReasonsFor(props);
    // wave-12: nodes whose ownText absorbed pure-inline runs are lossy —
    // the merge is an honest approximation (default bold/italic weight of
    // the absorbed run is dropped; see the EXTRACTOR-INLINE block comment).
    // Rides the same reasons array so the existing _lossy/_lossyReasons
    // emission + overall roll-up below cover it with no extra branches.
    if (node.inlineMerged) reasons.push('inline-run-merged');
    // wave-21 B-RC9a: ownText glued across a kept child — order changed
    // (see scanOwnText's reordered doc). LOUD marker, honest gate.
    //
    // wave-32 lane R RETIREMENT, per component and only when EARNED: a
    // component that ships `_runs` no longer approximates anything — the
    // wire carries the true document order and all three renderers read it
    // — so the reason comes off. A component whose alignment check refused
    // (alignRuns returned null: head-only sibling, maxDepth truncation, a
    // walk disagreement) keeps it, because for that one the loss is real
    // and unchanged. This is the only place the two states are told apart.
    if (node.inlineReordered && !node.runs) reasons.push('inline-run-reordered');
    // wave-22 EX2 B-RC4a: LOUD markers for the collapse. The property
    // folding itself happened above, BEFORE the lossy scan, so a folded
    // `text-decoration-inset: -0.5em` still trips the em/rem lane.
    if (node.uaHeadingProps) reasons.push('ua-heading-defaults');
    // wave-30 A7 — LOUD marker for the baked UA hyperlink rule. It also
    // carries the :visited gap (see the UA_LINK_PROPS banner): a component
    // wearing this reason is painting the UNVISITED colour by construction,
    // so a purple-vs-blue residual against a ref is OUR approximation and
    // must be read as such, not as a renderer divergence.
    if (uaLink) reasons.push(UA_LINK_REASON);
    // wave-36 M6 — the two LOUD halves of the table presentational bake.
    // 'baked' is provenance (a 1px cell rule in this fixture came from the
    // markup's `border=`, not from a stylesheet); 'unmodelled' is the scope
    // cut speaking for itself — this table carries rules/frame/bordercolor
    // and therefore ships with NO presentational geometry at all, so a
    // border-model divergence against the ref is ours and is stated.
    if (tablePres?.unmodelled) reasons.push(HTML_TABLE_PRESENTATION_UNMODELLED_REASON);
    else if (tablePres) reasons.push(HTML_TABLE_PRESENTATION_REASON);
    // Only PARTIAL-coverage collapses are approximations — a wrapper that
    // held part of the run now decorates all of it (see the flag's
    // assignment in extractBodyTreeNested). Full ancestor chains collapse
    // losslessly and stay unmarked.
    if (node.inlineChainCollapsed) reasons.push('inline-chain-collapsed');
    // wave-22 EX2 SKEPTIC: a decoration MODIFIER (style/thickness/inset/
    // offset) declared by a line-bearing link lost its fold slot to an outer
    // box and has no `_decorations` field — LOUD, because the declaration is
    // absent from the fixture entirely (see carriesUnexpressibleDecoration).
    if (node.inlineChainDeclDropped) reasons.push('inline-chain-decoration-dropped');
    // wave-21 A-RC6: the baked index is a statically-resolved runtime value
    // — informational provenance marker (never score-excluding; scoring is
    // gated by notApplicable tags, not lossyReasons).
    if (sibBaked) reasons.push('baked-sibling-index');
    // wave-25 ATTR BAKE — three LOUD, mutually independent markers (a bag
    // can bake one declaration, bail on another and poison a third):
    // 'baked-attr' is informational provenance like the sibling index;
    // 'attr-unresolved' says a form we do not model still ships raw;
    // 'attr-invalid-at-computed-value-time' says a declaration was rewritten
    // to `unset` because attr() produced the guaranteed-invalid value.
    if (attrBaked.baked) reasons.push(ATTR_BAKED_REASON);
    if (attrBaked.unresolved) reasons.push(ATTR_UNRESOLVED_REASON);
    if (attrBaked.iacvt) reasons.push(ATTR_IACVT_REASON);
    // wave-13: the sampled-animation LOUD marker rides the same reasons
    // array as inline-run-merged so the existing _lossy/_lossyReasons
    // emission + overall roll-up cover it with no extra branches.
    if (sampled) reasons.push('sampled-animation');
    // wave-15 BIDI-EXTRACT part 2: HTML `dir` attribute → CSS `direction`.
    // Presentational-hint precedence (HTML §15.3.4): an author-origin
    // `direction` already in the props bag (matched rule or inline style)
    // wins, so the mapping is only consulted when the bag has none.
    // `dir=auto` resolves NOW via the first-strong scan over the subtree
    // text (the runtimes cannot re-resolve) — a baked heuristic result, so
    // it rides the lossy lane as 'dir-auto-resolved' (LOUD, not silent).
    const dirResolved = ('direction' in props)
      ? null
      : dirAttributeDirection(node.attrs, collectSubtreeText(node));
    if (dirResolved && node.attrs.dir.trim().toLowerCase() === 'auto') {
      reasons.push('dir-auto-resolved');
    }
    if (reasons.length) {
      lossyOverall = true;
      reasons.forEach((r) => lossyReasonsOverall.add(r));
    }
    const cmp = { properties: props };
    if (node.tag === 'br') {
      // wave-21 B-RC1: a bare <br> is a LINE BREAK, not a box — the old
      // path fell into the 100x100 placeholder below, so five body-level
      // <br>s became a 500px phantom column pushing everything down on all
      // three platforms (abspos-containing-block-outside-spanner). Emit a
      // one-line-box spacer instead: height 20px = the pinned REF line box
      // (capture-browser-ref's REF_LINE_HEIGHT 1.25 × the 16px default —
      // verified against the cached ref PNG, whose prose after 5 <br>s
      // starts at y=136 ≈ 16px pad + 5×20px lines + 16px <p> margin),
      // width 0 so the empty line contributes NO horizontal ink. Any
      // matched declarations (rare — e.g. a universal reset) spread OVER
      // the base so an author height/width still wins, honest to cascade.
      // wave-22 BR-LINE-CONTEXT refinement of the height (see the
      // INLINE_LEVEL_TAGS banner for the full rule + measured regressions):
      //   - a br carrying a `clear` declaration is the float-row-break
      //     marker — its vertical contribution is clearance the engines'
      //     wave-19 Clear machinery already models (FloatRowPacking strut
      //     P6 expects a 0-height marker), so height 0 (justify-self-001);
      //   - a br PRECEDED by inline content on its line just ENDS that
      //     line (CSS 2.1 §9.5 — no height of its own), so height 0
      //     (empty-span-001's twelve `<span>…</span><br>` lines);
      //   - only a LINE-START br is a blank 20px line box (B-RC1's five
      //     consecutive body-level brs, verified against the cached ref).
      // Width stays 0 and BOTH dimensions stay explicit in every case so
      // the 100x100 empty-node placeholder below can never claim a br.
      const brEndsInlineLine = lineCtx?.hasInline === true;
      const brIsClearMarker = 'clear' in props;
      const brHeight = (brEndsInlineLine || brIsClearMarker) ? '0px' : '20px';
      cmp.properties = { width: '0px', height: brHeight, ...props };
      // meta.role wire: `_role` → IR v2 `meta.role` (same channel as
      // 'body-root') so renderers can special-case the break if needed.
      cmp._role = 'line-break';
      // A br always terminates the current line — the next sibling starts
      // a fresh, empty line box.
      if (lineCtx) lineCtx.hasInline = false;
    } else if (matchedRules === 0 && Object.keys(props).length === 0 && !node.ownText
               && !(node.children && node.children.length > 0)
               && !(INTRINSIC_WIDGET_TAGS.has(node.tag)
                    && !(node.attrs && FOREIGN_NS_MARKER_ATTR in node.attrs))) {
      // wave-24 B-RC2: the `!node.children` guard above keeps rule-less
      // CONTAINERS out of this branch. The placeholder's premise is "this
      // element has no content of its own, so give the canvas SOMETHING
      // non-degenerate" — but a rule-less `<ul>` wrapping kept `<li>`
      // children HAS content: the children the walker already decided to
      // keep, emitted into `cmp.children` a few lines below. Forcing
      // 100x100 on such a wrapper does not add a placeholder, it CLIPS a
      // real subtree into a hard 100×100 box (and, worse, pins a width the
      // children's own layout should have derived). MEASURED on
      // css-lists/change-list-style-type-001: its ten un-classed `<ul>`s
      // match no rule and carry no own text, so all ten became 100×100
      // boxes hosting their `<li>` — a column of clipped squares against a
      // ref that is ten full-width list rows. With the guard they emit an
      // EMPTY properties bag, exactly like the `ownText` case documented
      // below: the renderer lays the subtree out at UA defaults and the
      // capture is meaningful without an invented width/height.
      // Note the guard reads the TREE node's kept children (the same array
      // the `cmp.children` map is built from), not the raw DOM — an
      // element whose every child was dropped by the walker really is
      // empty and correctly keeps the placeholder.
      // wave-22 EX2 A-RC1 part 2: the `!INTRINSIC_WIDGET_TAGS` guard above
      // keeps rule-less form controls OUT of this branch — see that set's
      // banner for why a 100x100 <select> is worse than the UA chrome it
      // would replace.
      // Bug 1 honest fallback: no matching rules + no inline style + no
      // own text — this is the genuine "human instruction" case (or
      // scaffolding wrappers). Still emit a 100x100 placeholder so the
      // canvas is non-degenerate but downstream graders can recognise it
      // as an empty node. NB: when the element HAS own text we keep the
      // empty properties bag — the renderer lays out the text and the
      // capture is meaningful even without explicit width/height.
      cmp.properties = { width: '100px', height: '100px' };
    }
    // wave-22 BR-LINE-CONTEXT: non-br siblings update the open-line state
    // consumed by the br height rule above. Document order is guaranteed —
    // both sibling loops build strictly in sequence.
    if (lineCtx && node.tag !== 'br') {
      // Computed overrides beat tag defaults, mirroring the used-value
      // rules the engines apply: a floated or absolutely-positioned box is
      // out of flow (CSS 2.1 §9.7 — it neither joins nor ends the line;
      // floats specifically must NOT arm `hasInline`, or justify-self-001's
      // float rows would re-arm the state their clear-brs just cleared),
      // and an explicit block-family display on an inline tag ends the
      // line like any block box.
      const displayVal = String(props.display ?? '').trim().toLowerCase();
      const floatVal = String(props.float ?? '').trim().toLowerCase();
      const positionVal = String(props.position ?? '').trim().toLowerCase();
      const outOfFlow =
        floatVal === 'left' || floatVal === 'right' ||
        floatVal === 'inline-start' || floatVal === 'inline-end' ||
        positionVal === 'absolute' || positionVal === 'fixed';
      // display:none generates no box at all (CSS 2.1 §9.2.4) — it neither
      // opens nor closes the line, so the state is left untouched.
      if (!outOfFlow && displayVal !== 'none') {
        // In-flow: inline-level content opens/extends the line; block-level
        // content closes it (the next br would then start a blank line).
        const inlineLevel = displayVal !== ''
          ? displayVal.startsWith('inline') || displayVal === 'contents'
          : INLINE_LEVEL_TAGS.has(node.tag);
        lineCtx.hasInline = inlineLevel;
      }
    }
    // wave-15 part 2: land the dir-attribute mapping AFTER the placeholder
    // decision above — a rule-less, text-less `<div dir=ltr>` should still
    // read as scaffolding (placeholder box) rather than a styled subject,
    // so the mapped `direction` is added onto whichever bag survived.
    if (dirResolved) cmp.properties.direction = dirResolved;
    if (reasons.length) {
      cmp._lossy = true;
      cmp._lossyReasons = reasons;
    }
    // swarm-003 Bug 1: emit `_pseudo: { before?, after?, marker? }` when
    // any ::pseudo-element rule matched this host. The sister
    // F-G-RENDERER agent renders each entry as an inline span carrying
    // the declared `content` (and any other declarations from the rule).
    // We emit nothing when no pseudo-rules matched, keeping the fixture
    // byte-identical for components with no generated content (the
    // ~99% case in the existing fixtures/wpt corpus). Per-pseudo lossy
    // markers also propagate so the dashboard's lossy lane sees them.
    if (pseudo) {
      const pseudoOut = {};
      for (const peName of Object.keys(pseudo)) {
        const peProps = pseudo[peName];
        if (!peProps || Object.keys(peProps).length === 0) continue;
        // wave-21 A-RC6: pseudo bags bake with the HOST's sibling index —
        // per css-values-5 §5.1 tree-counting resolves against the
        // originating element for pseudo-elements.
        const peSibBaked = node.pos
          ? bakeSiblingIndex(peProps, node.pos.domSibIndex + 1)
          : false;
        // wave-25 ATTR BAKE: a pseudo-element's attr() reads the
        // ORIGINATING element's attributes (css-values-5 §7), which is
        // exactly the host `node.attrs` bag — so `::before { content:
        // attr(data-mark, "…") }` resolves against the host's data-mark.
        const peAttrBaked = bakeAttr(peProps, node.attrs);
        // wave-13 KEYFRAMES-SAMPLER: pseudo-element bags sample too (WPT
        // has ::before animation variants of the same time-stable family).
        const peSampled = bakeSampledAnimation(peProps, keyframes);
        const peReasons = lossyReasonsFor(peProps);
        // Same LOUD marker contract as the host-element path above.
        if (peSampled) peReasons.push('sampled-animation');
        if (peSibBaked) peReasons.push('baked-sibling-index');
        // Same three-marker contract as the host bag above.
        if (peAttrBaked.baked) peReasons.push(ATTR_BAKED_REASON);
        if (peAttrBaked.unresolved) peReasons.push(ATTR_UNRESOLVED_REASON);
        if (peAttrBaked.iacvt) peReasons.push(ATTR_IACVT_REASON);
        if (peReasons.length) {
          lossyOverall = true;
          peReasons.forEach((r) => lossyReasonsOverall.add(r));
        }
        const peEntry = { properties: peProps };
        if (peReasons.length) {
          peEntry._lossy = true;
          peEntry._lossyReasons = peReasons;
        }
        pseudoOut[peName] = peEntry;
      }
      if (Object.keys(pseudoOut).length > 0) cmp._pseudo = pseudoOut;
    }
    // EXTFIX-A part 2: own text → `_text` field. Only emitted when
    // non-empty so component fixtures without text (visual-test.json,
    // fixtures/properties/*) stay byte-identical post-rollout.
    if (node.ownText) cmp._text = node.ownText;
    // wave-22 EX2 B-RC4a: the merged decoration list for a collapsed
    // inline chain — `_decorations` on the fixture wire, forwarded by the
    // converter as IR v2 `meta.decorations` (the additive omit-when-absent
    // meta key sanctioned by schema/spec/05-versioning.md, same extension
    // point wave-20's `_attrs` → `meta.attrs` used). Shape + ordering +
    // the authoritative-when-present rule are pinned in the
    // collapseInlineRun banner; lane DECOR is the consumer. Emitted only
    // when the collapse produced at least one line, so every fixture that
    // does not collapse stays byte-identical.
    if (node.decorations && node.decorations.length > 0) {
      cmp._decorations = node.decorations;
    }
    // Bug 1 fix (css3-counter-styles-101): carry forward the originating
    // HTML element identity so the renderer can branch on it (e.g. wrap
    // children of `<ol>` in `<li>` boxes that trigger native marker
    // generation). We emit `_tag` only for non-generic tags — `div` is
    // the IR's default container shape and tagging it would bloat every
    // visual-test-style fixture without conveying new info. (`span` left
    // the generic set in wave-31 lane S — see the GENERIC_WRAPPER_TAGS
    // banner: an inline box is NOT the default box, so its tag is real
    // information the web renderer needs.)
    // Sister F-RENDERER consumes `_tag` as a lowercase HTML element name.
    // wave-20 fix 5: the serialized-DOM path stamps FOREIGN_NS_MARKER_ATTR
    // on every non-XHTML-namespace element before serialization (see the
    // marker's banner). A marked element renders with NO tag semantics in
    // the browser (no widget chrome, no list/heading/table behaviour), so
    // it gets the generic-container treatment: neither `_tag` nor `_attrs`
    // is emitted. Static-markup fixtures never carry the marker.
    const foreignNs = !!(node.attrs && FOREIGN_NS_MARKER_ATTR in node.attrs);
    if (node.tag && !GENERIC_WRAPPER_TAGS.has(node.tag) && !foreignNs) {
      cmp._tag = node.tag;
    }
    // wave-20 lane W1: forward the widget-identity attributes (see the
    // WIDGET_ATTR_TAGS wire-contract banner). Rides beside `_tag` — every
    // widget tag is non-generic, so a component carrying `_attrs` always
    // carries `_tag` too, and the runtimes key application on that tag.
    // Namespace-gated with `_tag` above: a foreign 'input' has no widget
    // identity to forward (css-ui-4 §7 appearance applies to the HTML
    // widgets; foreign elements have no UA chrome to configure).
    const widgetAttrs = foreignNs ? null : widgetAttrsFor(node.tag, node.attrs);
    // wave-36 lane M1: the replaced-element SOURCE rides the same `_attrs`
    // envelope (see the REPLACED_SRC_TAGS banner). The two lanes' tag sets
    // are disjoint, so this merge can never collide — it is a merge and not
    // an `else` purely so a future overlap fails loudly in a diff rather
    // than silently dropping one lane's keys. Namespace-gated with `_tag`
    // above for the same reason: a foreign-namespace 'img' has no replaced
    // content semantics (it is a plain unknown element, no UA chrome).
    const replacedSrc = foreignNs ? null : replacedSrcFor(node.tag, node.attrs);
    const mergedAttrs = (widgetAttrs || replacedSrc)
      ? { ...(replacedSrc ?? {}), ...(widgetAttrs ?? {}) }
      : null;
    if (mergedAttrs) cmp._attrs = mergedAttrs;
    // wave-37 lane W4 — THE LANG WIRE (see the resolveLanguage banner). The
    // element's COMPUTED content language, already walked, emitted
    // omit-when-absent so every lang-free document keeps its exact bytes.
    // NOT namespace-gated the way `_tag`/`_attrs` are: `lang` is inherited
    // text state, not tag semantics — an SVG `<text>` inside a `<div lang=ja>`
    // shapes with the same Japanese face the surrounding prose does, so
    // withholding it there would be the lie, not the caution.
    const computedLang = resolveLanguage(
      node.attrs, node.ancestors, effectiveCtx.documentLang ?? null,
    );
    if (computedLang) cmp._lang = computedLang;
    // EXTFIX-A part 1: children → nested IRComponent objects. Same
    // omit-when-empty rule so the fixture diff is minimal for
    // single-element tests (the ~70% case in the corpus). Each child
    // carries its own `id` (matching IRComponent.id) so downstream
    // renderers can address it individually for screenshot capture /
    // dashboard linking; top-level components don't repeat the id —
    // the components-map key IS their id.
    if (node.children && node.children.length > 0) {
      // Children stored as `{id1: {...}, id2: {...}}` map (NOT array),
      // matching the Kotlin parser at `converter/src/main/kotlin/app/parsing/css/CssParsing.kt:96`
      // which calls `obj["children"]?.jsonObject?.mapValues { ... }` — it
      // expects a JsonObject keyed by id, not a JsonArray. The components
      // map at the top level uses the same shape, keeping the schema
      // consistent across nesting levels. Each child carries its own
      // `id` field for diff readability + downstream addressing.
      const childMap = {};
      // wave-32 lane R: the run list's `{childIndex:k}` entries become
      // `{child: <authoring key>}` HERE, because this is the loop that
      // mints the keys — `${id}__${k}`, the same string that becomes the
      // child's map key and (after the converter hop) its `name`. Emitted
      // before the loop body only in the sense that it reads the same
      // formula; the loop below is what actually creates the children.
      if (node.runs) {
        cmp._runs = node.runs.map((entry) => (
          entry.childIndex === undefined
            ? { text: entry.text }
            : { child: `${id}__${entry.childIndex}` }
        ));
      }
      // wave-22 BR-LINE-CONTEXT: fresh line state per sibling scope. The
      // parent's ownText renders BEFORE its element children (the
      // collectSubtreeText ordering approximation documented at its
      // banner), so a non-empty ownText opens the children's first line
      // with inline content — a leading child br then ENDS that line
      // instead of adding a blank one.
      const childLineCtx = { hasInline: !!node.ownText };
      // wave-26 lane WWS: the last child's `wsAfter` describes the gap to
      // the parent's close tag, which separates nothing — so the stamp is
      // gated on having a following sibling (see the WS_AFTER_ROLE banner).
      const lastChildIdx = node.children.length - 1;
      node.children.forEach((child, i) => {
        const childId = `${id}__${i}`;
        const childCmp = buildNode(child, childId, childLineCtx);
        stampWsAfter(childCmp, child, i < lastChildIdx);
        childMap[childId] = { id: childId, ...childCmp };
      });
      cmp.children = childMap;
    }
    return cmp;
  }

  // wave-22 BR-LINE-CONTEXT: top-level (body-scope) line state. The wave-11
  // leading `__text` run is body prose BEFORE element 0, so it opens the
  // first line; the wave-21 B-RC2 `__text<k>` runs are prose in gap k
  // (immediately before tree element k) and re-open the line there. Both
  // feed the br height rule in buildNode (see INLINE_LEVEL_TAGS banner).
  const topLineCtx = { hasInline: !!leading.text };
  tree.forEach((node, idx) => {
    const id = `${idPrefix}__${idx}`;
    // A bare-text run in THIS gap (rendered before element idx) puts
    // inline content on the open line — a br element at idx then ends
    // that line rather than adding a blank 20px one.
    if (bodyRuns.some((run) => run.afterElemIndex === idx)) topLineCtx.hasInline = true;
    // wave-17: top-level body elements route through the slotting switch —
    // children of a sized body nest under it (source-truth structure);
    // everything else keeps the legacy `components[id]` sibling emission.
    const topCmp = buildNode(node, id, topLineCtx);
    // wave-26 lane WWS: body-level siblings get the same marker. It is
    // load-bearing only on the wave-17 SLOTTING path (children of a sized
    // body become real siblings under the body-root, where the renderer's
    // (prev, next) separator hook runs); unslotted top-level components are
    // composed roots the hook never pairs, so the marker is inert there —
    // stamped anyway so the wire says the same thing about the same source
    // regardless of which emission path a document happens to take.
    stampWsAfter(topCmp, node, idx < tree.length - 1);
    emitTopLevel(id, topCmp);
    // wave-21 B-RC2: emit the bare-text run that FOLLOWS element idx (gap
    // index idx+1), preserving reading order in the insertion-ordered
    // components map. Ids are `__text1`, `__text2`, … — `__text` (no
    // suffix) stays the wave-11 leading run for byte-compat. Empty
    // properties bag on purpose (prose defaults; same as the leading run).
    for (const run of bodyRuns) {
      if (run.afterElemIndex !== idx + 1) continue;
      emitTopLevel(`${idPrefix}__text${run.afterElemIndex}`,
        { properties: {}, _text: run.text });
    }
  });

  return {
    components,
    lossyOverall,
    lossyReasons: [...lossyReasonsOverall],
  };
}

/** Persist the {fixture, refFixture} pair into fixtures/wpt/<section>/. */
/**
 * wave-34 lane R — drop a `meta.runs` list that a downstream bake has
 * INVALIDATED.
 *
 * THE INVARIANT the extractor guarantees at emission (scanOwnText +
 * alignRuns): a component that carries `_runs` always carries a NON-EMPTY
 * `_text` too, and the list always contains at least one `{text}` entry.
 * Both halves follow from the emission gate — a run list is only computed
 * when the collapsed own-text buffer is non-empty (or the reorder fired,
 * which needs non-whitespace text after a kept child), and buildRunProto
 * drops pieces that normalise to nothing. Spec 03 §4.1 rule 3 says the same
 * thing from the wire's side: `text` STAYS, carrying the concatenation the
 * runs were split from.
 *
 * So `_runs` WITHOUT `_text` cannot come from this extractor. It means a
 * later stage removed the text — and exactly one does:
 * bidi-bake's applyBidiBakePlan `delete cmp._text` on every root, box and
 * hide, re-emitting the text as absolutely-positioned child components at
 * measured left/top. After that the component has no inline flow left to
 * order, and a surviving run list makes every reader that honours it paint
 * the dissolved text a SECOND time, inline, on top of the positioned boxes.
 *
 * MEASURED (css-text/boundary-shaping-009, the RTL family): its second div
 * already hit this in wave-32 — `_runs` from the reorder path surviving the
 * bake — and wave-34's widening extended it to the first div, moving the
 * test 0.9394 → 0.9283 against the ref. This sweep is the fix, and it is
 * deliberately at the PRODUCER: `_runs` is read by all three runtimes, so a
 * renderer-side guard would have to be written three times and would still
 * ship a self-contradictory fixture.
 *
 * Returns the number of lists dropped, for the extraction log.
 */
export function dropStaleRuns(fixture) {
  let dropped = 0;
  const walk = (map) => {
    for (const cmp of Object.values(map ?? {})) {
      if (!cmp || typeof cmp !== 'object') continue;
      // The text is gone but the order list is not — the stale pair.
      if (Array.isArray(cmp._runs) && !cmp._text) { delete cmp._runs; dropped += 1; }
      if (cmp.children) walk(cmp.children);
    }
  };
  walk(fixture?.components);
  return dropped;
}

export async function writeFixturePair({ fixture, refFixture, section, stem }) {
  const dir = join(OUT_ROOT, section);
  await fs.mkdir(dir, { recursive: true });
  const testPath = join(dir, `${stem}.json`);
  await fs.writeFile(testPath, JSON.stringify(fixture, null, 2) + '\n', 'utf8');
  let refPath = null;
  if (refFixture) {
    refPath = join(dir, `${stem}__ref.json`);
    await fs.writeFile(refPath, JSON.stringify(refFixture, null, 2) + '\n', 'utf8');
  }
  return { testPath, refPath };
}

// ── wave-23 BIDI BAKE: the static trigger ───────────────────────────────────
//
// tools/titan/bidi-bake.mjs delivers UAX#9 visual geometry by loading the
// TEST page in headless Chromium and re-expressing its laid-out text as
// positioned single-level runs (full rationale in that module's header). The
// gate for launching that browser lives HERE, in the stdlib-only static
// extractor, for two reasons:
//
//   1. COST — the bake costs a page load per test; a source-level detector
//      keeps the ~24k-file batch runs on the pure-static path.
//   2. CONSERVATISM — the bake dissolves normal flow into absolute boxes, so
//      it must NEVER touch a pure-LTR test. This detector is the promise
//      that it cannot: no bidi signal in the source ⇒ no browser, no bake,
//      byte-identical fixture.
//
// The three signals below are the complete set of ways CSS/HTML can put a
// document into bidi processing: RTL-script content, explicit bidi
// formatting controls, `dir=rtl|auto` attributes, and the two CSS properties
// (`direction: rtl`, non-normal `unicode-bidi`). `dir=ltr` alone is NOT a
// signal — an author spelling out the default cannot create a reorder.

/** Strong right-to-left script ranges (Unicode DerivedBidiClass R and AL,
 *  by block). Shared with bidi-bake.mjs: the same list decides "this test
 *  triggers", "this element is bidi-affected" (evaluated in-page) and "this
 *  run mixes directions", so the three can never disagree. Astral ranges are
 *  included, which is why every consumer iterates by CODE POINT. */
export const RTL_CODEPOINT_RANGES = [
  [0x0590, 0x05FF], // Hebrew
  [0x0600, 0x06FF], // Arabic
  [0x0700, 0x074F], // Syriac
  [0x0750, 0x077F], // Arabic Supplement
  [0x0780, 0x07BF], // Thaana
  [0x07C0, 0x07FF], // NKo
  [0x0800, 0x083F], // Samaritan
  [0x0840, 0x085F], // Mandaic
  [0x0860, 0x086F], // Syriac Supplement
  [0x0870, 0x089F], // Arabic Extended-B
  [0x08A0, 0x08FF], // Arabic Extended-A
  [0xFB1D, 0xFB4F], // Hebrew presentation forms
  [0xFB50, 0xFDFF], // Arabic Presentation Forms-A
  [0xFE70, 0xFEFC], // Arabic Presentation Forms-B (stops before U+FEFF ZWNBSP)
  [0x10800, 0x10FFF], // RTL historic scripts (Cypriot … Hanifi Rohingya)
  [0x1E800, 0x1EFFF], // Mende Kikakui, Adlam, Arabic Mathematical Alphabetic
];

/** Explicit bidi FORMATTING CONTROLS (UAX#9 §2). Kept separate from the
 *  script ranges because they are a trigger signal only: U+200E LRM is
 *  strong L, so folding it into the RTL list would make the bake's
 *  mixed-direction guard bail on perfectly ordinary LTR runs. */
export const BIDI_CONTROL_CODEPOINTS = [
  0x200E, 0x200F,                         // LRM, RLM
  0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // LRE, RLE, PDF, LRO, RLO
  0x2066, 0x2067, 0x2068, 0x2069,         // LRI, RLI, FSI, PDI
];

// All three probes are guarded by `(?<![-\w])` rather than `\b`: a plain
// word boundary matches after a HYPHEN, so `data-dir=rtl` would read as the
// HTML `dir` attribute and `redirection: rtl` as `direction: rtl`. The
// lookbehind refuses both a word character and a hyphen before the keyword,
// which is exactly the CSS/HTML ident boundary.

/** `dir="rtl"` / `dir=auto` (quoted, single-quoted or bare). `dir=ltr` is
 *  deliberately absent — see the section banner. */
export const BIDI_DIR_ATTR_RX =
  /(?<![-\w])dir\s*=\s*(?:"\s*(?:rtl|auto)\s*"|'\s*(?:rtl|auto)\s*'|(?:rtl|auto)\b)/i;

/** `direction: rtl` in any stylesheet or inline style attribute. */
export const BIDI_DIRECTION_CSS_RX = /(?<![-\w])direction\s*:\s*rtl\b/i;

/** `unicode-bidi:` set to anything other than its initial `normal` — embed,
 *  isolate, isolate-override, bidi-override, plaintext. */
export const BIDI_UNICODE_BIDI_CSS_RX = /(?<![-\w])unicode-bidi\s*:\s*(?!normal\b)[a-z-]/i;

/**
 * Does this test's source put the document into bidi processing? Returns a
 * short human-readable REASON (logged and carried on the bake outcome) or
 * null. Comments are stripped first, mirroring every other static pass, so a
 * commented-out `direction: rtl` cannot arm a browser launch.
 */
export function bidiBakeTrigger(html) {
  const src = stripComments(String(html ?? ''));
  // Content signal first — it is the one that cannot be faked by markup a
  // browser would ignore.
  for (const ch of src) {
    const cp = ch.codePointAt(0);
    for (const [lo, hi] of RTL_CODEPOINT_RANGES) {
      if (cp >= lo && cp <= hi) return 'rtl-codepoint';
    }
    if (BIDI_CONTROL_CODEPOINTS.includes(cp)) return 'bidi-control-codepoint';
  }
  if (BIDI_DIR_ATTR_RX.test(src)) return 'dir-attribute';
  if (BIDI_DIRECTION_CSS_RX.test(src)) return 'direction-rtl';
  if (BIDI_UNICODE_BIDI_CSS_RX.test(src)) return 'unicode-bidi';
  return null;
}

// ── wave-38 VIEW-TRANSITION BAKE: the static trigger ────────────────────────
//
// tools/titan/view-transition-bake.mjs drives a test's view transition to its
// FROZEN state in headless Chromium and re-expresses the settled
// `::view-transition` pseudo tree as ordinary positioned boxes (full rationale
// in that module's header). The gate for launching that browser lives HERE,
// in the stdlib-only static extractor, for the two reasons the bidi trigger
// banner above already states: COST (a bake is a page load plus two isolation
// screenshots per painted leaf, and the ~24k-file batch runs must stay on the
// pure-static path) and CONSERVATISM (the bake `display: none`s every live
// component and writes a synthetic subtree in their place, so it must never
// reach a document that has no view transition at all).
//
// The four signals below are the complete set of ways a document can enter a
// view transition or style its pseudo tree, per css-view-transitions-1/-2:
// the JS entry points (`startViewTransition` on `document` or on an element
// for the scoped variant), the naming properties (`view-transition-name`,
// `view-transition-class`), any `::view-transition*` pseudo-element selector,
// and the `:active-view-transition*` selectors. Comments are stripped first,
// mirroring every other static pass, so a commented-out demo cannot arm a
// browser launch.
//
// This is deliberately an OVER-approximation — `css-view-transitions/parsing/`
// tests mention the properties without ever starting a transition. The browser
// is the authority that narrows it: no active transition at settle is a loud
// bail, and the fixture stays byte-identical.

/** `document.startViewTransition(...)` / `el.startViewTransition(...)` — the
 *  only ways to begin one. Matched on the method name alone (any receiver),
 *  because the corpus reaches it through `document`, a saved reference and,
 *  for css-view-transitions-2 scoped transitions, an arbitrary element. */
export const VT_START_API_RX = /(?<![-\w])startViewTransition\s*\(/;

/** The two naming properties. `view-transition-class` is included on its own
 *  (not as a prefix of the name property) because a test may set only the
 *  class and inherit the name from a rule in a linked sheet. */
export const VT_NAME_PROPERTY_RX =
  /(?<![-\w])view-transition-(?:name|class)\s*:/i;

/** Any `::view-transition…` pseudo-element selector — the tree the bake
 *  serializes. Covers the bare root pseudo and all four sub-pseudos. */
export const VT_PSEUDO_SELECTOR_RX = /::view-transition(?![-\w])|::view-transition-[a-z-]+\s*\(/i;

/** The css-view-transitions-2 state pseudo-classes. A test that only asserts
 *  `:active-view-transition` still has a live tree worth serializing. */
export const VT_ACTIVE_SELECTOR_RX = /:active-view-transition(?:-type)?(?![-\w])/i;

/**
 * Does this test's source put the document into a view transition (or style
 * one)? Returns a short human-readable REASON (logged and carried on the bake
 * outcome) or null.
 */
export function viewTransitionBakeTrigger(html) {
  const src = stripComments(String(html ?? ''));
  // API first — it is the signal that a transition actually RUNS, which is
  // the only shape the bake can deliver; the rest are styling-only hints.
  if (VT_START_API_RX.test(src)) return 'start-view-transition-api';
  if (VT_PSEUDO_SELECTOR_RX.test(src)) return 'view-transition-pseudo';
  if (VT_NAME_PROPERTY_RX.test(src)) return 'view-transition-name';
  if (VT_ACTIVE_SELECTOR_RX.test(src)) return 'active-view-transition-selector';
  return null;
}

// ── specSection helper (mirrors bucket-wpt.mjs) ──────────────────────────────
function specSectionOf(testRel) {
  const parts = testRel.split('/');
  if (parts.length >= 2 && parts[0] === 'css') return parts[1] || 'css';
  return 'css';
}

/** Re-read one test's AUTHORED source for the counter bake's dynamic gate.
 *  Deliberately NOT threaded out of extractFixture: under `--post-load` that
 *  function may have run on a SERIALIZED post-script DOM, and the gate must
 *  see the `<script>` the serializer already executed away. A missing file
 *  cannot happen here (extractFixture just read it) but an empty string is
 *  the safe answer — it means "no dynamic signal", and the bake still has
 *  its own per-item declines. */
async function readTestSource(testRel) {
  try { return await fs.readFile(join(WPT_DIR, testRel), 'utf8'); } catch { return ''; }
}

// ── CLI ──────────────────────────────────────────────────────────────────────
async function main() {
  // wave-16 POST-LOAD activation (opt-in): `--post-load` flag or
  // POST_LOAD_EXTRACT=1 env (the env form is how section-runner.sh opts a
  // whole section run in without a script change). When enabled, tests
  // whose wpt-buckets.json notApplicable tags cross the EXTRACTION WALL
  // (post-load-extract.mjs's isWallTagged — exactly the tags the score gate
  // excludes on) get the live-browser computed-state overlay; everything
  // else keeps the static path byte-identically. The module is imported
  // DYNAMICALLY so the default static path never pays the puppeteer/sharp
  // import cost (and so this module stays stdlib-only for its ~24k-file
  // batch runs).
  const postLoadEnabled = process.argv.includes('--post-load')
    || process.env.POST_LOAD_EXTRACT === '1';
  // wave-23 BIDI BAKE activation (opt-in, same shape): `--bidi-bake` flag or
  // BIDI_BAKE=1 env. Independent of --post-load — one delivers post-SCRIPT
  // state, the other post-LAYOUT bidi geometry — and when both are on they
  // compose in that order (structure/state first, then the bidi geometry
  // measured on the same settled page).
  const bidiBakeEnabled = process.argv.includes('--bidi-bake')
    || process.env.BIDI_BAKE === '1';
  // wave-38 VIEW-TRANSITION BAKE activation (opt-in, same shape): `--vt-bake`
  // flag or VT_BAKE=1 env. Independent of the other two — it delivers the
  // settled `::view-transition` pseudo tree, which no computed-style overlay
  // or text-geometry read can reach — and it runs LAST of the four passes
  // because it retires every live component in favour of that tree.
  const vtBakeEnabled = process.argv.includes('--vt-bake')
    || process.env.VT_BAKE === '1';
  const inputs = process.argv.slice(2)
    .filter((a) => a !== '--post-load' && a !== '--bidi-bake' && a !== '--vt-bake');
  if (inputs.length === 0) {
    console.error('usage: extract-fixture.mjs [--post-load] [--bidi-bake] [--vt-bake] <relative-test-path>...');
    console.error('       (paths are repo-relative, e.g. "css/css-color/a98rgb-001.html")');
    process.exit(1);
  }
  // Lazily-loaded post-load module handle (null while disabled).
  const postLoad = postLoadEnabled ? await import('./post-load-extract.mjs') : null;
  // Same lazy-import discipline for the bidi bake: it pulls puppeteer, and
  // the default static path must never pay that cost.
  const bidiBake = bidiBakeEnabled ? await import('./bidi-bake.mjs') : null;
  // Same lazy-import discipline again: the view-transition bake pulls
  // puppeteer AND pngjs (it solves each snapshot's colour out of two
  // isolation composites), neither of which the static path may pay for.
  const vtBake = vtBakeEnabled ? await import('./view-transition-bake.mjs') : null;
  let ok = 0, fail = 0;
  try {
    for (const rel of inputs) {
      try {
        const result = await extractFixture(rel);
        // Post-load overlay BEFORE writing so the persisted fixture carries
        // the baked state + `_wpt.postLoadExtracted` stamp atomically.
        // Declines/bails leave the fixture byte-identical (the documented
        // bail-to-static contract) and are surfaced in the log line.
        let postLoadNote = '';
        // wave-30 A3: activation now has TWO routes — the bucketer's
        // extraction-wall tags (wave-16) OR a static rule pass that dropped
        // ≥1 rule as unsupported (countUnsupportedRules above, surfaced on
        // `result.unsupportedRules`). See shouldPostLoadExtract's banner for
        // why a dropped rule is exactly the "ask the browser" signal.
        if (postLoad && await postLoad.shouldPostLoadExtractFor(rel, result.unsupportedRules)) {
          const outcome = await postLoad.postLoadAugmentFixture(result.fixture, rel);
          // wave-20: `+structure` marks the tree-re-extraction path (the
          // fixture carries _wpt.structureExtracted alongside the state stamp).
          // wave-29 S-RC5: `+pseudo(N)` marks the pseudo-bag re-derivation —
          // N generated-content bags that the class/attribute mutation moved
          // and that would otherwise have shipped at their pre-mutation
          // value inside a fixture stamped "delivered".
          postLoadNote = ` [post-load: ${outcome.status}${outcome.structure ? '+structure' : ''}` +
            `${outcome.pseudoRederived ? `+pseudo(${outcome.pseudoRederived})` : ''}` +
            `${outcome.reason ? ` — ${outcome.reason}` : ''}]`;
        }
        // wave-23 BIDI BAKE, after post-load so it measures the settled tree
        // the fixture actually carries. The module re-checks the static
        // trigger itself (it also has a standalone CLI), so a non-bidi test
        // costs one regex sweep and no browser.
        let bidiNote = '';
        if (bidiBake) {
          const outcome = await bidiBake.bidiBakeFixture(result.fixture, rel);
          // 'skipped' is the overwhelmingly common outcome (no bidi signal)
          // and would drown the batch log — only report real activity.
          if (outcome.status !== 'skipped') {
            bidiNote = ` [bidi-bake: ${outcome.status}` +
              (outcome.status === 'baked'
                ? ` — ${outcome.roots} roots, ${outcome.runs} runs`
                : ` — ${outcome.reason}`) + ']';
          }
          // wave-34 lane R: the bake DISSOLVES inline flow (see
          // dropStaleRuns) — sweep the run lists it just invalidated.
          const stale = dropStaleRuns(result.fixture) + dropStaleRuns(result.refFixture);
          if (stale > 0) bidiNote += ` [stale-runs dropped: ${stale}]`;
        }
        // wave-27 lane CBAKE — the counter-style bake runs LAST, on the tree
        // the fixture will actually carry: post-load may have re-extracted
        // the structure and the bidi bake may have moved an item's text into
        // a positioned child, and the marker belongs to the `<li>` that
        // survives both. Unlike the other bakes it is pure (no browser, no
        // opt-in flag): css-counter-styles-3 §6 is a closed table, so the
        // cost on a list-free fixture is one JSON.stringify gate and the
        // result is byte-identical. The AUTHORED source is passed (not any
        // post-load rewrite) because the dynamic-counter gate is about what
        // the test SAYS, and a `<script>` is exactly what post-load ran.
        const counterOutcome = counterBake.bakeCounterStyles(
          result.fixture, await readTestSource(rel));
        const counterNote = counterOutcome.status === 'skipped' ? ''
          : ` [counter-bake: ${counterOutcome.status} — ${counterOutcome.stamped} markers` +
            `${counterOutcome.declined ? `, ${counterOutcome.declined} declined` : ''}` +
            `${counterOutcome.reason ? ` (${counterOutcome.reason})` : ''}]`;
        // wave-38 VIEW-TRANSITION BAKE, after every other pass. It is the one
        // bake that RETIRES the tree the others built (a captured document is
        // painted into the root snapshot, not in place — see that module's
        // applyViewTransitionBakePlan), so running it last means the passes
        // above never operate on the synthetic pseudo-tree subtree and the
        // synthetic subtree is never re-walked by a pass that expects real
        // elements. Skips/declines/bails leave the fixture byte-identical.
        let vtNote = '';
        if (vtBake) {
          // The bake THROWS on a browser fault rather than dressing one up as
          // a scope-boundary bail (see its discardWedgedBrowser banner). In
          // THIS batch path that must not cost the fixture: an uncaught throw
          // here skips writeFixturePair entirely, so the section run would
          // silently lose a test that the pure-static path handles fine —
          // strictly worse than never having enabled the bake. Measured: 7
          // such faults in one 53-test batch (_diag38/N5/bake3.log). So the
          // fault is caught, the static pair is still written byte-identically
          // (the bail-to-static contract), and the note says `errored` — a
          // word no successful outcome uses, so it cannot read as a bail.
          try {
            const outcome = await vtBake.viewTransitionBakeFixture(result.fixture, rel);
            // 'skipped' is the overwhelmingly common outcome (no VT signal in
            // the source) and would drown the batch log — report real activity.
            if (outcome.status !== 'skipped') {
              vtNote = ` [vt-bake: ${outcome.status}` +
                (outcome.status === 'baked'
                  ? ` — ${outcome.groups} groups, ${outcome.leaves} leaves` +
                    // wave-39 A4 provenance: how many of those groups shipped
                    // as a PROVED time-invariant cross-fade (a still-running
                    // UA fade between two identical snapshots) rather than as
                    // a frozen single leaf. Silent when zero, so the wave-38
                    // log shape is unchanged for every test that had none.
                    (outcome.crossFade ? `, ${outcome.crossFade} invariant cross-fade` : '')
                  : ` — ${outcome.reason}`) + ']';
            }
          } catch (vtErr) {
            vtNote = ` [vt-bake: errored — ${vtErr.message ?? vtErr}]`;
          }
        }
        const written = await writeFixturePair(result);
        console.log(`extracted ${rel} → ${relative(REPO_ROOT, written.testPath)}` +
                    (written.refPath ? ` (+ ref)` : ' (ref skipped)')
                    + postLoadNote + bidiNote + counterNote + vtNote);
        ok++;
      } catch (err) {
        console.error(`FAIL ${rel}: ${err.message ?? err}`);
        fail++;
      }
    }
  } finally {
    // Post-load keeps one shared Chromium alive across tests — close it
    // even when a test threw, or the process would hang on exit.
    if (postLoad) await postLoad.closePostLoadBrowser();
    // The bidi bake keeps its OWN shared Chromium (post-load-extract's
    // instance is module-private there); same leak discipline.
    if (bidiBake) await bidiBake.closeBidiBakeBrowser();
    // …and the view-transition bake keeps a third. Same leak discipline: a
    // shared browser left open holds the process alive past the last test.
    if (vtBake) await vtBake.closeViewTransitionBakeBrowser();
  }
  console.log(`extract-fixture: ${ok} ok, ${fail} failed`);
  process.exit(fail > 0 ? 2 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('extract-fixture: fatal:', err);
    process.exit(2);
  });
}
