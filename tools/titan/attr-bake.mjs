// tools/titan/attr-bake.mjs — wave-25 THE ATTR BAKE (the fourth bake).
//
// THE WALL: the css-values attr() family fails IDENTICALLY on all three
// platforms — attr-color-valid / attr-color-invalid-cast 0.900 vs the
// browser ref, attr-in-max 0.906, attr-length-invalid-cast 0.906,
// attr-length-valid-zero{,-nofallback} 0.803 — while every PLATFORM PAIR
// scores a perfect 1.000. A three-way tie at the wrong answer is never
// three renderer bugs; it is one upstream gap. Here it is exactly one:
//
//   `width: attr(data-test type(<length>), 0)` is css-values-5 §7 typed
//   attr(). The converter's value parsers have no attr() grammar, so the
//   declaration falls through to the raw `Generic` lane (`{"type":
//   "Generic","data":{"propertyName":"width","rawValue":"attr(…)",
//   "_unmapped":true}}`) and every engine drops it. The red #outer2 box
//   then lays out at `width: auto` instead of `width: 0` and paints the
//   full viewport — the 0.803.
//
// THE PRECEDENT — and why this belongs in the EXTRACTOR, not the parser:
// attr() reads the ELEMENT'S OWN ATTRIBUTES. Unlike var()/env()/calc() on
// runtime units, its input is fully known at extract time: the extractor
// already parses every tag's attributes (for selector matching and for the
// widget `meta.attrs` channel), so the substitution is a pure static
// rewrite of the fixture, exactly like the wave-15 `dir=auto` first-strong
// resolution, the wave-13 keyframe t-sampler and the wave-21 sibling-index
// bake. Same contract as those three: resolve statically, rewrite the
// declaration, and mark it LOUDLY ('baked-attr') so provenance stays
// honest. The markers are informational — scoring is gated by the
// notApplicable tag channel in inject-wpt-block.mjs, never by lossyReasons.
//
// SCOPE BOUNDARY (documented, enforced, never silent):
//   - CONSERVATIVE TRIGGER. `hasAttrFunction` gates everything: a value
//     without an `attr(` token is returned by identity, so the 12,600-test
//     corpus outside the 32 attr() tests is byte-identical by construction.
//   - THREE OUTCOMES, ALL LOUD. A declaration either (a) bakes — every
//     attr() in it resolved, reason 'baked-attr'; or (b) goes
//     invalid-at-computed-value-time — an attr() with no usable value and
//     no fallback, which css-values-5 §7.1 defines as the guaranteed-
//     invalid value poisoning the whole declaration; we write the CSS-wide
//     keyword `unset` (the spec's own words for IACVT: "inherited value if
//     the property is inherited, initial value otherwise") and mark
//     'attr-invalid-at-computed-value-time'; or (c) BAILS VERBATIM — a
//     form this module does not model (namespace prefix, unsupported
//     `type()` syntax, an attribute value that itself carries var()/attr())
//     is left exactly as authored and marked 'attr-unresolved'. There is no
//     fourth, silent branch.
//   - IACVT IS A REWRITE, NOT A DELETION. Deleting the declaration would
//     let an earlier same-property declaration win the cascade
//     (`width: 200px; width: attr(bad)`), which is precisely the behaviour
//     the spec forbids: attr() is valid at PARSE time, so the later
//     declaration wins the cascade and only then computes to unset.
//   - NO PSEUDO/ROOT GUESSING. The caller supplies the ORIGINATING
//     element's attribute bag. Pseudo-element bags bake against their host
//     (css-values-5 §7: attr() on ::before reads the originating element),
//     and the synthetic body-root bag — which merges html/body/:root rules
//     and therefore has NO single originating element — is left unbaked
//     and marked 'attr-unresolved' rather than resolved against a guess.

// ── Lossy markers ────────────────────────────────────────────────────────────

/** At least one attr() in the bag resolved to a static value. Provenance
 *  only — the fixture is now MORE faithful, not less. */
export const ATTR_BAKED_REASON = 'baked-attr';
/** At least one attr() was left verbatim because this module does not model
 *  its form. The declaration still ships raw and will still be dropped
 *  downstream — the marker is what keeps that visible. */
export const ATTR_UNRESOLVED_REASON = 'attr-unresolved';
/** A declaration computed to the guaranteed-invalid value and was rewritten
 *  to `unset` (css-values-5 §7.1 + css-variables-1 §3 IACVT). */
export const ATTR_IACVT_REASON = 'attr-invalid-at-computed-value-time';

// ── Unit + syntax tables (css-values-4) ──────────────────────────────────────

/** <length> units: absolute (§5.2) + font-relative (§5.1) + viewport-
 *  relative (§5.3, all four viewport-size variants). A length in a
 *  relative unit is a perfectly valid cast target — the substituted value
 *  then rides extract-fixture's existing em/rem + viewport-units lossy
 *  lanes, which is the honest outcome. */
