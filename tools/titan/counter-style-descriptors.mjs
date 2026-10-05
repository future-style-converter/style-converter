// tools/titan/counter-style-descriptors.mjs — wave 52, lane L6 (T7), part 1 of 2.
//
// The css-counter-styles-3 §3 DESCRIPTOR GRAMMAR as validators (null =
// "invalid": what CSS does with an invalid declaration and what the §7.1
// CSSOM does with an invalid setter), plus the setter verdict built on them.
// Split from counter-style-author.mjs (rule parser + table) for the ≤200 rule.
// WHY A SCRIPT VERDICT, NOT A BLANKET `<script` BAIL: all 15 gate cssom tests
// carry a `<script>`; the 8 `-invalid` ones assign only values the grammar
// REJECTS ("Invalid values should be ignored"), so the static rule IS their
// rendering; only the 7 valid setter tests are the script-mutation wall.

/** css-values-4 §4.2 (<custom-ident>): the CSS-wide keywords (and the
 *  reserved `default`) a `<custom-ident>` excludes. */
export const CSS_WIDE_KEYWORDS = new Set(['initial', 'inherit', 'unset', 'revert', 'revert-layer', 'default']);
/** §3.1.1 the six counter systems (`extends` is handled separately). */
export const SYSTEMS = new Set(['cyclic', 'numeric', 'alphabetic', 'symbolic', 'additive', 'fixed']);

/** One CSS escape (`\3001`, `\207B`, `\2023`, or `\c`) → its character. */
const unescape = (s) => s.replace(/\\([0-9a-fA-F]{1,6})\s?|\\(.)/g, (_, hex, ch) =>
  hex ? String.fromCodePoint(Number.parseInt(hex, 16)) : ch);

/** A tiny css-syntax-3 tokenizer for descriptor VALUES only: strings,
 *  idents (with escapes), integers, commas, functions (opaque), and `other`
 *  for anything else (`*`, `~`, a bare `)`). Whitespace separates. */
