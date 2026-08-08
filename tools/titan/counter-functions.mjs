//
// tools/titan/counter-functions.mjs — wave-37 lane W8, part 1 of 2.
//
// The css-lists-3 VALUE GRAMMAR this bake consumes, split out of
// counter-bake.mjs for the CLAUDE.md size rule: this module is pure parsing
// (no tree, no state), that one is the document walk.
//
// Three grammars live here:
//   • `counter-reset`     — `[<name> <integer>? | reversed(<name>) <integer>?]+ | none`
//   • `counter-increment` / `counter-set` — `[<name> <integer>?]+ | none`
//   • the `counter()` / `counters()` functions inside a `content` value,
//     located as SPANS so the caller can splice resolved text back in
//     without re-serialising the rest of the declaration.
//
// Everything returns null on anything it does not fully understand (a
// `var()`, an `attr()`, a malformed pair). The bake turns null into a
// REFUSAL — the fixture is left byte-identical — which is the same discipline
// counter-style-bake.mjs and generated-content-bake.mjs already run on.

/** A CSS <custom-ident> for a counter name. `none` is the property's own
 *  "no counters" keyword and can never be a name. */
const IDENT_RX = /^-?[_a-zA-Z -￿][-_a-zA-Z0-9 -￿]*$/;

/** Split a declaration into top-level tokens, keeping `reversed(foo)` and
 *  quoted strings whole. Parentheses nest; whitespace outside them splits. */
function tokenize(value) {
  const out = [];
  let cur = '';
  let depth = 0;
  let quote = null;
  for (let i = 0; i < value.length; i++) {
    const ch = value[i];
    if (quote) {
      cur += ch;
      if (ch === '\\') { cur += value[++i] ?? ''; continue; }
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '"' || ch === '\'') { quote = ch; cur += ch; continue; }
    if (ch === '(') { depth++; cur += ch; continue; }
    if (ch === ')') { depth--; cur += ch; continue; }
    if (depth === 0 && /\s/.test(ch)) { if (cur) out.push(cur); cur = ''; continue; }
    cur += ch;
  }
  if (cur) out.push(cur);
  return depth === 0 && quote === null ? out : null;
}

/**
 * Parse a `counter-reset` value.
 *
 * @returns Array<{ name, value: number|null, reversed: boolean }> — `value`
 *   null means the integer was OMITTED, which for a plain counter is the
 *   initial 0 and for `reversed()` is the IMPLIED value the walk computes
 *   (css-lists-3 §4.2). `[]` for `none`. null when unparseable.
 */
export function parseCounterReset(value) {
  if (typeof value !== 'string') return null;
  const toks = tokenize(value.trim());
  if (toks === null) return null;
  if (toks.length === 1 && toks[0].toLowerCase() === 'none') return [];
  const out = [];
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i];
    const rev = /^reversed\(\s*([^)]*?)\s*\)$/i.exec(t);
    const name = rev ? rev[1] : t;
    if (!IDENT_RX.test(name) || name.toLowerCase() === 'none') return null;
    // An <integer> may follow the name; anything else ends this pair.
    let num = null;
    if (i + 1 < toks.length && /^[-+]?\d+$/.test(toks[i + 1])) {
      num = Number.parseInt(toks[++i], 10);
    }
    out.push({ name, value: num, reversed: !!rev });
  }
  return out.length ? out : null;
}

/**
 * Parse a `counter-increment` / `counter-set` value. Same shape as above
 * minus `reversed()`, which those properties do not accept. `dflt` is the
 * integer an omitted one means: 1 for counter-increment, 0 for counter-set.
 */
export function parseCounterPairs(value, dflt) {
  if (typeof value !== 'string') return null;
  const toks = tokenize(value.trim());
  if (toks === null) return null;
  if (toks.length === 1 && toks[0].toLowerCase() === 'none') return [];
  const out = [];
  for (let i = 0; i < toks.length; i++) {
    const name = toks[i];
    if (!IDENT_RX.test(name) || name.toLowerCase() === 'none') return null;
    let num = dflt;
    if (i + 1 < toks.length && /^[-+]?\d+$/.test(toks[i + 1])) {
      num = Number.parseInt(toks[++i], 10);
    }
    out.push({ name, value: num });
  }
  return out.length ? out : null;
}

