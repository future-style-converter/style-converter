//
// tools/titan/generated-content-bake.mjs — wave-36 lane M3.
//
// THE HOLE THIS CLOSES — measured, in the pipeline, not in the spec.
// ------------------------------------------------------------------
// swarm-003 Bug 1 taught the extractor to route `div::before { … }` rules
// into `cmp._pseudo.<name>.properties`, and the converter forwards that bag
// VERBATIM to IR v2 `pseudos` (schema/ir-v2.schema.json validates the slot
// payload permissively — "extractor-owned shape"). Every consumer then does
// the obvious thing with it: runtimes/web/src/renderer/PseudoNodeRenderer.ts
// emits a real `<span>` and drops the raw declarations — INCLUDING
// `content` — into that span's INLINE STYLE.
//
// And `content` on a non-pseudo element does not paint text. Measured
// directly against the capture browser (_diag36/M3/probe3.mjs):
//
//   <span style='content: "a" "a" "a"'>   →  getComputedStyle().content
//                                            === '"aaa"'   (parsed fine)
//                                         →  getBoundingClientRect()
//                                            === 0 × 0     (paints nothing)
//
// css-content-3 §2.1 element replacement is implemented in Blink for
// <image> values only; a <string> computes and then renders nothing. So the
// declared text existed on the wire, reached the DOM, and vanished at the
// last hop. The renderer ALREADY has the receiving end for the repair —
// `renderPseudoNode` reads `p._text ?? p.text` and passes it as the span's
// child — and nothing was ever producing it.
//
// MEASURED POPULATION (every `pseudos.*` bag in the archived wave35-webmap
// per-section IRs, 65 sections): 3,989 pseudo bags, 3,695 carrying a
// `content` declaration. Of those:
//     460  pure <string> sequences  (174 of them the empty string)
//   3,151  counter() / counters()   — NOT this lane (see REFUSALS)
//      33  open-quote / close-quote — this lane, resolved below
//       6  url() / image()          — element replacement; Blink DOES paint
//                                     these, so text would DOUBLE them
// i.e. ~286 non-empty string texts plus the quote family light up, spread
// over css-pseudo (155), css-display (41), css-anchor-position (39),
// css-inline (36), css-contain (26), css-align (22), css-flexbox (21), …
//
// WHY A SEPARATE MODULE, and why a POST-PASS over the built tree.
// `open-quote` cannot be resolved where the other pseudo bakes live
// (buildComponents' per-node loop): css-content-3 §2.1 defines the quote
// depth as a DOCUMENT-ORDER counter threaded ::before → children → ::after,
// so it needs a walk that owns the whole tree, not a per-node hook. Making
// it a post-pass also keeps the one place a bag is refused (see REFUSALS) in
// a single file with its own tests, next to attr-bake.mjs and
// counter-style-bake.mjs — the two precedents for "resolve a css-values/
// css-content function the wire cannot carry, and mark the fixture lossy".
//
// REFUSALS — the whole correctness story. When ANY component of a `content`
// value is something this module cannot resolve to literal text, it emits
// NO `_text` at all for that bag, leaving the fixture byte-identical to
// before this lane. Never a partial string:
//   * counter() / counters()  — needs the css-lists-3 counter tree, which is
//     a different (and much larger) lane. counter-style-bake.mjs already
//     resolves the ::marker sub-case through `_markerText`.
//   * url() / image() / image-set() / linear-gradient() / element() — these
//     are <content-replacement>, and Blink DOES paint them through the
//     inline-style path today. Adding text would paint the image AND the
//     text.
//   * attr() — by the time this runs, attr-bake.mjs has already replaced
//     every resolvable attr() with a literal <string>, so a SURVIVING
//     attr() is one it declined (unresolved / IACVT). Inventing a value
//     here would contradict that decision.
//   * var() / env() / calc() and any other function token — unresolvable by
//     construction ("null means runtime-dependent", schema/spec/02-values).
//   * `contents`, target-counter(), leader() — real css-content-3 values we
//     do not model.
//   * `normal` / `none` — these generate NO BOX at all; there is no text to
//     emit and emitting `""` would only churn fixture bytes.
//
// The quote-depth counter is maintained across refusals (a recognised quote
// keyword still moves the depth even when its bag is refused for an
// unrelated token), so one unresolvable sibling cannot silently mis-number
// every later quote in the document.
//
// LOSSY MARKER: a baked bag gets `_lossyReasons: ['generated-content-baked']`
// — the same LOUD provenance contract every other bake follows. The marker
// says "this text was computed by the extractor, not read off a runtime",
// which is exactly what a dashboard needs to know.

