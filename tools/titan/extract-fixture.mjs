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
// Returns: Array<{ selector: string, props: Record<string,string> }>
export function parseCss(css) {
  const rules = [];
  // Track brace nesting so we can skip @media / @supports bodies wholesale.
  let i = 0;
  const n = css.length;
  while (i < n) {
    // Skip whitespace.
    while (i < n && /\s/.test(css[i])) i++;
    if (i >= n) break;
    // @-rule: skip to matching '}'.
    if (css[i] === '@') {
      // Scan to either ';' (one-liner like @charset) or '{' (block).
      let depth = 0;
      let saw = false;
      while (i < n) {
        const ch = css[i];
        if (ch === '{') { depth++; saw = true; i++; continue; }
        if (ch === '}') { depth--; i++; if (depth === 0 && saw) break; continue; }
        if (ch === ';' && !saw) { i++; break; }
        i++;
      }
      continue;
    }
    // Read selector list up to '{'.
    const selStart = i;
    while (i < n && css[i] !== '{') i++;
    if (i >= n) break;
    const selectorList = css.slice(selStart, i).trim();
    i++; // consume '{'
    // Read body up to matching '}'.
    const bodyStart = i;
    let depth = 1;
    while (i < n && depth > 0) {
      const ch = css[i];
      if (ch === '{') depth++;
      else if (ch === '}') depth--;
      i++;
    }
    const body = css.slice(bodyStart, i - 1);
    // Parse declarations.
    const props = {};
    for (const decl of body.split(';')) {
      const colon = decl.indexOf(':');
      if (colon < 0) continue;
      const k = decl.slice(0, colon).trim();
      let v = decl.slice(colon + 1).trim();
      if (!k || !v) continue;
      // Strip !important — IR has no concept of it; the rendered value wins.
      v = v.replace(/\s*!important\s*$/i, '').trim();
      // Skip custom properties (the bucketer flagged them as B already).
      if (k.startsWith('--')) continue;
      props[k] = v;
    }
    if (Object.keys(props).length === 0) continue;
    // One rule per selector in the comma-list, so each element maps cleanly
    // to its own component.
    for (const sel of selectorList.split(',')) {
      const s = sel.trim();
      if (!s) continue;
      rules.push({ selector: s, props });
    }
  }
  return rules;
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
function walkChildren(html) {
  const out = [];
  let i = 0;
  const n = html.length;
  while (i < n) {
    // Skip text whitespace.
    while (i < n && /\s/.test(html[i])) i++;
    if (i >= n) break;
    // If not '<' it's stray text; skip to next '<' or end. Text nodes
    // never become components — only elements do.
    if (html[i] !== '<') {
      const next = html.indexOf('<', i);
      i = next < 0 ? n : next;
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
  const bodyMatch = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  const fallback = !bodyMatch;
  const inner = bodyMatch ? bodyMatch[1] : html;
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
  // as the body and rely on HEAD_ONLY_TAGS + the implicit-<html> unwrap below
  // to skip head scaffolding.
  const bodyMatch = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  let inner = bodyMatch ? bodyMatch[1] : html;
  // Implicit-<html> case: many minimal WPT tests open <html> but never close
  // it and never open <body>. Unwrap a leading <html> (and/or <head>) so its
  // contents are walked at depth 0 — otherwise every body-content element
  // ends up at depth 1 (which prevents body-scope rules from being noticed
  // and duplicates the HEAD_ONLY filter). The leading skip tolerates a
  // <!doctype> and any whitespace before the first significant tag.
  for (let unwrap = 0; unwrap < 2; unwrap++) {
    // Skip an optional <!doctype …> + leading whitespace.
    const skipped = inner.replace(/^\s*<!doctype\b[^>]*>\s*/i, '').replace(/^\s+/, '');
    const wrapMatch = /^<(html|head|body)\b[^>]*>([\s\S]*?)(?:<\/\1\s*>|$)/i.exec(skipped);
    if (!wrapMatch) break;
    inner = wrapMatch[2];
  }
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
 * Rules splitSelectorChain/parseCompound reject (sibling combinators,
 * attr selectors, unknown pseudos) are skipped: our matcher drops those
 * rules everywhere, so they can't style a child component either — the
 * merge loses nothing the engine would have applied.
 *
 * Exported so the unit tests can pin the guard.
 */
export function collectStyledTags(rules) {
  const out = new Set();
  for (const r of rules) {
    // Tokenise the selector into compounds; null = unsupported chain
    // (sibling combinators / malformed) — rule never matches, skip.
    const chain = splitSelectorChain(r.selector);
    if (!chain || chain.compounds.length === 0) continue;
    // Only the rightmost compound identifies the rule's HOST element.
    const parsed = parseCompound(chain.compounds[chain.compounds.length - 1]);
    if (parsed.unsupported) continue; // unmatchable rule — can't style anything
    // Record the concrete host tag; '*' is excluded (see doc comment).
    if (parsed.needTag && parsed.needTag !== '*') out.add(parsed.needTag);
  }
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
  if (!innerHtml) return { text: '', merged };
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
      textBuf += innerHtml.slice(i, end);
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
    // wave-12 EXTRACTOR-INLINE: pure-inline run merging. When merging is
    // on and this child is a text-only phrase element with no attributes
    // and no tag-targeting rule, splice its inner text into the parent's
    // buffer AT THIS POSITION — reading order preserved, so the preamble
    // becomes '…green square and no red.' instead of '…and .'. The guard
    // on lastCloseStart skips malformed unclosed tags (their trailing text
    // is picked up as plain parent text by the outer loop anyway).
    if (mergeCtx && lastCloseStart >= 0) {
      // Child's inner content — the slice between open and close tags.
      const childInner = innerHtml.slice(tagOpenEnd + 1, lastCloseStart);
      // Attributes parsed from the raw open tag (same scan walkChildren
      // uses) so the predicate here agrees with the tree walker's.
      const attrs = parseAttrsFromTagOpen(tagOpen);
      if (isPureInlineMergeable(tagName, attrs, childInner, mergeCtx.styledTags ?? null)) {
        // Absorb the run; the shared whitespace collapse below normalises
        // any boundary spacing per CSS Text §4.1.
        textBuf += childInner;
        merged++;
      }
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
  if (preserveWhitespace) return { text: decodedBuf, merged };
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
  const collapsed = decodedBuf
    .replace(/[ \t\n\r\f]+/g, ' ')
    .replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, '');
  // Text plus the wave-12 merge count (0 whenever mergeCtx was null).
  return { text: collapsed, merged };
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
  // extractBodyTreeNested so both walkers agree on what "body content" is.
  const bodyMatch = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  let inner = bodyMatch ? bodyMatch[1] : html;
  for (let unwrap = 0; unwrap < 2; unwrap++) {
    const skipped = inner.replace(/^\s*<!doctype\b[^>]*>\s*/i, '').replace(/^\s+/, '');
    const wrapMatch = /^<(html|head|body)\b[^>]*>([\s\S]*?)(?:<\/\1\s*>|$)/i.exec(skipped);
    if (!wrapMatch) break;
    inner = wrapMatch[2];
  }
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
  // Same body-locator + implicit-<html> unwrap logic as extractBodyTree —
  // factored as a sibling rather than shared so each retains its own
  // depth bound + ownText extraction without action-at-a-distance.
  const bodyMatch = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  let inner = bodyMatch ? bodyMatch[1] : html;
  for (let unwrap = 0; unwrap < 2; unwrap++) {
    const skipped = inner.replace(/^\s*<!doctype\b[^>]*>\s*/i, '').replace(/^\s+/, '');
    const wrapMatch = /^<(html|head|body)\b[^>]*>([\s\S]*?)(?:<\/\1\s*>|$)/i.exec(skipped);
    if (!wrapMatch) break;
    inner = wrapMatch[2];
  }
  // Bug 2 fix (selectors__child-indexed-no-parent): each ancestor entry
  // gains a `pos: {sibIndex, sibCount, sibTypeIndex, sibTypeCount, isEmpty,
  // isRoot}` field so non-rightmost pseudo-classes can be evaluated honestly
  // (e.g. `.parent:first-child .target`). The actual ancestor list returned
  // to callers keeps its legacy shape (real ancestors only — no synthetic
  // `:root` entry) so existing selectorMatches() tests stay green. The
  // synthetic root is injected in propsForElement() at match time, which
  // is the only call site that consumes pos data.
  function recurse(fragment, ancestors, depth) {
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
    const kids = rawKids.filter((k) => {
      if (!mergeCtx) return true; // legacy path — no merging, keep all
      const mergeable =
        isPureInlineMergeable(k.tag, k.attrs, k.innerHtml, mergeCtx.styledTags ?? null);
      if (!mergeable) { seenBlock = true; return true; }
      // Depth 0: only the leading run was absorbed (into __text); inline
      // kids after the first kept sibling remain components.
      if (depth === 0) return seenBlock;
      // Depth ≥ 1: the parent's merged ownText carries this kid's text.
      return false;
    });
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
      if (depth + 1 < maxDepth && k.innerHtml) {
        children = recurse(
          k.innerHtml,
          [...ancestors, childAncestor],
          depth + 1,
        );
      }
      pos.isEmpty = children.length === 0 && !ownText;
      const node = {
        tag: k.tag, attrs: k.attrs, raw: k.raw,
        ownText,
        ancestors: ancestors.slice(),
        children,
        pos,
      };
      // wave-12: flag nodes that absorbed inline runs so buildComponents
      // emits the 'inline-run-merged' lossy marker (honest approximation:
      // the run's default styling — bold for <strong> etc. — is dropped).
      // Only set when true, keeping legacy node shapes byte-identical.
      if (ownRes.merged > 0) node.inlineMerged = true;
      out.push(node);
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
 * Synthetic `:root` ancestor injected at match time (NOT in the public
 * ancestors chain). Selectors-4 §3.4.3 + §6.4.1 carve-out: the document
 * root has `isRoot:true` so child-indexed pseudos all match. We model the
 * root as if it were the sole sibling of itself (sibCount/typeCount = 1)
 * so anyone evaluating `:nth-of-type(1)` on it sees the expected truth.
 */
const ROOT_SENTINEL_ANCESTOR = {
  tag: ':root', attrs: {},
  pos: {
    isRoot: true,
    sibIndex: 0, sibCount: 1,
    sibTypeIndex: 0, sibTypeCount: 1,
    isEmpty: false,
  },
};

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
const GENERIC_WRAPPER_TAGS = new Set(['div', 'span']);

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
]);

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
function parseCompound(compound) {
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
        arg = compound.slice(i + 1, k);
        i = k + 1; // past the ')'
      }
      const isFunctional = arg !== null;
      const supported = isFunctional
        ? SUPPORTED_FUNCTIONAL_PSEUDOS.has(name)
        : SUPPORTED_PSEUDOS.has(name);
      if (!supported) { out.unsupported = true; return out; }
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

/**
 * wave-8 (css-flexbox abspos-autopos family + css-break): tokenise a full
 * selector into its compound chain WITH combinators. Selectors-4 §15 defines
 * four combinators: descendant (whitespace), child (`>`), next-sibling (`+`),
 * subsequent-sibling (`~`). We model descendant + child; sibling combinators
 * stay unsupported (the extractor has no sibling-adjacency matcher and a
 * silent downgrade of `div + p` to `div p` would over-match).
 *
 * Why this exists: the previous pipeline (splitCompounds + a blanket
 * `/[>+~]/` reject in selectorMatchesPseudoElement) dropped every rule
 * containing `>` WHOLESALE. WPT leans on `A > B` heavily — e.g. the six
 * css-flexbox/abspos/abspos-autopos-* tests style their subject via
 * `.flex > div { position:absolute; … }`, and losing that rule shipped
 * placeholder children with no styles (the all-platform ~0.90 scores were
 * the corrupted fixture, not the renderers).
 *
 * Returns `{ compounds: string[], combinators: string[] }` where
 * `combinators[i]` relates `compounds[i]` to `compounds[i+1]` and is either
 * `' '` (descendant) or `'>'` (child). Returns null when the selector uses
 * sibling combinators (`+`/`~`) or is malformed (leading/trailing/double
 * `>`), so callers treat the rule as unsupported — same bail contract the
 * old regex reject had.
 *
 * Paren-aware like splitCompounds: whitespace and `>` inside a functional
 * pseudo's argument (`:nth-child(2n + 1)`) never split. Exported for unit
 * testing.
 */
export function splitSelectorChain(sel) {
  // Sibling combinators unsupported — bail early. `+` inside parens (An+B
  // arguments like `:nth-child(2n+1)`) must NOT trip this, so the check
  // walks with paren depth rather than a flat regex.
  const compounds = [];
  const combinators = [];
  let buf = '';
  let depth = 0;
  // The combinator that will bind the NEXT compound to the previous one.
  // null = none seen yet (plain whitespace run → descendant at flush time).
  let pendingChild = false;
  // Flush the accumulated compound buffer, recording the combinator that
  // separated it from the previous compound (child if a `>` was seen in
  // the separator run, descendant otherwise).
  const flush = () => {
    if (!buf) return true;
    if (compounds.length > 0) combinators.push(pendingChild ? '>' : ' ');
    else if (pendingChild) return false; // leading `>` — malformed
    compounds.push(buf);
    buf = '';
    pendingChild = false;
    return true;
  };
  for (let i = 0; i < sel.length; i++) {
    const c = sel[i];
    if (c === '(') { depth++; buf += c; continue; }
    if (c === ')') { depth--; buf += c; continue; }
    if (depth > 0) { buf += c; continue; } // inside a functional pseudo arg
    if (c === '+' || c === '~') return null; // sibling combinators — unsupported
    if (c === '>') {
      // Child combinator between compounds. Flush whatever compound was
      // being read; a second `>` before any new compound text (`a >> b`)
      // is malformed CSS — bail.
      if (!flush()) return null;
      if (pendingChild) return null; // double `>` with no compound between
      if (compounds.length === 0) return null; // leading `>` — malformed
      pendingChild = true;
      continue;
    }
    if (/\s/.test(c)) {
      // Top-level whitespace: compound boundary (descendant combinator
      // unless a `>` already marked this separator run as child).
      if (!flush()) return null;
      continue;
    }
    buf += c;
  }
  if (!flush()) return null;
  // Trailing `>` with no right-hand compound (`.a >`) is malformed.
  if (pendingChild) return null;
  return { compounds, combinators };
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
 * Returns null when `pos` is missing (caller can't evaluate without
 * position data — bail and treat the selector as non-matching).
 */
function evalPseudo(pseudo, pos, tag = null, attrs = null, ctx = {}) {
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
          const r = compoundMatches(s, tag, attrs ?? {}, pos, ctx);
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
          const sibPos = { isRoot: false, sibIndex: s, sibCount: sibs.length };
          const matches = ofSelectors.some((selStr) => {
            const r = compoundMatches(selStr, sib.tag, sib.attrs ?? {}, sibPos, ctx);
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
              const sibPos = { isRoot: false, sibIndex: s, sibCount: sibs.length };
              const matches = ofSelectors.some((selStr) => {
                const r = compoundMatches(selStr, sib.tag, sib.attrs ?? {}, sibPos, ctx);
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
 * NOTE: this function only checks the host-element side of the compound;
 * the `pseudoElement` field on parseCompound's output is observed by
 * selectorMatchesPseudoElement(), not here. Returning `true` here means
 * "the compound's host-side filters match"; the caller may still need to
 * dispatch the rule onto a pseudo-element bucket.
 */
function compoundMatches(compound, tag, attrs, pos = null, ctx = {}) {
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
    const result = evalPseudo(ps, pos, tag, attrs, ctx);
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
 *
 * Attribute selectors / `+`/`~` sibling combinators are unsupported —
 * when they appear anywhere in the selector we skip the rule.
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
  const lastResult = compoundMatches(last, tag, attrs, pos, ctx);
  if (lastResult !== true) return null;
  const pe = parsedLast.pseudoElement || '';
  // Single-compound selector — done.
  if (compounds.length === 1) return pe;
  // Pre-flight every non-rightmost compound for unsupported syntax so the
  // rule bails uniformly (legacy behaviour: unsupported ANYWHERE in the
  // chain → null, never a partial match).
  for (let ci = 0; ci < compounds.length - 1; ci++) {
    if (parseCompound(compounds[ci]).unsupported) return null;
  }
  // Without ancestors, preserve the legacy "rightmost compound only"
  // fallback for pure-DESCENDANT chains so old call sites (and the
  // existing test 'descendant — applies last compound') keep passing.
  // Child chains get NO such degrade: `.flex > div` matching any bare
  // `<div>` would be a structural over-match, so we bail honestly.
  if (!ancestors) return combinators.includes('>') ? null : pe;
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
   * Each ancestor entry carries its own position metadata so pseudos on
   * non-rightmost compounds (e.g. `:root:first-child .target`) evaluate
   * against the right element. The synthetic `:root` sentinel ancestor
   * prepended in propsForElement has `isRoot: true` so the Selectors-4
   * §6.4.1 carve-out fires.
   */
  const matchPrefix = (ci, maxIdx) => {
    if (ci < 0) return true; // whole chain consumed — match
    const rel = combinators[ci]; // relates compounds[ci] → compounds[ci+1]
    if (rel === '>') {
      // Child combinator: compounds[ci] must match the IMMEDIATE parent
      // (the highest ancestor index still available). No scan, no
      // backtracking at this step — the child relation is exact.
      if (maxIdx < 0) return false; // ran out of ancestors
      const a = ancestors[maxIdx];
      if (compoundMatches(compounds[ci], a.tag, a.attrs, a.pos ?? null, ctx) !== true) return false;
      return matchPrefix(ci - 1, maxIdx - 1);
    }
    // Descendant combinator: try every remaining ancestor from nearest to
    // farthest, backtracking into the rest of the chain on each candidate.
    for (let j = maxIdx; j >= 0; j--) {
      const a = ancestors[j];
      if (compoundMatches(compounds[ci], a.tag, a.attrs, a.pos ?? null, ctx) === true
          && matchPrefix(ci - 1, j - 1)) return true;
    }
    return false;
  };
  return matchPrefix(compounds.length - 2, ancestors.length - 1) ? pe : null;
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
  // Bug 2 fix: inject the synthetic `:root` ancestor at the head of the
  // chain so selectors like `:root:first-child #a` find a `:root` to
  // match against. We only do this when an ancestor chain was supplied
  // (callers that pass `null` are the legacy direct-call test surface
  // which wouldn't expect the sentinel to appear in their fixtures).
  const chain = ancestors ? [ROOT_SENTINEL_ANCESTOR, ...ancestors] : null;
  for (const r of rules) {
    const m = selectorMatchesPseudoElement(r.selector, tag, attrs, chain, pos, ctx);
    if (m === null) continue;
    matchedRules.push(r);
    if (m === '') {
      // Host-element match — merge into the regular props bag.
      Object.assign(props, r.props);
    } else {
      // Pseudo-element match — merge into the bucket for that pe name.
      if (!pseudo[m]) pseudo[m] = {};
      Object.assign(pseudo[m], r.props);
    }
  }
  // Inline style="..." trumps everything.
  if (attrs.style) {
    for (const decl of attrs.style.split(';')) {
      const colon = decl.indexOf(':');
      if (colon < 0) continue;
      const k = decl.slice(0, colon).trim();
      const v = decl.slice(colon + 1).trim().replace(/\s*!important\s*$/i, '').trim();
      if (k && v && !k.startsWith('--')) props[k] = v;
    }
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
 * Returns `{ props, matchedRules }`. Cascade order matches parseCss output
 * (last write wins for same-specificity).
 */
export function propsForBodyRoot(rules) {
  const props = {};
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
    Object.assign(props, r.props);
  }
  return { props, matchedRules };
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
//   - zero or positive delay (value at t=0 is the pre-animation state or
//     depends on fill-mode backwards — timeline-dependent);
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

// The animation longhands the sampler understands + drops after baking.
// animation-composition/range are listed as DROP-ON-SAMPLE too: if present
// they'd have parsed to non-default composite behaviour we don't model, so
// parseAnimationDecl refuses to sample when they carry non-initial values.
const ANIMATION_LONGHANDS = new Set([
  'animation', 'animation-name', 'animation-duration', 'animation-delay',
  'animation-timing-function', 'animation-iteration-count',
  'animation-direction', 'animation-fill-mode', 'animation-play-state',
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
  const re = /@keyframes\s+([A-Za-z_][\w-]*)\s*\{/g;
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
  const spec = { name: null, durationMs: 0, delayMs: 0, easing: 'ease',
                 iterations: 1, direction: 'normal' };
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
      // <single-animation-fill-mode>: parsed to keep the token from being
      // mistaken for the name; the VALUE is irrelevant inside the active
      // phase (css-animations-1 §5.4) so it isn't recorded.
      if (!gotFill && ['none', 'forwards', 'backwards', 'both'].includes(low)) {
        gotFill = true; continue;
      }
      // <single-animation-play-state>: paused samples identically at t=0
      // (the WPT engineering makes the value time-stable either way), so
      // like fill-mode it's consumed but not recorded.
      if (!gotPlay && ['running', 'paused'].includes(low)) {
        gotPlay = true; continue;
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
  // fill-mode / play-state longhands: consumed for the sawAny signal only —
  // both are provably irrelevant to a sample strictly inside the active
  // phase (fill: §5.4; paused: t=0 value identical), mirroring the
  // shorthand slots above.
  if (getProp('animation-fill-mode') !== undefined) sawAny = true;
  if (getProp('animation-play-state') !== undefined) sawAny = true;
  if (!sawAny) return null;                        // no animation declarations at all
  return spec;
}

/**
 * THE SAMPLER. Given a component's merged props bag and the stylesheet's
 * parsed @keyframes map, decide whether the declared animation is the WPT
 * time-stable negative-delay pattern and, if so, statically compute every
 * keyframed property at `progress = -delay / duration`.
 *
 * Returns `{ baked: {prop: cssValue}, dropped: [animPropKeys] }` on
 * success, or null when ANY scope-boundary rule (see the section banner)
 * fires — null means "extract verbatim exactly as before this wave";
 * partial bakes never happen (one uninterpolable property aborts the
 * whole sample, keeping the fixture honest). Exported for tests.
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
  // ★ THE boundary rule: only a strictly NEGATIVE delay is time-stable —
  // zero/positive delays mean the screenshot races the timeline (out of
  // scope by design; the declarations ride through verbatim).
  if (!(anim.delayMs < 0)) return null;
  // Web-Animations §4.8.3.1: at local time 0, iteration progress is
  // (-delay)/duration for the first iteration.
  const progress = -anim.delayMs / anim.durationMs;
  // Must land strictly INSIDE the active phase's first iteration: at 0/1
  // or beyond, the rendered value depends on fill-mode / iteration
  // compositing (css-animations-1 §5.4) — out of scope.
  if (!(progress > 0 && progress < 1)) return null;
  if (!(progress < anim.iterations)) return null;  // count must cover the sample point
  if (anim.direction !== 'normal') return null;    // directed-progress mapping — boundary
  const elementTf = parseTimingFunction(anim.easing); // element-level easing (may be null)
  const baked = {};                                // sampled property → CSS value
  // Union of properties any frame declares = the animated property set.
  const animatable = new Set();
  for (const f of frames) for (const k of Object.keys(f.props)) animatable.add(k);
  if (animatable.size === 0) return null;          // keyframes body was all-invalid/empty
  for (const prop of animatable) {
    // css-animations-1 §4 keyframe selection is PER PROPERTY: only frames
    // declaring this property participate in its segment lookup.
    const pf = frames.filter((f) => prop in f.props);
    // prev = nearest frame at-or-before progress; next = nearest at-or-after.
    let prev = null, next = null;                  // bracketing frames
    for (const f of pf) {                          // frames are offset-sorted ascending
      if (f.offset <= progress + KF_EPS) prev = f; // keeps advancing → last one ≤ progress
      if (next === null && f.offset >= progress - KF_EPS) next = f; // first one ≥ progress
    }
    // Missing bracket ⇒ the §4 fallback is the element's UNDERLYING value,
    // which static extraction can't know — abort the whole sample.
    if (!prev || !next) return null;
    if (prev === next || Math.abs(next.offset - prev.offset) < KF_EPS) {
      baked[prop] = prev.props[prop];              // progress sits ON a frame — exact value, no easing
      continue;
    }
    // Segment-local progress in [0,1] between the bracketing frames.
    const local = (progress - prev.offset) / (next.offset - prev.offset);
    // css-animations-1 "Timing functions for keyframes": a tf declared ON
    // the previous keyframe governs this segment; else the element's.
    const tf = prev.easing !== undefined ? parseTimingFunction(prev.easing) : elementTf;
    if (!tf) return null;                          // unsupported easing — refuse, don't mis-ease
    const eased = evalTimingFunction(tf, local);   // eased (possibly overshooting) progress
    const v = lerpCssValue(prev.props[prop], next.props[prop], eased); // interpolate the pair
    if (v === null) return null;                   // uninterpolable value family — abort whole sample
    baked[prop] = v;                               // record the sampled value
  }
  return { baked, dropped: animKeys };             // success — caller bakes + drops
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
 * every string property value. Components left with un-deliverable url()
 * refs gain `_lossy: true` + 'requires-bundled-asset' in `_lossyReasons`;
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
  return { inlined: totalInlined, unresolved: totalUnresolved };
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
export async function extractFixture(testRel) {
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
  const html = await fs.readFile(testAbs, 'utf8');
  const cleaned = stripComments(html);

  const inlineCss = extractInlineStyle(cleaned);
  const linkedHrefs = extractLinkedStylesheets(cleaned);
  let allCss = inlineCss;
  for (const href of linkedHrefs) {
    // Skip http(s) — bucketer should have filtered already.
    if (/^https?:\/\//i.test(href)) continue;
    const cssAbs = href.startsWith('/')
      ? join(WPT_DIR, href.slice(1))
      : resolve(dirname(testAbs), href);
    try {
      const cssRaw = await fs.readFile(cssAbs, 'utf8');
      allCss += '\n' + stripComments(cssRaw);
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

  const stem = basename(testRel, '.html');
  const section = specSectionOf(testRel);

  // wave-13: pass the keyframes map so the sampler can run (5th arg; the
  // 4th stays the ctx default — buildComponents harvests :defined itself).
  const built = buildComponents(cleaned, rules, stem, null, keyframes);

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
    let refCss = refInline;
    for (const href of refLinks) {
      if (/^https?:\/\//i.test(href)) continue;
      const cssAbs = href.startsWith('/')
        ? join(WPT_DIR, href.slice(1))
        : resolve(dirname(refAbs), href);
      try {
        refCss += '\n' + stripComments(await fs.readFile(cssAbs, 'utf8'));
      } catch { /* tolerate missing */ }
    }
    const refRules = parseCss(refCss);
    // wave-13: refs get the same sampler over THEIR stylesheet (refs of the
    // time-stable family are static paint, so this is normally a no-op —
    // but symmetric handling keeps the test/ref pair comparable if a ref
    // ever animates).
    const refKeyframes = parseKeyframes(refCss);
    const refBuilt = buildComponents(refCleaned, refRules, `${stem}__ref`, null, refKeyframes);
    refFixture = {
      _wpt: { ref: refRel, of: testRel, specSection: section },
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

  return { fixture, refFixture, refRel, section, stem };
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
  const effectiveCtx = ctx ?? { definedTags: collectDefinedTags(cleaned) };
  // wave-12 EXTRACTOR-INLINE: merge context for pure-inline run merging.
  // styledTags guards the merge — any tag a rule directly targets keeps
  // its child-component path so the declarations under test survive (see
  // collectStyledTags). Merging is ALWAYS on in production extraction;
  // only legacy direct calls to the walkers (unit tests) run without it.
  const mergeCtx = { styledTags: collectStyledTags(rules) };

  // Bug 3 + 4: body-root component for body-scope CSS. Stays as a flat
  // top-level entry (it represents <body> itself; the body's element
  // children become the __0/__1/... siblings in the components map and
  // their descendants nest under their respective .children — there's no
  // need to also nest the __0 siblings inside __body because every
  // renderer already iterates the components map at the top level).
  const root = propsForBodyRoot(rules);

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

  if (root.matchedRules > 0 && Object.keys(root.props).length > 0) {
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
    if (reasons.length) {
      lossyOverall = true;
      reasons.forEach((r) => lossyReasonsOverall.add(r));
    }
    const cmp = { properties: root.props };
    if (reasons.length) {
      cmp._lossy = true;
      cmp._lossyReasons = reasons;
    }
    // Tag the synthetic root so downstream consumers can recognise it.
    cmp._role = 'body-root';
    components[`${idPrefix}__body`] = cmp;
  }

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
    components[`${idPrefix}__text`] = cmp;
  }

  // EXTFIX-A: walk subtree as a NESTED tree (not flat). depth ≤ 5 covers
  // the css-grid abspos / css-overflow / css-contain patterns documented
  // in swarm-001 without unbounded blow-up. wave-12: the mergeCtx switches
  // on pure-inline run merging (see extractBodyTreeNested's doc comment).
  const tree = extractBodyTreeNested(cleaned, 5, mergeCtx);

  if (tree.length === 0) {
    // No renderable elements at all. Keep the legacy degenerate
    // placeholder so the capture pipeline still produces a row — unless
    // the body-root or the wave-11 leading-text component already gives
    // the canvas real content (a text-only body now yields its prose as
    // `__text` instead of a phantom 100x100 box).
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
  function buildNode(node, id) {
    const { props, matchedRules, pseudo } = propsForElement(
      rules, node.tag, node.attrs, node.ancestors, node.pos ?? null, effectiveCtx,
    );
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
    if (matchedRules === 0 && Object.keys(props).length === 0 && !node.ownText) {
      // Bug 1 honest fallback: no matching rules + no inline style + no
      // own text — this is the genuine "human instruction" case (or
      // scaffolding wrappers). Still emit a 100x100 placeholder so the
      // canvas is non-degenerate but downstream graders can recognise it
      // as an empty node. NB: when the element HAS own text we keep the
      // empty properties bag — the renderer lays out the text and the
      // capture is meaningful even without explicit width/height.
      cmp.properties = { width: '100px', height: '100px' };
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
        // wave-13 KEYFRAMES-SAMPLER: pseudo-element bags sample too (WPT
        // has ::before animation variants of the same time-stable family).
        const peSampled = bakeSampledAnimation(peProps, keyframes);
        const peReasons = lossyReasonsFor(peProps);
        // Same LOUD marker contract as the host-element path above.
        if (peSampled) peReasons.push('sampled-animation');
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
    // Bug 1 fix (css3-counter-styles-101): carry forward the originating
    // HTML element identity so the renderer can branch on it (e.g. wrap
    // children of `<ol>` in `<li>` boxes that trigger native marker
    // generation). We emit `_tag` only for non-generic tags — `div` and
    // `span` are the IR's default container shapes and tagging them would
    // bloat every visual-test-style fixture without conveying new info.
    // Sister F-RENDERER consumes `_tag` as a lowercase HTML element name.
    if (node.tag && !GENERIC_WRAPPER_TAGS.has(node.tag)) {
      cmp._tag = node.tag;
    }
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
      node.children.forEach((child, i) => {
        const childId = `${id}__${i}`;
        const childCmp = buildNode(child, childId);
        childMap[childId] = { id: childId, ...childCmp };
      });
      cmp.children = childMap;
    }
    return cmp;
  }

  tree.forEach((node, idx) => {
    const id = `${idPrefix}__${idx}`;
    components[id] = buildNode(node, id);
  });

  return {
    components,
    lossyOverall,
    lossyReasons: [...lossyReasonsOverall],
  };
}

/** Persist the {fixture, refFixture} pair into fixtures/wpt/<section>/. */
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

// ── specSection helper (mirrors bucket-wpt.mjs) ──────────────────────────────
function specSectionOf(testRel) {
  const parts = testRel.split('/');
  if (parts.length >= 2 && parts[0] === 'css') return parts[1] || 'css';
  return 'css';
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
  const inputs = process.argv.slice(2).filter((a) => a !== '--post-load');
  if (inputs.length === 0) {
    console.error('usage: extract-fixture.mjs [--post-load] <relative-test-path>...');
    console.error('       (paths are repo-relative, e.g. "css/css-color/a98rgb-001.html")');
    process.exit(1);
  }
  // Lazily-loaded post-load module handle (null while disabled).
  const postLoad = postLoadEnabled ? await import('./post-load-extract.mjs') : null;
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
        if (postLoad && await postLoad.isWallTagged(rel)) {
          const outcome = await postLoad.postLoadAugmentFixture(result.fixture, rel);
          postLoadNote = ` [post-load: ${outcome.status}${outcome.reason ? ` — ${outcome.reason}` : ''}]`;
        }
        const written = await writeFixturePair(result);
        console.log(`extracted ${rel} → ${relative(REPO_ROOT, written.testPath)}` +
                    (written.refPath ? ` (+ ref)` : ' (ref skipped)') + postLoadNote);
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
