#!/usr/bin/env node
//
// testing/titan/extract-fixture.mjs — Phase 1 WPT extractor.
//
// Implements TITAN_ARCHITECTURE.md Section 4.2: walk a single WPT test HTML
// (or a list of them), parse inline <style> + linked .css, and emit a
// Style-Converter fixture JSON in the existing IR shape:
//
//     { components: { "<id>": { properties: { … } } }, _wpt: { … } }
//
// The output mirrors examples/properties/<category>/<name>.json so the
// existing test-all.sh + compare-screenshots pipeline can ingest it
// unchanged.
//
// Usage:
//   node testing/titan/extract-fixture.mjs <relative-test-path>...
//
//   - Each path is repo-relative under WPT (e.g. "css/css-color/a98rgb-001.html")
//     matching the format in testing/wpt-buckets.json's `buckets.A` array.
//
// Outputs:
//   examples/wpt/<spec-section>/<test-stem>.json          (the test)
//   examples/wpt/<spec-section>/<test-stem>__ref.json     (the reference)
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

import { promises as fs } from 'node:fs';
import { resolve, dirname, join, relative, basename, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'testing', 'wpt');
const OUT_ROOT   = process.env.WPT_FIXTURES_ROOT ?? join(REPO_ROOT, 'examples', 'wpt');