/** The lossy marker stamped on a pseudo bag whose text this module resolved.
 *  Exported so extract-fixture and the unit pins share one spelling. */
export const GENERATED_CONTENT_BAKED_REASON = 'generated-content-baked';

// ── The FOREIGN-NAMESPACE refusal ───────────────────────────────────────────
//
// A pseudo-element on an SVG or MathML element generates NO BOX. SVG 2 §6.1
// and MathML Core §3 both define their own layout for their own elements and
// neither adopts CSS box generation for ::before/::after, and Blink follows
// them: the declaration COMPUTES, and nothing is laid out.
//
// PROBED, not assumed (_diag36/M3/probe4.mjs, the capture browser):
//   <math id=m>  #m:before{content:"MATHTEXT"}  → rect 0×0, body innerText ''
//   <svg id=s width=10 height=10>  #s:before{…} → rect exactly 10×10, no text
//   <div id=d>   #d:before{content:"DIVTEXT"}   → rect 800×18, text painted
// getComputedStyle(el,'::before').content returns the string in ALL THREE —
// the computed value is not the question, box generation is.
//
// MEASURED COST OF NOT HAVING THIS: css-content/attr-case-sensitivity-003 is
// two `<math definitionURL=…>` elements with `:before { content: attr(…) }`.
// Its browser-ref paints NOTHING (ink 0.00%), and baking the text moved the
// capture to ink 0.42% — a PASS at 1.0000 turned into a 0.9813 fail. The
// text was correctly resolved and correctly not rendered by the browser.
//
// The flag is inherited down the walk: a descendant of <svg>/<math> is in the
// same namespace. `<foreignObject>` re-enters the HTML namespace and would
// deserve to re-enable the bake; no bucket-A test reaches that shape today,
// so it stays a named gap rather than untested code.
const FOREIGN_NAMESPACE_ROOT_TAGS = new Set(['svg', 'math']);

/** True when this component's `_tag` opens a non-HTML namespace subtree. */
export function opensForeignNamespace(cmp) {
  const tag = cmp?._tag;
  return typeof tag === 'string' && FOREIGN_NAMESPACE_ROOT_TAGS.has(tag.toLowerCase());
}

// ── CSS <string> unescaping (CSS Syntax 3 §4.3.7 consume-an-escaped-code-point)
//
// The corpus writes quote marks as `"\201C"` at least as often as it writes
// the literal character, so an unescaper is not optional: `content: "\201C"`
// must bake U+201C, not the five characters `\201C`.
const HEX_ESCAPE_RX = /^\\([0-9a-fA-F]{1,6})(\r\n|[ \n\r\t\f])?/;

/**
 * Decode one CSS string body (the text BETWEEN the quote marks) per CSS
 * Syntax 3 §4.3.7: `\<1-6 hex><optional single whitespace>` is a code point,
 * `\<newline>` is a line continuation (nothing), and `\<anything else>` is
 * that character literally. Exported for the pins.
 */
export function unescapeCssString(body) {
  let out = '';
  let i = 0;
  while (i < body.length) {
    const ch = body[i];
    if (ch !== '\\') { out += ch; i++; continue; }
    const rest = body.slice(i);
    const hex = HEX_ESCAPE_RX.exec(rest);
    if (hex) {
      const cp = parseInt(hex[1], 16);
      // §4.3.7: zero, a surrogate, or a value above the maximum code point
      // all become U+FFFD rather than throwing.
      out += (cp === 0 || (cp >= 0xD800 && cp <= 0xDFFF) || cp > 0x10FFFF)
        ? '�'
        : String.fromCodePoint(cp);
      i += hex[0].length;
      continue;
    }
    // Line continuation — a backslash directly before a newline emits nothing.
    const nl = /^\\(\r\n|[\n\r\f])/.exec(rest);
    if (nl) { i += nl[0].length; continue; }
    // EOF after a backslash: §4.3.7 says emit U+FFFD (a parse error, but not
    // a crash). Any other escaped character is itself.
    if (i + 1 >= body.length) { out += '�'; i++; continue; }
    out += body[i + 1];
    i += 2;
  }
  return out;
}

// The four quote keywords (css-content-3 §2.1 `<quote>`). Split into the
// pair that EMITS a mark and the `no-` pair that only moves the depth.
const QUOTE_KEYWORDS = new Set([
  'open-quote', 'close-quote', 'no-open-quote', 'no-close-quote',
]);