const LENGTH_UNITS = new Set(('px cm mm q in pt pc em rem ex ch cap ic lh rlh ' +
  'vw vh vi vb vmin vmax svw svh svi svb svmin svmax ' +
  'lvw lvh lvi lvb lvmin lvmax dvw dvh dvi dvb dvmin dvmax').split(' '));
/** <angle> (§6.1), <time> (§6.2), <frequency> (§6.3), <resolution> (§6.4)
 *  and the grid <flex> unit (css-grid-1 §7.2.3). Needed both for the
 *  `type()` casts below and for the `<attr-unit>` form (`attr(x deg)`). */
const ANGLE_UNITS = new Set(['deg', 'grad', 'rad', 'turn']);
const TIME_UNITS = new Set(['s', 'ms']);
const FREQUENCY_UNITS = new Set(['hz', 'khz']);
const RESOLUTION_UNITS = new Set(['dpi', 'dpcm', 'dppx', 'x']);
const FLEX_UNITS = new Set(['fr']);

/** `<attr-unit>` (css-values-5 §7): the '%' sign or any dimension unit.
 *  `attr(data-w px)` casts the attribute's <number> to that unit. */
const ATTR_UNITS = new Set([
  '%', ...LENGTH_UNITS, ...ANGLE_UNITS, ...TIME_UNITS,
  ...FREQUENCY_UNITS, ...RESOLUTION_UNITS, ...FLEX_UNITS,
]);

/** The CSS Color 4 §6.1 named colors, plus the §7 `transparent` /
 *  `currentcolor` keywords and the §7.4 system colors. Names only: this
 *  module VALIDATES the cast, it never resolves a color — the substituted
 *  text is handed to the converter's real ColorParser. The list has to be
 *  complete, because a missing name would silently divert a valid cast to
 *  its fallback (the exact bug attr-color-invalid-cast is designed to
 *  catch, inverted). */
const NAMED_COLORS = new Set((
  'aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond ' +
  'blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue ' +
  'cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey ' +
  'darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon ' +
  'darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet ' +
  'deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen ' +
  'fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew ' +
  'hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon ' +
  'lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey ' +
  'lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey ' +
  'lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine ' +
  'mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue ' +
  'mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose ' +
  'moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid ' +
  'palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink ' +
  'plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon ' +
  'sandybrown seagreen seashell sienna silver skyblue slateblue slategray slategrey ' +
  'snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white ' +
  'whitesmoke yellow yellowgreen ' +
  'transparent currentcolor ' +
  'accentcolor accentcolortext activetext buttonborder buttonface buttontext canvas ' +
  'canvastext field fieldtext graytext highlight highlighttext linktext mark marktext ' +
  'selecteditem selecteditemtext visitedtext'
).split(' '));

/** Color FUNCTIONS we accept as a valid `<color>` cast. Only the function
 *  NAME is checked — the argument grammar is the converter's job, and a
 *  malformed one fails there loudly rather than being silently re-routed to
 *  the attr() fallback (which would misattribute the failure). */
const COLOR_FUNCTIONS = new Set(['rgb', 'rgba', 'hsl', 'hsla', 'hwb', 'lab', 'lch',
  'oklab', 'oklch', 'color', 'color-mix', 'light-dark', 'device-cmyk']);