/** Split a function's argument list on TOP-LEVEL commas, strings intact. */
function splitArgs(src) {
  const out = [];
  let cur = '';
  let depth = 0;
  let quote = null;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (quote) {
      cur += ch;
      if (ch === '\\') { cur += src[++i] ?? ''; continue; }
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '"' || ch === '\'') { quote = ch; cur += ch; continue; }
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; continue; }
    cur += ch;
  }
  out.push(cur.trim());
  return out;
}

/** Unescape a CSS <string> token (`"a\"b"` → `a"b`). Mirrors
 *  generated-content-bake.mjs's unescapeCssString for the separator
 *  argument of `counters()`, the one string this module has to read. */
export function readCssString(token) {
  if (typeof token !== 'string' || token.length < 2) return null;
  const q = token[0];
  if ((q !== '"' && q !== '\'') || token[token.length - 1] !== q) return null;
  const body = token.slice(1, -1);
  let out = '';
  for (let i = 0; i < body.length; i++) {
    if (body[i] !== '\\') { out += body[i]; continue; }
    const hex = /^([0-9a-fA-F]{1,6})\s?/.exec(body.slice(i + 1));
    if (hex) {
      out += String.fromCodePoint(Number.parseInt(hex[1], 16));
      i += hex[0].length;
      continue;
    }
    out += body[i + 1] ?? '';
    i += 1;
  }
  return out;
}

/**
 * Locate every `counter()` / `counters()` call in a `content` value.
 *
 * @returns Array<{ start, end, fn, name, sep, style }> in source order, or
 *   null when a call is present but malformed (the caller must refuse rather
 *   than paint a partial string). `[]` when the value has no counter call —
 *   the common case, and the signal to leave the declaration alone.
 */
export function findCounterCalls(content) {
  if (typeof content !== 'string') return null;
  const out = [];
  const rx = /(?<![-\w])(counters?)\s*\(/gi;
  let m;
  while ((m = rx.exec(content)) !== null) {
    const open = m.index + m[0].length - 1;
    // Walk to the matching ')', respecting nesting and strings.
    let depth = 1;
    let i = open + 1;
    let quote = null;
    for (; i < content.length && depth > 0; i++) {
      const ch = content[i];
      if (quote) {
        if (ch === '\\') i++;
        else if (ch === quote) quote = null;
        continue;
      }
      if (ch === '"' || ch === '\'') { quote = ch; continue; }
      if (ch === '(') depth++;
      else if (ch === ')') depth--;
    }
    if (depth !== 0) return null;
    const fn = m[1].toLowerCase();
    const args = splitArgs(content.slice(open + 1, i - 1));
    const name = args[0]?.trim();
    if (!name || !IDENT_RX.test(name)) return null;
    let sep = null;
    let style = 'decimal';
    if (fn === 'counters') {
      // `counters(name, <string>, <style>?)` — the separator is REQUIRED.
      if (args.length < 2 || args.length > 3) return null;
      sep = readCssString(args[1]);
      if (sep === null) return null;
      // <counter-style-name> is a custom-ident and CASE-SENSITIVE
      // (css-counter-styles-3 §3): `Hiragana` is NOT `hiragana`, which is the
      // whole assertion of counter-style-at-rule/name-case-sensitivity.html.
      // An unmatched spelling therefore reaches counterRepresentation as-is
      // and is REFUSED there rather than silently resolving to the lowercase
      // style the author did not name.
      if (args.length === 3) style = args[2].trim();
    } else {
      // `counter(name, <style>?)`
      if (args.length > 2) return null;
      if (args.length === 2) style = args[1].trim();   // case-sensitive, see above
    }
    if (!IDENT_RX.test(style)) return null;
    out.push({ start: m.index, end: i, fn, name, sep, style });
    rx.lastIndex = i;
  }
  return out;
}

/** Serialise resolved text as a CSS <string> so the rewritten declaration
 *  stays a legal `content` value that generated-content-bake.mjs can parse. */
export function asCssString(text) {
  return `"${String(text).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}