/**
 * Tokenise a `content` value into the component list css-content-3 §2.1
 * describes, or return null when any component is one this module refuses
 * (see the REFUSALS paragraph in the header).
 *
 * Returns an array of `{ kind: 'string', value }` / `{ kind: 'quote', name }`.
 * The `/ <string>` alt-text tail (css-content-3 §2.3) is DISCARDED, not
 * refused: alt text is for assistive technology and is never painted, so a
 * value that carries one still has a fully resolvable painted half.
 *
 * Exported for the pins.
 */
export function parseContentComponents(value) {
  if (typeof value !== 'string') return null;
  const s = value;
  const out = [];
  let i = 0;
  const n = s.length;
  while (i < n) {
    const ch = s[i];
    // Whitespace between components carries no text of its own (a literal
    // space must be written inside a <string>), so it is simply skipped.
    if (/\s/.test(ch)) { i++; continue; }
    // The alt-text separator ends the painted list (§2.3).
    if (ch === '/') break;
    if (ch === '"' || ch === '\'') {
      // Consume to the matching unescaped quote. An UNTERMINATED string is a
      // parse error we refuse rather than guess the end of.
      let j = i + 1;
      let body = '';
      let closed = false;
      while (j < n) {
        if (s[j] === '\\') { body += s[j] + (s[j + 1] ?? ''); j += 2; continue; }
        if (s[j] === ch) { closed = true; j++; break; }
        body += s[j];
        j++;
      }
      if (!closed) return null;
      out.push({ kind: 'string', value: unescapeCssString(body) });
      i = j;
      continue;
    }
    // An identifier — or the NAME of a function, which is the refusal case.
    const id = /^[-\w -￿]+/.exec(s.slice(i));
    if (!id) return null;                       // a token shape we do not know
    const name = id[0].toLowerCase();
    i += id[0].length;
    // A `(` directly after the name makes it a function: counter(), attr(),
    // url(), var(), … — every one of them a documented refusal.
    if (s[i] === '(') return null;
    if (QUOTE_KEYWORDS.has(name)) { out.push({ kind: 'quote', name }); continue; }
    // `normal` / `none` generate no box; `contents` and everything else are
    // values we do not model. All refuse.
    return null;
  }
  return out.length > 0 ? out : null;
}

/**
 * Parse a computed `quotes` value (css-content-3 §2.2) into
 * `{ kind: 'auto' | 'none' | 'pairs', pairs }`, or null when unparseable.
 *
 * `auto` is reported, not resolved: resolving it needs the CLDR table keyed
 * by the element's CONTENT LANGUAGE, which this wire does not carry (the
 * `lang` attribute is not forwarded — see the lane's deferred list). The
 * caller therefore emits no mark for a quote under `auto`, which leaves the
 * capture exactly where it is today rather than inventing an English pair
 * for a Japanese document.
 *
 * Exported for the pins.
 */
export function parseQuotesValue(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  const lower = trimmed.toLowerCase();
  if (lower === '' || lower === 'auto') return { kind: 'auto', pairs: [] };
  if (lower === 'none') return { kind: 'none', pairs: [] };
  // A pair list: an EVEN number of <string>s, open/close alternating.
  const strings = [];
  let i = 0;
  while (i < trimmed.length) {
    const ch = trimmed[i];
    if (/\s/.test(ch)) { i++; continue; }
    if (ch !== '"' && ch !== '\'') return null;   // CSS-wide keyword, var(), …
    let j = i + 1;
    let body = '';
    let closed = false;
    while (j < trimmed.length) {
      if (trimmed[j] === '\\') { body += trimmed[j] + (trimmed[j + 1] ?? ''); j += 2; continue; }
      if (trimmed[j] === ch) { closed = true; j++; break; }
      body += trimmed[j];
      j++;
    }
    if (!closed) return null;
    strings.push(unescapeCssString(body));
    i = j;
  }
  if (strings.length === 0 || strings.length % 2 !== 0) return null;
  const pairs = [];
  for (let k = 0; k < strings.length; k += 2) {
    pairs.push({ open: strings[k], close: strings[k + 1] });
  }
  return { kind: 'pairs', pairs };
}

/**
 * Apply one `<quote>` keyword to a quote-depth state, returning the text it
 * paints ('' when it paints none) and mutating `state.depth`.
 *
 * css-content-3 §2.1, exactly:
 *   open-quote      → paint pairs[min(depth, last)].open,  then depth += 1
 *   close-quote     → depth = max(depth - 1, 0), then paint pairs[…].close
 *   no-open-quote   → depth += 1, paint nothing
 *   no-close-quote  → depth = max(depth - 1, 0), paint nothing
 * "If the depth is greater than the number of pairs, the LAST pair is used"
 * — hence the clamp rather than a wrap. A `close-quote` at depth 0 is a
 * parse-time-legal but nonsensical document; the spec says the depth never
 * goes negative and no mark is rendered, which the clamp + `atZero` guard
 * below implement.
 *
 * Exported for the pins.
 */