/** A CSS <number> token (css-syntax-3 §4.3.1), sign + optional exponent. */
const NUMBER_RX = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i;
/** A CSS <dimension>: <number> immediately followed by an ident unit. */
const DIMENSION_RX = /^([+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?)([a-z%]+)$/i;
/** A CSS <custom-ident>-ish token: no whitespace, no punctuation that would
 *  end a component value. Deliberately loose — the strictness that matters
 *  for the corpus is the <length>/<color> casts. */
const IDENT_RX = /^-?[A-Za-z_\u0080-\uFFFF][-\w\u0080-\uFFFF]*$/;

// ── Value scanning primitives ────────────────────────────────────────────────

/**
 * THE CONSERVATIVE TRIGGER. True only when the value carries an `attr(`
 * function token. The negative lookbehind keeps it off idents that merely
 * END in "attr" (a hypothetical `data-attr(` never occurs, but the guard
 * costs nothing and documents the intent). Every public entry point in this
 * module short-circuits on this, so a corpus without attr() is untouched.
 */
export function hasAttrFunction(value) {
  return typeof value === 'string' && /(?<![-\w])attr\(/i.test(value);
}

/**
 * Locate the next `attr(` call in `text` at or after `from`, skipping any
 * occurrence inside a CSS string. Returns `{ start, argsStart, argsEnd, end }`
 * where `[argsStart, argsEnd)` is the argument text and `end` is one past
 * the closing paren, or null when there is no (well-formed) call left.
 * Exported so the tests can pin the nesting + quote handling directly.
 */
export function findAttrCall(text, from = 0) {
  for (let i = from; i < text.length; i++) {
    const ch = text[i];
    // Strings are opaque: `content: "attr(x)"` is literal text, not a call.
    if (ch === '"' || ch === "'") { i = skipString(text, i); continue; }
    // Cheap reject before the (case-insensitive) 5-char compare.
    if (ch !== 'a' && ch !== 'A') continue;
    if (text.slice(i, i + 5).toLowerCase() !== 'attr(') continue;
    // An ident char immediately before would make this a different token.
    if (i > 0 && /[-\w]/.test(text[i - 1])) continue;
    const argsStart = i + 5;
    const argsEnd = matchParen(text, argsStart);
    // Unbalanced parens = malformed CSS; report "no call" so the caller
    // bails verbatim instead of splicing on a guessed boundary.
    if (argsEnd < 0) return null;
    return { start: i, argsStart, argsEnd, end: argsEnd + 1 };
  }
  return null;
}

/** Index of the closing quote of the string starting at `i` (or the last
 *  index when unterminated, so callers always advance). Handles `\` escapes. */
function skipString(text, i) {
  const quote = text[i];
  for (let j = i + 1; j < text.length; j++) {
    if (text[j] === '\\') { j++; continue; }   // escaped char — skip the pair
    if (text[j] === quote) return j;           // closing quote
  }
  return text.length - 1;                      // unterminated — consume the rest
}

/** Index of the `)` matching the (already-consumed) `(` whose body starts at
 *  `from`, honouring nesting and strings. -1 when unbalanced. */
function matchParen(text, from) {
  let depth = 1;
  for (let i = from; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"' || ch === "'") { i = skipString(text, i); continue; }
    if (ch === '(') depth++;
    else if (ch === ')' && --depth === 0) return i;
  }
  return -1;
}

/**
 * Split `text` at its FIRST top-level comma (parens + strings are opaque).
 * Returns `[head, tail]` with `tail === null` when there is no such comma.
 * css-values-5 §7 puts exactly one comma between the name/type head and the
 * `<declaration-value>` fallback; every later comma belongs to the fallback
 * (`attr(x type(<color>), rgb(1, 2, 3))`), which is why this splits ONCE.
 */
export function splitFirstTopLevelComma(text) {
  let depth = 0;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"' || ch === "'") { i = skipString(text, i); continue; }
    if (ch === '(') depth++;
    else if (ch === ')') depth--;
    else if (ch === ',' && depth === 0) return [text.slice(0, i), text.slice(i + 1)];
  }
  return [text, null];
}

// ── Argument parsing ─────────────────────────────────────────────────────────

/**
 * Parse an attr() argument list per css-values-5 §7:
 *
 *   attr() = attr( <attr-name> <attr-type>? , <declaration-value>? )
 *   <attr-name> = [ <ident-token>? '|' ]? <ident-token>
 *   <attr-type> = type( <syntax> ) | raw-string | <attr-unit>
 *
 * Returns `{ name, type, fallback }` (fallback === null when the comma was
 * absent), or null for any shape this module refuses to model — an explicit
 * namespace prefix (`attr(svg|width …)`: resolving it needs the document's
 * @namespace table, which the extractor does not build), an explicitly
 * EMPTY fallback (`attr(x,)`, whose empty-token-stream semantics we decline
 * to guess), or an unrecognised `<attr-type>`. null makes the caller bail
 * verbatim + mark 'attr-unresolved'.
 */
export function parseAttrArgs(argText) {
  const [head, rawFallback] = splitFirstTopLevelComma(argText);
  // An explicit comma with nothing usable after it: refuse (see doc above).
  if (rawFallback !== null && rawFallback.trim() === '') return null;
  const headTrim = head.trim();
  if (!headTrim) return null;                       // `attr()` — no name at all
  // First whitespace-delimited token is the name; whatever follows is the type.
  const sp = headTrim.search(/\s/);
  const nameToken = sp < 0 ? headTrim : headTrim.slice(0, sp);
  const typeToken = sp < 0 ? '' : headTrim.slice(sp + 1).trim();
  const name = parseAttrName(nameToken);
  if (name === null) return null;
  const type = parseAttrType(typeToken);
  if (type === null) return null;
  return { name, type, fallback: rawFallback === null ? null : rawFallback.trim() };
}

/**
 * Resolve `<attr-name>` to the lowercase attribute key the extractor's
 * `parseAttrsFromTagOpen` stores. `*|foo` (any namespace) collapses to a
 * plain `foo` lookup — in an HTML document every attribute the extractor
 * sees is in the null namespace, so "any namespace" and "no namespace"
 * select the same set. An EXPLICIT prefix (`svg|foo`) returns null (bail).
 * Names are matched ASCII case-insensitively, matching the HTML parser's
 * own attribute-name lowercasing (css-values-5 §7: attribute names are
 * matched case-insensitively in HTML documents).
 */
export function parseAttrName(token) {
  // ANY '|' bails — including the wildcard `*|bar`. Two corpus tests pin
  // this from opposite directions and neither can be served by a guess:
  // css-values/attr-namespace-wildcard carries the explicit assertion
  // "Wildcard not supported in attr() function" (resolving `*|bar` there
  // would paint the red inner box the test exists to forbid), and
  // attr-namespace-non-existing needs the document's @namespace table —
  // which the extractor does not build — to decide whether `foo|bar`
  // is an undeclared-prefix error or a simple attribute miss. Bailing
  // leaves both declarations exactly as authored (byte-identical
  // properties) and marks them 'attr-unresolved'.
  if (token.includes('|')) return null;
  if (!token || !IDENT_RX.test(token)) return null;
  // HTML lowercases attribute names at parse time and css-values-5 §7
  // matches them ASCII case-insensitively in HTML documents — which is
  // also the key shape parseAttrsFromTagOpen stores.
  return token.toLowerCase();
}

/**
 * Parse `<attr-type>`. An OMITTED type defaults to `raw-string`
 * (css-values-5 §7: "if <attr-type> is omitted, it defaults to
 * raw-string"), which is also what makes the legacy `content: attr(x)`
 * form keep working. Returns null for unmodelled types.
 */
export function parseAttrType(token) {
  if (token === '') return { kind: 'raw-string' };
  const lower = token.toLowerCase();
  if (lower === 'raw-string') return { kind: 'raw-string' };
  const fn = /^type\(([\s\S]*)\)$/i.exec(token);
  if (fn) {
    const syntax = fn[1].trim().toLowerCase();
    // Only SINGLE syntax components are modelled. Unions (`<length> |
    // <percentage>`) and multipliers (`<length>+`, `<color>#`) would need a
    // real <syntax> parser; refusing them is loud, guessing would not be.
    if (!SYNTAX_VALIDATORS.has(syntax)) return null;
    return { kind: 'syntax', syntax };
  }
  if (ATTR_UNITS.has(lower)) return { kind: 'unit', unit: lower };
  return null;
}

// ── Cast validation (css-values-5 §7.1 "the substitution value") ─────────────

/**
 * One validator per modelled `<syntax>` component. Each answers a single
 * question: does this raw ATTRIBUTE STRING parse as that CSS type? A `true`
 * means the raw text is spliced into the declaration verbatim; a `false`
 * sends the call to its fallback (the "invalid cast" rule the two
 * *-invalid-cast tests assert). The map's KEYS are also the accept-list
 * parseAttrType enforces — anything absent bails loudly.
 */
const SYNTAX_VALIDATORS = new Map([
  // `*` — the universal syntax: the attribute text is parsed as a
  // <declaration-value> (css-syntax-3 §9). isDeclarationValue enforces that
  // grammar exactly; a bare "non-empty" test is NOT enough (see that
  // function's banner for the four measured Chromium divergences).
  ['*', (v) => isDeclarationValue(v)],
  // <string>: css-values-5 §7.1 parses the ATTRIBUTE TEXT AS CSS for every
  // `type(<syntax>)` cast, so a <string> cast requires the attribute to
  // ALREADY carry a quoted CSS string — it does NOT quote raw text. That is
  // the `raw-string` type's job, and conflating the two is wrong in both
  // directions (measured against Chrome 150):
  //   attr(x type(<string>)) + x="abc"    → cast FAILS (→ fallback/IACVT);
  //                                          quoting it would paint `abc`
  //                                          where the browser paints nothing.
  //   attr(x type(<string>)) + x='"abc"'  → the string `abc`; re-quoting it
  //                                          would paint the literal `"abc"`.
  // (Contrast `attr(x raw-string)` + x='"abc"' → the string `"abc"`, which
  // castAttrValue's raw-string arm still produces.)
  ['<string>', isCssStringToken],
  ['<number>', (v) => NUMBER_RX.test(v)],
  ['<integer>', (v) => /^[+-]?\d+$/.test(v)],
  ['<percentage>', (v) => isDimension(v, (u) => u === '%')],
  ['<length>', isLength],
  // <length-percentage> is the union the box-model properties actually take.
  ['<length-percentage>', (v) => isLength(v) || isDimension(v, (u) => u === '%')],
  ['<angle>', (v) => isDimension(v, (u) => ANGLE_UNITS.has(u))],
  ['<time>', (v) => isDimension(v, (u) => TIME_UNITS.has(u))],
  ['<color>', isColor],
  ['<custom-ident>', (v) => IDENT_RX.test(v.trim())],
]);

/**
 * `<declaration-value>` (css-syntax-3 §9): "any sequence of one or more
 * tokens, so long as the sequence does not contain <bad-string-token>,
 * <bad-url-token>, unmatched <)-token>, <]-token>, or <}-token>, or
 * top-level <semicolon-token> tokens or <delim-token> with a value of '!'."
 *
 * WHY THIS EXISTS AS A REAL CHECK, not a `!== ''` stub. Every `type(<syntax>)`
 * cast parses the ATTRIBUTE TEXT AS CSS before validating it against the
 * syntax component, so text that is not even a well-formed token stream fails
 * the cast outright. Measured against Chrome 150, a permissive `*` accepted
 * four inputs the browser rejects — and every one of them is also a
 * SERIALIZATION hazard, because the baked text is spliced back into a
 * declaration value:
 *
 *   data-t="200px; color: red"       browser: cast fails → IACVT → width auto
 *                                    permissive: `width: 200px; color: red`
 *                                    — the `;` ends the declaration and the
 *                                    remainder becomes a NEW one.
 *   data-t="200px} #zz{width:1px"    browser: cast fails.  permissive: the
 *                                    `}` escapes the rule entirely.
 *   data-t="red !important"          browser: cast fails.  permissive:
 *                                    silently promotes the declaration.
 *   data-t='"a" ; "b"'               browser: cast fails.  permissive:
 *                                    truncates to `content: "a"`.
 *
 * Applied as a PRECONDITION to every `type(<syntax>)` cast (castAttrValue),
 * so the same guard covers `<color>`'s function-by-name arm — `rgb(1;2)`
 * would otherwise pass the name check and end with `)`.
 *
 * Strings are opaque (a `;` inside a quoted string is fine), an unterminated
 * string is a <bad-string-token>, and `;` / `!` are rejected only at
 * TOP LEVEL, exactly as the grammar says.
 */
export function isDeclarationValue(raw) {
  const t = String(raw).trim();
  if (t === '') return false;                    // "one or more tokens"
  const stack = [];                              // open bracket chars
  const CLOSE = { ')': '(', ']': '[', '}': '{' };
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (ch === '"' || ch === "'") {
      // A string token: opaque, but it MUST terminate on the same line —
      // an unterminated string (or a raw newline inside one) is a
      // <bad-string-token>, which the grammar forbids.
      let j = i + 1;
      let closed = false;
      for (; j < t.length; j++) {
        if (t[j] === '\\') { j++; continue; }    // escape — skip the pair
        if (t[j] === '\n' || t[j] === '\r') break; // raw newline → bad-string
        if (t[j] === ch) { closed = true; break; }
      }
      if (!closed) return false;
      i = j;
      continue;
    }
    if (ch === '(' || ch === '[' || ch === '{') { stack.push(ch); continue; }
    if (ch === ')' || ch === ']' || ch === '}') {
      // Unmatched (or mismatched) closer — the grammar's second exclusion.
      if (stack.pop() !== CLOSE[ch]) return false;
      continue;
    }
    // Top-level only, per the grammar's wording.
    if (stack.length === 0 && (ch === ';' || ch === '!')) return false;
  }
  // A LEFT-OVER OPENER IS LEGAL and deliberately not rejected. css-syntax-3
  // §4.3.2 auto-closes a function token at EOF (a parse error, but the token
  // is still returned), and <declaration-value> excludes only unmatched
  // CLOSERS. Measured: `attr(x type(*))` with x="calc(10px + 5px" computes
  // 15px in Chrome 150 — an earlier `stack.length === 0` check here made us
  // wrongly report IACVT for exactly that input.
  return true;
}