// ── Bucket-index lookup (for lossy markers) ──────────────────────────────────
//
// Loading the bucket index up-front avoids re-classifying each input. Lazy
// init so the test suite can run without the index file present.
let _bucketIdx = null;
async function bucketIndex() {
  if (_bucketIdx) return _bucketIdx;
  const p = join(REPO_ROOT, 'testing', 'wpt-buckets.json');
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
 *  trip on commented-out blocks. */
export function stripComments(html) {
  // HTML comments first (can contain `*/`).
  let out = html.replace(/<!--[\s\S]*?-->/g, '');
  // CSS comments inside <style> blocks.
  out = out.replace(/\/\*[\s\S]*?\*\//g, '');
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
//     already screens those)
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
 * Exported so the unit tests can pin the contract.
 */
export function extractOwnText(innerHtml) {
  if (!innerHtml) return '';
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
        cursor = c.index + c[0].length;
      }
    }
    i = cursor;
  }
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
  const collapsed = textBuf
    .replace(/[ \t\n\r\f]+/g, ' ')
    .replace(/^[ \t\n\r\f]+|[ \t\n\r\f]+$/g, '');
  return collapsed;
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
 */
export function extractBodyTreeNested(html, maxDepth = 5) {
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
    const kids = walkChildren(fragment).filter((k) => !HEAD_ONLY_TAGS.has(k.tag));
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
      const ownText = extractOwnText(k.innerHtml);
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
      if (depth + 1 < maxDepth && k.innerHtml) {
        children = recurse(
          k.innerHtml,
          [...ancestors, childAncestor],
          depth + 1,
        );
      }
      pos.isEmpty = children.length === 0 && !ownText;
      out.push({
        tag: k.tag, attrs: k.attrs, raw: k.raw,
        ownText,
        ancestors: ancestors.slice(),
        children,
        pos,
      });
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
  const scriptRe = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
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
const HEAD_ONLY_TAGS = new Set([
  'title', 'meta', 'link', 'style', 'script', 'base', 'head',
]);

// Bug 1 fix (css3-counter-styles-101): tags considered "generic" — i.e.
// the renderer treats them as a plain styled container. We DON'T emit
// `_tag` for these so the existing hand-authored visual-test/examples/*
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
 *
 * Pseudo-classes / attribute selectors / `>`/`+`/`~` combinators are
 * unsupported — when they appear in the rightmost compound we skip the rule.
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
  // Reject child / sibling combinators anywhere in the full selector — we
  // don't model adjacency. The split below would otherwise drop them
  // silently and let `div + p` match a bare `<p>`.
  if (/[>+~]/.test(sel)) return null;
  const compounds = splitCompounds(sel);
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
  // Descendant chain: each prior compound (in order) must match some
  // ancestor that comes AFTER the previous match (preserves nesting order).
  // Without ancestors, preserve the legacy "rightmost compound only"
  // behaviour so old call sites (and the existing test
  // 'descendant — applies last compound') keep passing.
  if (!ancestors) return pe;
  const ancestorChain = ancestors;
  let cursor = 0;
  for (let ci = 0; ci < compounds.length - 1; ci++) {
    const c = compounds[ci];
    let matched = false;
    while (cursor < ancestorChain.length) {
      const a = ancestorChain[cursor++];
      // Each ancestor entry carries its own position metadata so pseudos
      // on non-rightmost compounds (e.g. `:root:first-child .target`)
      // evaluate against the right element. The synthetic `:root`
      // sentinel ancestor we prepend in extractBodyTreeNested has
      // `isRoot: true` so the Selectors-4 §6.4.1 carve-out fires.
      const r = compoundMatches(c, a.tag, a.attrs, a.pos ?? null, ctx);
      if (r === null) return null; // unsupported syntax — bail
      if (r === true) { matched = true; break; }
    }
    if (!matched) return null;
  }
  return pe;
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
    // Reject combinators anywhere — same conservatism as selectorMatches.
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

  const fuzzy = extractFuzzy(cleaned);
  const refHref = extractRefHref(cleaned);
  if (!refHref) throw new Error(`extract-fixture: ${testRel} has no rel="match" link`);
  const refAbs = refHref.startsWith('/')
    ? join(WPT_DIR, refHref.slice(1))
    : resolve(dirname(testAbs), refHref);
  const refRel = relative(WPT_DIR, refAbs).split(sep).join('/');

  const stem = basename(testRel, '.html');
  const section = specSectionOf(testRel);

  const built = buildComponents(cleaned, rules, stem);

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
    const refBuilt = buildComponents(refCleaned, refRules, `${stem}__ref`);
    refFixture = {
      _wpt: { ref: refRel, of: testRel, specSection: section },
      components: refBuilt.components,
    };
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
export function buildComponents(cleaned, rules, idPrefix, ctx = null) {
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

  // Bug 3 + 4: body-root component for body-scope CSS. Stays as a flat
  // top-level entry (it represents <body> itself; the body's element
  // children become the __0/__1/... siblings in the components map and
  // their descendants nest under their respective .children — there's no
  // need to also nest the __0 siblings inside __body because every
  // renderer already iterates the components map at the top level).
  const root = propsForBodyRoot(rules);
  if (root.matchedRules > 0 && Object.keys(root.props).length > 0) {
    const reasons = lossyReasonsFor(root.props);
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

  // EXTFIX-A: walk subtree as a NESTED tree (not flat). depth ≤ 5 covers
  // the css-grid abspos / css-overflow / css-contain patterns documented
  // in swarm-001 without unbounded blow-up.
  const tree = extractBodyTreeNested(cleaned);

  if (tree.length === 0) {
    // No renderable elements at all (extremely rare — body had only text).
    // Keep the legacy degenerate placeholder so the capture pipeline still
    // produces a row.
    if (!components[`${idPrefix}__body`]) {
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
    const reasons = lossyReasonsFor(props);
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
    // ~99% case in the existing examples/wpt corpus). Per-pseudo lossy
    // markers also propagate so the dashboard's lossy lane sees them.
    if (pseudo) {
      const pseudoOut = {};
      for (const peName of Object.keys(pseudo)) {
        const peProps = pseudo[peName];
        if (!peProps || Object.keys(peProps).length === 0) continue;
        const peReasons = lossyReasonsFor(peProps);
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
    // examples/properties/*) stay byte-identical post-rollout.
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

/** Persist the {fixture, refFixture} pair into examples/wpt/<section>/. */
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
  const inputs = process.argv.slice(2);
  if (inputs.length === 0) {
    console.error('usage: extract-fixture.mjs <relative-test-path>...');
    console.error('       (paths are repo-relative, e.g. "css/css-color/a98rgb-001.html")');
    process.exit(1);
  }
  let ok = 0, fail = 0;
  for (const rel of inputs) {
    try {
      const result = await extractFixture(rel);
      const written = await writeFixturePair(result);
      console.log(`extracted ${rel} → ${relative(REPO_ROOT, written.testPath)}` +
                  (written.refPath ? ` (+ ref)` : ' (ref skipped)'));
      ok++;
    } catch (err) {
      console.error(`FAIL ${rel}: ${err.message ?? err}`);
      fail++;
    }
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