export function tokenize(value) {
  const out = [];
  // Ident = css-syntax-3 §4.3.9 name-start (letter / `_` / non-ASCII / escape)
  // then name chars; non-ASCII matters: author symbols like `ⓐ` are idents.
  const rx = /\s+|,|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|([-+]?\d+)(?![\w.-])|((?:-?(?:[a-zA-Z_]|[^\x00-\x7F]|\\[^\n])(?:[\w-]|[^\x00-\x7F]|\\[^\n])*))(\()?|(.)/gy;
  let m;
  while ((m = rx.exec(value)) !== null) {
    if (m[0].trim() === '') continue;                         // whitespace
    if (m[0] === ',') out.push({ t: 'comma', v: ',' });
    else if (m[1]) out.push({ t: 'string', v: unescape(m[1].slice(1, -1)) });
    else if (m[2] !== undefined) out.push({ t: 'int', v: Number.parseInt(m[2], 10) });
    else if (m[3] && m[4]) {                                    // name( … ) — consume to the matching paren
      const close = value.indexOf(')', rx.lastIndex);
      out.push({ t: 'func', v: m[3].toLowerCase() });
      rx.lastIndex = close < 0 ? value.length : close + 1;
    } else if (m[3]) out.push({ t: 'ident', v: unescape(m[3]) });
    else out.push({ t: 'other', v: m[5] });
  }
  return out;
}

/** `<symbol> = <string> | <image> | <custom-ident>` (§3.1.2). An `<image>`
 *  is UNMODELLED (returns the IMAGE sentinel so the rule declines instead of
 *  guessing); a CSS-wide keyword is not a custom-ident (broken-symbols:
 *  `symbols: ⓐ inherit` is an INVALID rule, Blink paints "1."). */
export const IMAGE_SYMBOL = Symbol('image-symbol');
export function parseSymbol(tok) {
  if (!tok) return null;
  if (tok.t === 'string') return tok.v;
  if (tok.t === 'ident') return CSS_WIDE_KEYWORDS.has(tok.v.toLowerCase()) ? null : tok.v;
  if (tok.t === 'func') return /^(url|src|image|image-set|-webkit-image-set|linear-gradient|radial-gradient|conic-gradient|repeating-linear-gradient|repeating-radial-gradient|repeating-conic-gradient|cross-fade|element|paint)$/.test(tok.v) ? IMAGE_SYMBOL : null;
  return null;                                                  // ints, `*`, `~`, `)` …
}
const allSymbols = (toks) => { const out = toks.map(parseSymbol); return out.some((s) => s === null) ? null : out; };

/** `<counter-style-name>`: a custom-ident that is not `none` (§3); the
 *  reserved `decimal` / `disc` cannot be (re)defined by a rule or a `name`
 *  setter (§3, §7.1 — cssom-name-setter-invalid lists both). */
export function parseName(value, { forSetter = false } = {}) {
  const toks = tokenize(value);
  if (toks.length !== 1 || toks[0].t !== 'ident') return null;
  const n = toks[0].v;
  if (CSS_WIDE_KEYWORDS.has(n.toLowerCase()) || n.toLowerCase() === 'none') return null;
  if (forSetter && (n.toLowerCase() === 'decimal' || n.toLowerCase() === 'disc')) return null;
  return n;
}

/** §3.1.1 `system: cyclic | numeric | alphabetic | symbolic | additive |
 *  [fixed <integer>?] | [extends <counter-style-name>]`. */
export function parseSystem(value) {
  const toks = tokenize(value);
  if (toks.length === 0 || toks[0].t !== 'ident') return null;
  const kw = toks[0].v.toLowerCase();
  if (kw === 'fixed') {
    if (toks.length === 1) return { system: 'fixed', first: 1 };
    return toks.length === 2 && toks[1].t === 'int' ? { system: 'fixed', first: toks[1].v } : null;
  }
  if (kw === 'extends') {
    if (toks.length !== 2 || toks[1].t !== 'ident') return null;
    const base = parseName(toks[1].v);
    return base === null ? null : { system: 'extends', base };
  }
  return toks.length === 1 && SYSTEMS.has(kw) ? { system: kw } : null;
}

/** §3.1.4 `negative: <symbol> <symbol>?` → { prefix, suffix }. */
export function parseNegative(value) {
  const toks = tokenize(value), syms = toks.length >= 1 && toks.length <= 2 ? allSymbols(toks) : null;
  return syms === null ? null : { prefix: syms[0], suffix: syms[1] ?? '' };
}

/** §3.1.5 `prefix` / `suffix`: exactly one `<symbol>`. */
export function parseAffix(value) { const toks = tokenize(value); return toks.length === 1 ? parseSymbol(toks[0]) : null; }

/** Split a token list on its commas — the `#` (comma-list) multiplier. */
const commaGroups = (toks) => toks.reduce((gs, t) => { if (t.t === 'comma') gs.push([]); else gs[gs.length - 1].push(t); return gs; }, [[]]);

/** §3.1.3 `range: [ [<integer> | infinite]{2} ]# | auto` → 'auto' or
 *  [[lo, hi], …] with lo ≤ hi (`3 1` invalid; `1 infinity` invalid — the
 *  keyword is `infinite`). */
export function parseRange(value) {
  const toks = tokenize(value);
  if (toks.length === 1 && toks[0].t === 'ident' && toks[0].v.toLowerCase() === 'auto') return 'auto';
  const groups = commaGroups(toks);
  const bound = (t) => (t.t === 'int' ? t.v : t.t === 'ident' && t.v.toLowerCase() === 'infinite' ? null : undefined);
  const out = [];
  for (const g of groups) {
    if (g.length !== 2) return null;
    const lo = bound(g[0]), hi = bound(g[1]);
    if (lo === undefined || hi === undefined) return null;
    const loN = lo === null ? -Infinity : lo, hiN = hi === null ? Infinity : hi;
    if (loN > hiN) return null;
    out.push([loN, hiN]);
  }
  return out;
}

/** §3.1.6 `pad: <integer [0,∞]> && <symbol>` (either order) → { length, symbol }. */
export function parsePad(value) {
  const toks = tokenize(value);
  if (toks.length !== 2) return null;
  const [a, b] = toks[0].t === 'int' ? [toks[0], toks[1]] : [toks[1], toks[0]];
  if (a.t !== 'int' || a.v < 0) return null;
  const sym = parseSymbol(b);
  return sym === null ? null : { length: a.v, symbol: sym };
}

/** §3.1.7 `fallback: <counter-style-name>`. */
export function parseFallback(value) { return parseName(value); }

/** §3.1.2 `symbols: <symbol>+`. The per-system minimum (≥ 2 for alphabetic
 *  / numeric) is checked by the caller, which knows the system. */
export function parseSymbols(value) {
  const toks = tokenize(value);
  return toks.length === 0 || toks.some((t) => t.t === 'comma') ? null : allSymbols(toks);
}

/** §3.1.2 `additive-symbols: [ <integer [0,∞]> && <symbol> ]#`, weights in
 *  STRICTLY descending order → [[weight, symbol], …]. */
export function parseAdditiveSymbols(value) {
  const toks = tokenize(value);
  const out = [];
  for (const g of toks.length ? commaGroups(toks) : [[]]) {
    // Each tuple is exactly one non-negative integer and one symbol, in
    // either order (`"X" 1` is `1 "X"` — additive-symbols-syntax.html).
    if (g.length !== 2) return null;
    const [w, s] = g[0].t === 'int' ? [g[0], g[1]] : [g[1], g[0]];
    if (w.t !== 'int' || w.v < 0) return null;
    const sym = parseSymbol(s);
    if (sym === null) return null;
    if (out.length && w.v >= out[out.length - 1][0]) return null;   // not strictly descending
    out.push([w.v, sym]);
  }
  return out;
}

/** The descriptor table: kebab-case name → validator. `speak-as` is
 *  accepted verbatim (it never reaches a rendered marker). */
export const DESCRIPTOR_PARSERS = {
  system: parseSystem, symbols: parseSymbols, 'additive-symbols': parseAdditiveSymbols,
  negative: parseNegative, prefix: parseAffix, suffix: parseAffix, range: parseRange,
  pad: parsePad, fallback: parseFallback, 'speak-as': (v) => v.trim() || null,
};

// ── §7.1 CSSOM setter verdict ───────────────────────────────────────────────

/** CSSOM camelCase → descriptor name (`additiveSymbols` → `additive-symbols`);
 *  `name` is the rule's prelude, not a descriptor; anything else is unknown. */
const descriptorOf = (setter) => (setter === 'name' ? 'name'
  : (({}).hasOwnProperty.call(DESCRIPTOR_PARSERS, setter.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()))
    ? setter.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()) : undefined));