/**
 * Characters that can never appear in a COLOR FUNCTION's argument list at
 * any nesting depth: the declaration-terminating `;`, the `!` of
 * `!important`, block brackets, and string quotes. Used to narrow isColor's
 * function-by-name arm (below) — string-aware only in the sense that a
 * quote is itself disqualifying, since no color function takes a string.
 */
function hasColorArgHazard(args) {
  return /[;!{}[\]"']/.test(args);
}

/**
 * SPLICE SAFETY — a separate question from spec legality, and the reason
 * isDeclarationValue above does NOT reject leftover openers.
 *
 * The tokenizer auto-closes an unclosed function at EOF, so `calc(10px + 5px`
 * is a legal <declaration-value> and Chrome 150 computes `width: attr(x
 * type(*))` with that attribute as 15px. But this module's product is
 * RE-SERIALIZED into a declaration, and there the same text swallows its own
 * terminator: `width: calc(10px + 5px;` never closes, so the `;` is consumed
 * as part of the function and the declaration (and whatever follows) is lost.
 *
 * Rather than emit a value whose meaning depends on what comes after it, an
 * attribute that cannot be spliced verbatim is treated as an UNMODELLED FORM
 * — bailed verbatim and marked 'attr-unresolved', the module's documented
 * third outcome — instead of being mis-baked or diverted to a fallback the
 * browser would not take. raw-string is exempt by construction: cssString()
 * wraps the text in quotes, which makes any byte sequence splice-safe.
 */
export function isSpliceSafe(raw) {
  const t = String(raw);
  const stack = [];
  const CLOSE = { ')': '(', ']': '[', '}': '{' };
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (ch === '"' || ch === "'") {              // strings are opaque
      for (let j = i + 1; j < t.length; j++) {
        if (t[j] === '\\') { j++; continue; }
        if (t[j] === ch) { i = j; break; }
        if (j === t.length - 1) return false;    // unterminated string
      }
      continue;
    }
    if (ch === '(' || ch === '[' || ch === '{') stack.push(ch);
    else if (ch === ')' || ch === ']' || ch === '}') {
      if (stack.pop() !== CLOSE[ch]) return false;
    }
  }
  return stack.length === 0;                     // no auto-closed opener left
}