export function applyQuoteKeyword(name, state, quotes) {
  if (name === 'no-open-quote') { state.depth += 1; return ''; }
  if (name === 'no-close-quote') {
    state.depth = Math.max(state.depth - 1, 0);
    return '';
  }
  if (name === 'open-quote') {
    const idx = Math.min(state.depth, quotes.pairs.length - 1);
    state.depth += 1;
    // `none` and (for now) `auto` paint nothing — see parseQuotesValue.
    if (quotes.kind !== 'pairs') return '';
    return quotes.pairs[idx].open;
  }
  // close-quote
  const atZero = state.depth === 0;
  state.depth = Math.max(state.depth - 1, 0);
  if (quotes.kind !== 'pairs' || atZero) return '';
  const idx = Math.min(state.depth, quotes.pairs.length - 1);
  return quotes.pairs[idx].close;
}

/**
 * Resolve one pseudo bag's `content` declaration to literal text.
 * Returns the string (possibly '') on success, or null on refusal.
 * `state.depth` is advanced for every recognised quote keyword even when the
 * bag is ultimately refused — see the header's note on cross-bag numbering.
 */
function resolveContentText(contentValue, state, quotes) {
  const comps = parseContentComponents(contentValue);
  if (comps === null) {
    // Refused. Keep the counter honest anyway: scan for bare quote keywords
    // (a `content: open-quote counter(x)` still opens a quote in the
    // browser) and apply their depth effect without painting anything.
    if (typeof contentValue === 'string') {
      for (const m of contentValue.matchAll(/(?<![-\w(])((?:no-)?(?:open|close)-quote)(?![-\w(])/gi)) {
        applyQuoteKeyword(m[1].toLowerCase(), state, quotes);
      }
    }
    return null;
  }
  let text = '';
  for (const c of comps) {
    if (c.kind === 'string') { text += c.value; continue; }
    text += applyQuoteKeyword(c.name, state, quotes);
  }
  return text;
}

// css-contain-1 §3.3: STYLE containment SCOPES the `content` property's
// open-quote / close-quote / no-open-quote / no-close-quote "to the element's
// sub-tree". Scoped means the subtree still INHERITS the outer quote depth
// and its own changes do not ESCAPE — it does NOT mean the subtree restarts
// at depth 0. The four css-contain/quote-scoping-* tests pin exactly that
// distinction, and all four agree on the save-and-restore reading:
//
//   -001 `div{quotes:"A" "Z" "1" "9"} div::before,span::before{open-quote}
//         div::after{close-quote} span{contain:style}`          → "A1Z"
//        A(d0→1) · span inherits d1 so its open paints "1" · restore d1,
//        div::after closes to d0 → "Z".  A RESET would have painted "A".
//   -002 span::after{close-quote}                               → "AZZ"
//   -003 span::before{no-open-quote}                            → "AZ"
//   -004 span::after{no-close-quote}                            → "AZ"
//
// The `contain` values that include style containment are `style`, plus the
// two composite keywords `content` (= layout paint style) and `strict`
// (= size layout paint style).
const STYLE_CONTAINMENT_KEYWORDS = new Set(['style', 'content', 'strict']);

/** True when a property bag declares style containment (see above). */
export function declaresStyleContainment(props) {
  const raw = props?.contain;
  if (typeof raw !== 'string') return false;
  return raw.trim().toLowerCase().split(/\s+/)
    .some((kw) => STYLE_CONTAINMENT_KEYWORDS.has(kw));
}

/**
 * Walk a built component tree in DOCUMENT ORDER and materialise every
 * resolvable pseudo `content` declaration as `_text` on the same bag.
 *
 * Order is the css-content-3 §2.1 order that the quote counter demands:
 * a component's `::marker`, then its `::before`, then its children, then its
 * `::after`. (`::marker` precedes `::before` per CSS Pseudo 4 §4.5.)
 *
 * `quotes` inheritance rides the walk: a component's own declared `quotes`
 * value shadows its parent's for the whole subtree, which is the css-cascade
 * inheritance this flat wire cannot express any other way.
 *
 * Mutates the tree in place. Returns `{ baked, refused }` counts so the
 * caller can log and stamp the fixture's lossy record.
 */
export function bakeGeneratedContentText(components, inheritedQuotes = null) {
  const state = { depth: 0 };
  let baked = 0;
  let refused = 0;

  const bakeBag = (bag, quotes, foreign) => {
    if (!bag || typeof bag !== 'object') return;
    const content = bag.properties?.content;
    if (typeof content !== 'string') return;
    // Never overwrite text a different producer already resolved (today
    // nothing else writes here; the guard keeps that true by construction).
    if (typeof bag._text === 'string') return;
    // Resolve FIRST, even for a host we are about to refuse: resolution is
    // what advances the document quote depth, and a refusal must never
    // desynchronise the counter for the elements that follow.
    const text = resolveContentText(content, state, quotes);
    // SVG / MathML hosts generate no pseudo box at all — see the
    // FOREIGN_NAMESPACE_ROOT_TAGS banner and its probe.
    if (foreign) { refused++; return; }
    if (text === null) { refused++; return; }
    // Omit-when-empty: `content: ""` is a real, common declaration whose
    // box is sized by other declarations, and stamping `_text: ""` on it
    // would churn fixture bytes for a string the renderer already defaults
    // to. Only a NON-EMPTY resolution is new information.
    if (text === '') return;
    bag._text = text;
    baked++;
    const reasons = new Set(bag._lossyReasons ?? []);
    reasons.add(GENERATED_CONTENT_BAKED_REASON);
    bag._lossy = true;
    bag._lossyReasons = [...reasons];
  };

  // ── THE MARKER CARVE-OUT (measured, and the reason this is not symmetric)
  //
  // `::marker` looks like the same slot as ::before/::after and is not. The
  // web harness renders a REAL `<li>`, so Blink SYNTHESISES a marker from
  // list-style-type on its own, and wave-27's counter-style bake owns the
  // resolved-string channel for it — `_markerText` → `meta.markerText`,
  // whose schema contract says in as many words: "AUTHORITATIVE when
  // present: a reader that honours it MUST NOT also synthesise a marker".
  // Writing `_text` into the marker bag adds a SECOND marker span next to
  // the one the browser already drew.
  //
  // MEASURED (css-pseudo at full cap, the run that included markers):
  // 82 → 78 passing, every loss a `marker-*` test, and the signature is
  // exactly doubling — marker-content-003 capture ink 17.69% against a
  // 12.39% ref (0.9920 → 0.9270), marker-content-004 17.69% vs 8.52%
  // (0.9509 → 0.9033). The text was right; the box was already there.
  //
  // The slot is still WALKED, because the marker's `content` can carry
  // `open-quote` (css-lists/marker-quotes is exactly that) and the document
  // quote depth has to see it. Resolve, advance, discard.
  const markerOnlyDepth = (bag, quotes) => {
    if (!bag || typeof bag !== 'object') return;
    const content = bag.properties?.content;
    if (typeof content === 'string') resolveContentText(content, state, quotes);
  };

  const visit = (map, quotesIn, foreignIn = false) => {
    for (const cmp of Object.values(map ?? {})) {
      if (!cmp || typeof cmp !== 'object') continue;
      // Once inside <svg>/<math> every descendant is in that namespace too.
      const foreign = foreignIn || opensForeignNamespace(cmp);
      // Own `quotes` declaration shadows the inherited one for this subtree.
      const own = cmp.properties?.quotes;
      const quotes = (typeof own === 'string' && parseQuotesValue(own))
        ? parseQuotesValue(own)
        : quotesIn;
      // css-contain-1 §3.3 — style containment SCOPES the quote counter to
      // this element's subtree: it inherits the current depth and its own
      // changes are rolled back on the way out (see the banner's four-test
      // derivation). The element's own ::before/::after are inside the
      // scope, which is what quote-scoping-002/003/004 measure.
      const contained = declaresStyleContainment(cmp.properties);
      const savedDepth = state.depth;
      // ::marker is walked for its QUOTE DEPTH ONLY — `markerOnlyDepth`
      // resolves the value (moving the counter) and never writes `_text`.
      // See the MARKER CARVE-OUT banner: that slot already has a resolved-
      // text channel (`_markerText`, wave-27) whose contract forbids a
      // second synthesised marker, and the renderers honour it.
      markerOnlyDepth(cmp._pseudo?.marker, quotes);
      bakeBag(cmp._pseudo?.before, quotes, foreign);
      visit(cmp.children, quotes, foreign);
      bakeBag(cmp._pseudo?.after, quotes, foreign);
      if (contained) state.depth = savedDepth;
    }
  };

  // Initial `quotes` is the document's — `auto` unless a caller supplies the
  // root value (the body-root's bag, which the root-inherited bake has
  // already copied onto every top-level child anyway).
  visit(components, parseQuotesValue(inheritedQuotes ?? 'auto'));
  return { baked, refused };
}