/** Would this `CSSCounterStyleRule.<setter> = value` take effect on `rule`
 *  (the parsed target — `{ system, symbolCount }`)? §7.1: a value the
 *  grammar rejects is ignored; `system` may not change the ALGORITHM
 *  (`fixed 0` on a fixed rule is fine, `numeric` on it is not, and
 *  `extends X` may only replace another `extends`); `symbols` /
 *  `additive-symbols` must fit the rule's system (≥ 2 for alphabetic /
 *  numeric, `symbols` never on additive, `additive-symbols` only on it). */
export function setterTakesEffect(setter, value, rule) {
  const desc = descriptorOf(setter);
  if (!desc) return false;
  if (desc === 'name') return parseName(value, { forSetter: true }) !== null;
  const parsed = DESCRIPTOR_PARSERS[desc](value);
  if (parsed === null) return false;
  if (desc === 'system') {
    if (!rule) return false;
    return parsed.system === rule.system && (parsed.system !== 'extends' || parsed.base !== rule.base);
  }
  if (desc === 'symbols') {
    if (!rule || rule.system === 'additive') return false;
    return !(['alphabetic', 'numeric'].includes(rule.system) && parsed.length < 2);
  }
  if (desc === 'additive-symbols') return !!rule && rule.system === 'additive';
  return true;
}