/**
 * A single, complete CSS <string-token> (css-syntax-3 §4.3.5) and nothing
 * else — the shape `type(<string>)` requires the ATTRIBUTE ITSELF to carry.
 * Must open and close with the SAME quote, terminate exactly at the end of
 * the text, carry no raw newline, and honour `\` escapes.
 */
export function isCssStringToken(raw) {
  const t = String(raw).trim();
  if (t.length < 2) return false;
  const q = t[0];
  if (q !== '"' && q !== "'") return false;
  for (let i = 1; i < t.length; i++) {
    if (t[i] === '\\') { i++; continue; }        // escape — skip the pair
    if (t[i] === '\n' || t[i] === '\r') return false; // raw newline → bad-string
    // The closing quote is only legal as the very last character; an earlier
    // one would mean trailing junk (`"a" "b"`, `"a" ; "b"`).
    if (t[i] === q) return i === t.length - 1;
  }
  return false;                                  // never closed
}

/** <dimension> whose unit satisfies `unitOk`. */
function isDimension(raw, unitOk) {
  const m = DIMENSION_RX.exec(raw.trim());
  return m !== null && unitOk(m[2].toLowerCase());
}

/** <length>: a dimension in a length unit, OR the unitless zero. CSS grammar
 *  admits `0` as a length (css-values-4 §5: "zero lengths may be written
 *  without a unit"), and that is EXACTLY what attr-length-valid-zero
 *  asserts — `data-test="0"` must cast to a valid 0 length so the red
 *  #outer2 collapses, rather than failing the cast and taking the
 *  auto-width path that produced the 0.803. */
function isLength(raw) {
  const t = raw.trim();
  if (NUMBER_RX.test(t) && parseFloat(t) === 0) return true;
  return isDimension(t, (u) => LENGTH_UNITS.has(u));
}

/** <color>: a named/system keyword, a 3/4/6/8-digit hex, or a known color
 *  function. `qqffuutt` (attr-color-invalid-cast's attribute) matches none
 *  of the three and correctly routes to the `green` fallback. */
function isColor(raw) {
  const t = raw.trim().toLowerCase();
  if (NAMED_COLORS.has(t)) return true;
  const hex = /^#([0-9a-f]+)$/.exec(t);
  if (hex) return [3, 4, 6, 8].includes(hex[1].length);
  const fn = /^([a-z-]+)\(/.exec(t);
  if (fn === null || !COLOR_FUNCTIONS.has(fn[1]) || !t.endsWith(')')) return false;
  // The ARGUMENT GRAMMAR stays the converter's job (a full color parser here
  // would duplicate ColorParser.kt), but token-level junk must be rejected
  // HERE, not deferred: when a fallback exists, a bad cast has to REACH it.
  // Measured — `attr(x type(<color>), green)` with x="rgb(1;2)" computes
  // green in Chrome 150; accepting the function on its name alone baked
  // `rgb(1;2)` instead, which renders transparent AND carries a `;` into a
  // declaration value. Residual, documented gap: an argument list that is
  // token-clean but semantically wrong (`rgb(a b c)`) still passes here and
  // fails downstream instead of routing to the fallback.
  return !hasColorArgHazard(t.slice(fn[0].length, -1));
}

/** Serialize raw attribute text as a CSS <string> token. Escapes the two
 *  characters CSS strings cannot carry raw (`\` and the delimiter) and
 *  encodes control characters with the `\XXXXXX ` hex form — a literal
 *  newline is invalid inside a CSS string (css-syntax-3 §4.3.5). */
export function cssString(raw) {
  const body = String(raw).replace(/[\\"]/g, (c) => `\\${c}`)
    .replace(/[\u0000-\u001F\u007F]/g, (c) => `\\${c.codePointAt(0).toString(16)} `);
  return `"${body}"`;
}

// ── Substitution ─────────────────────────────────────────────────────────────

/** Outcome vocabulary shared by resolveAttrCall + substituteAttrInValue.
 *  'ok' carries `text`; 'invalid' is the guaranteed-invalid value (→ IACVT);
 *  'unsupported' means "leave the source text alone and mark it". */
const OK = (text) => ({ status: 'ok', text });
const INVALID = { status: 'invalid' };
const UNSUPPORTED = { status: 'unsupported' };

/** Fallback-recursion cap. A fallback may itself contain attr()
 *  (`attr(a, attr(b, red))`); anything deeper than this is pathological and
 *  bails loudly rather than spinning. */
const MAX_FALLBACK_DEPTH = 8;

/**
 * Resolve ONE parsed attr() call against the element's attribute bag,
 * per css-values-5 §7.1:
 *   1. attribute present AND the cast succeeds → the cast's substitution;
 *   2. otherwise the fallback, if one was given (recursively substituted,
 *      since a fallback is itself a <declaration-value>);
 *   3. otherwise the DEFAULT fallback — the empty string for raw-string,
 *      the guaranteed-invalid value for every other type.
 * Exported for direct unit pinning.
 */
export function resolveAttrCall(parsed, attrs, depth = 0) {
  const { name, type, fallback } = parsed;
  const raw = Object.prototype.hasOwnProperty.call(attrs ?? {}, name)
    ? String(attrs[name])
    : null;
  // An attribute whose own text carries a substitution function would need
  // a second substitution pass the spec explicitly does NOT perform; rather
  // than ship a half-resolved value, bail the whole declaration verbatim.
  if (raw !== null && /(?<![-\w])(?:var|attr|env)\(/i.test(raw)) return UNSUPPORTED;
  // Second bail of the same family: text that is legal CSS but cannot be
  // SPLICED back into a declaration without swallowing its terminator (see
  // isSpliceSafe). raw-string is exempt — cssString() quotes it.
  if (raw !== null && type.kind !== 'raw-string' && !isSpliceSafe(raw)) return UNSUPPORTED;
  if (raw !== null) {
    const cast = castAttrValue(raw, type);
    if (cast !== null) return OK(cast);
  }
  // Step 2 — the author-supplied fallback. Recurse so nested attr() inside
  // it resolves too, and so an invalid nested call propagates.
  if (fallback !== null) {
    if (depth >= MAX_FALLBACK_DEPTH) return UNSUPPORTED;
    if (!hasAttrFunction(fallback)) return OK(fallback);
    const sub = substituteAttrInValue(fallback, attrs, depth + 1);
    if (sub.unresolved) return UNSUPPORTED;
    if (sub.invalid) return INVALID;
    return OK(sub.value);
  }
  // Step 3 — the type-dependent default fallback.
  return type.kind === 'raw-string' ? OK('""') : INVALID;
}

/**
 * Apply an `<attr-type>` to a raw attribute string. Returns the CSS text to
 * splice in, or null when the cast FAILS (which sends the call to its
 * fallback). The three arms mirror the three `<attr-type>` productions.
 */
export function castAttrValue(raw, type) {
  // raw-string: the attribute text becomes a CSS string, unparsed. This is
  // the legacy `content: attr(data-x)` behaviour the corpus leans on.
  if (type.kind === 'raw-string') return cssString(raw);
  // <attr-unit>: the attribute must be a bare <number>; the unit is appended
  // (`attr(data-w px)` + data-w="12" → `12px`). css-values-5 §7.
  if (type.kind === 'unit') {
    const t = raw.trim();
    if (!NUMBER_RX.test(t)) return null;
    return `${t}${type.unit}`;
  }
  // type(<syntax>): the attribute text is parsed AS CSS, so it must first be
  // a well-formed <declaration-value> (see isDeclarationValue's banner for
  // the measured Chromium divergences a permissive check produced, and for
  // why this is also the serialization guard). Only then does the
  // per-syntax validator get a say.
  if (!isDeclarationValue(raw)) return null;
  const validator = SYNTAX_VALIDATORS.get(type.syntax);
  // parseAttrType already enforced membership; this is a belt-and-braces
  // guard so a future syntax added to one table but not the other bails
  // loudly instead of throwing mid-extraction.
  if (!validator) return null;
  if (!validator(raw)) return null;
  // The attribute text is spliced in VERBATIM — including <string>, whose
  // validator already required the attribute to BE a quoted CSS string.
  // (Re-quoting here is the raw-string arm's job, above.)
  return raw.trim();
}

/**
 * Substitute every attr() in one declaration value. Returns
 * `{ value, baked, unresolved, invalid }`:
 *   - `baked`      — at least one call resolved (value rewritten);
 *   - `unresolved` — at least one call was left verbatim (unmodelled form);
 *   - `invalid`    — a call yielded the guaranteed-invalid value, so the
 *                    WHOLE declaration is invalid at computed-value time;
 *                    `value` is then meaningless and the caller writes
 *                    `unset` instead.
 * Scanning is left-to-right and skips PAST each resolved call, so an attr()
 * nested in a fallback is handled by the recursion in resolveAttrCall (once)
 * rather than being re-visited here (twice).
 */
export function substituteAttrInValue(value, attrs, depth = 0) {
  let out = '';
  let i = 0;
  let baked = false;
  let unresolved = false;
  for (;;) {
    const call = findAttrCall(value, i);
    if (!call) { out += value.slice(i); break; }
    out += value.slice(i, call.start);
    const argText = value.slice(call.argsStart, call.argsEnd);
    const parsed = parseAttrArgs(argText);
    const res = parsed === null ? UNSUPPORTED : resolveAttrCall(parsed, attrs, depth);
    // Guaranteed-invalid poisons the WHOLE declaration, so we stop
    // immediately and hand back the ORIGINAL text: `value` is meaningless
    // once `invalid` is set (the caller writes `unset`), and returning the
    // untouched source keeps the result inspectable in a debugger.
    if (res.status === 'invalid') return { value, baked, unresolved, invalid: true };
    if (res.status === 'unsupported') {
      // Copy the call through untouched — the declaration keeps shipping the
      // authored text, and the caller marks it 'attr-unresolved'.
      out += value.slice(call.start, call.end);
      unresolved = true;
    } else {
      out += res.text;
      baked = true;
    }
    i = call.end;
  }
  return { value: out, baked, unresolved, invalid: false };
}

// ── Post-substitution folding ────────────────────────────────────────────────

/**
 * Fold single-argument `min()` / `max()` / `calc()` wrappers left behind by
 * a substitution — `max(attr(data-test type(<length>)))` becomes
 * `max(200px)`, and min/max of ONE value is that value by definition
 * (css-values-4 §10.4), so the fold is exact rather than approximate.
 *
 * This exists because the converter classifies any `min(`/`max(`/`calc(`
 * head as a runtime EXPRESSION (LengthParser.isExpression) and refuses to
 * pre-resolve it — which is right in general and wrong for the degenerate
 * one-argument case that attr-in-max lands on. Applied ONLY to values this
 * module just rewrote, so no non-attr declaration in the corpus can change.
 * Runs innermost-first and to a fixed point (`calc(max(1px))` → `1px`).
 */
export function foldSingleArgMathWrappers(value) {
  // Innermost calls first: the body charset excludes parens, so a match can
  // only ever be a wrapper with no nested function left inside it.
  const rx = /(?<![-\w])(?:calc|min|max)\(\s*([^(),]+?)\s*\)/gi;
  let out = value;
  for (let pass = 0; pass < 8; pass++) {          // fixed-point cap: 8 nestings
    const next = out.replace(rx, (m, body) => {
      // A bare single operand only. Anything with an operator inside is a
      // real calculation and stays for the converter/engines to handle.
      if (/[+*/]|(?:^|\s)-\s/.test(body)) return m;
      return body;
    });
    if (next === out) return out;                 // fixed point reached
    out = next;
  }
  return out;
}

// ── Public entry point ───────────────────────────────────────────────────────

/**
 * Bake every attr() in a declaration bag against `attrs`, the ORIGINATING
 * element's attribute map (lowercase keys, as parseAttrsFromTagOpen emits).
 * MUTATES `props` in place — same contract as extract-fixture's
 * bakeSiblingIndex / bakeSampledAnimation — and returns the three LOUD
 * markers the caller pushes onto `_lossyReasons`:
 *
 *   { baked, unresolved, iacvt }
 *
 * Values without an `attr(` token are not touched, not re-serialized, and
 * not even scanned past the trigger regex.
 */
export function bakeAttr(props, attrs) {
  let baked = false;
  let unresolved = false;
  let iacvt = false;
  for (const [k, v] of Object.entries(props)) {
    if (!hasAttrFunction(v)) continue;            // THE conservative trigger
    const res = substituteAttrInValue(v, attrs ?? {});
    if (res.invalid) {
      // css-values-5 §7.1 + css-variables-1 §3: the declaration is valid at
      // PARSE time (so it still won the cascade) and becomes the guaranteed-
      // invalid value at computed-value time. `unset` is the CSS-wide
      // keyword with exactly that meaning, and every converter longhand
      // parser already accepts it (GlobalKeywords.kt).
      props[k] = 'unset';
      iacvt = true;
      continue;
    }
    if (res.unresolved) unresolved = true;
    if (!res.baked) continue;                     // nothing resolved — leave as-is
    // Fold only what we just rewrote (see foldSingleArgMathWrappers).
    props[k] = foldSingleArgMathWrappers(res.value);
    baked = true;
  }
  return { baked, unresolved, iacvt };
}
