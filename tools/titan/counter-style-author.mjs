// tools/titan/counter-style-author.mjs — wave 52, lane L6 (T7), part 2 of 2.
//
// AUTHOR `@counter-style` rules for the counter-style bake. Before this wave
// `counter-style-bake.mjs` bailed the WHOLE fixture on the substring
// `@counter-style` ("a style whose descriptors never reach the fixture"), so
// 26 gate tests carried the raw author name to the wire and all three
// runtimes painted decimal: wave51-fix css-counter-styles/cssom/
// cssom-pad-setter-invalid paints "1. 2. 3." on every platform where the ref
// paints "001. 002. 003." (`f 0.9821 / 0.9819 / 0.9820`). But the descriptors
// DO reach the fixture — they are in the authored `<style>` — and §3 is a
// closed grammar over closed algorithms, so this module parses them into the
// same table shape as `PREDEFINED` and the bake's §7.1 algorithm runs on it.
//
// Scope: static rules in the document's `<style>` blocks. Rules inside a
// `<script>` template literal (the shadow-DOM tests) are NOT read — the
// script gate (cssomScriptVerdict) bails those fixtures as before, since a
// shadow tree is a scope this extractor never walks.

import { PREDEFINED, BULLET_STYLES } from './counter-style-table.mjs';
import {
  DESCRIPTOR_PARSERS, IMAGE_SYMBOL, parseName, setterTakesEffect,
} from './counter-style-descriptors.mjs';
// Index-based `<script>` scanning (wave 52): the regex it replaced was a
// CodeQL bad-tag-filter finding; html-blocks.test.mjs pins byte-equality.
import { scriptBlocks, stripScripts } from './html-blocks.mjs';

/** Strip `<script>…</script>` so a template-literal `<style>` inside a script
 *  (override-in-shadow-dom) is never read as a document-scope rule. */
const withoutScripts = (html) => stripScripts(html);

/**
 * Every `@counter-style` rule in the document's `<style>` blocks, in source
 * order: `{ name, styleId, index, decls }` where `decls` keeps the LAST
 * VALID value per descriptor (CSS drops an invalid declaration and a later
 * valid one overrides — descriptor-pad-invalid: `pad: 3 "0"` survives three
 * invalid re-declarations). `styleId` / `index` let the CSSOM verdict map
 * `sheet.sheet.rules[N]` back to a rule.
 */
export function extractCounterStyleRules(html) {
  const out = [];
  const styleRx = /<style\b([^>]*)>([\s\S]*?)<\/style>/gi;
  let sm;
  while ((sm = styleRx.exec(withoutScripts(html))) !== null) {
    const styleId = (sm[1].match(/\bid\s*=\s*["']?([^"'\s>]+)/i) || [])[1] ?? null;
    const css = sm[2].replace(/\/\*[\s\S]*?\*\//g, '');            // CSS comments are not declarations
    const ruleRx = /@counter-style\s+([^\s{]+)\s*\{([^}]*)\}/g;
    let rm, index = 0;
    while ((rm = ruleRx.exec(css)) !== null) {
      const name = parseName(rm[1]);
      const decls = {};
      for (const decl of rm[2].split(';')) {
        const colon = decl.indexOf(':');
        if (colon < 0) continue;
        const desc = decl.slice(0, colon).trim().toLowerCase();
        const parser = DESCRIPTOR_PARSERS[desc];
        if (!parser) continue;                                    // unknown descriptor: dropped, like CSS
        const parsed = parser(decl.slice(colon + 1).trim());
        if (parsed !== null) decls[desc] = parsed;                // invalid value: dropped, earlier one stands
      }
      // `@counter-style decimal` / `disc` / `none` cannot be defined (§3):
      // the rule is invalid and contributes nothing — recorded with
      // `invalidName` so the CSSOM index bookkeeping stays aligned.
      out.push({ name, styleId, index: index++, decls, invalidName: name === null
        || ['decimal', 'disc'].includes(name.toLowerCase()) });
    }
  }
  return out;
}

/** §4 `range: auto` per system. */
const AUTO_RANGE = { cyclic: null, numeric: null, fixed: null, alphabetic: [[1, Infinity]], symbolic: [[1, Infinity]], additive: [[0, Infinity]] };
/** A PREDEFINED entry in the author table's shape (ranges as a list). */
const fromPredefined = (p) => ({ ...p, ranges: p.range ? [p.range] : null, prefix: '', negative: { prefix: '-', suffix: '' } });

/**
 * Resolve the extracted rules into `{ name → entry }`. An entry is
 * `{ system, symbols?, add?, first?, ranges, fallback, prefix, suffix, pad?,
 *   negative }` — a strict superset of PREDEFINED's shape — or `{ bullet }`
 * for a style that `extends` a §6.1 bullet (out of the bake's scope, like
 * the bullet itself). An INVALID rule (missing `symbols`, `extends` with
 * `symbols`, a CSS-wide keyword symbol, a reserved name) defines nothing:
 * the name stays undefined, so `list-style-type: a` behaves as decimal
 * (§2) — broken-symbols, where Blink paints "1.". Duplicate names: the
 * last rule wins (§3). `extends` of an undefined name or a cycle is
 * `extends decimal` (§3.1.1).
 */
export function buildAuthorTable(rules) {
  const byName = new Map();
  for (const r of rules) if (!r.invalidName) byName.set(r.name, r);
  const table = {};
  const resolve = (name, chain) => {
    if (name in table) return table[name];
    const rule = byName.get(name);
    if (!rule) {                                                   // not an author rule → predefined / bullet / undefined
      const lower = name.toLowerCase();
      return PREDEFINED[lower] ? fromPredefined(PREDEFINED[lower]) : BULLET_STYLES.has(lower) ? { bullet: lower } : undefined;
    }
    const d = rule.decls;
    const sys = d.system ?? { system: 'symbolic' };               // §3.1.1 initial value
    let entry;
    if (sys.system === 'extends') {
      if (d.symbols || d['additive-symbols']) return undefined;   // §3.1.1: extends may not declare symbols → invalid
      const base = chain.includes(name) ? undefined : resolve(sys.base, [...chain, name]);
      const parent = base ?? fromPredefined(PREDEFINED.decimal);  // undefined / cyclic → extends decimal
      if (parent.bullet) { table[name] = { bullet: parent.bullet }; return table[name]; }
      entry = { ...parent };
    } else {
      const syms = d.symbols, add = d['additive-symbols'];
      if (sys.system === 'additive' ? !add : !syms) return undefined;                 // required descriptor missing
      if (['alphabetic', 'numeric'].includes(sys.system) && syms.length < 2) return undefined;
      if ((syms ?? add.map(([, s]) => s)).includes(IMAGE_SYMBOL)) return undefined;   // image symbols: unmodelled
      entry = { system: sys.system, symbols: syms, add, first: sys.first, ranges: AUTO_RANGE[sys.system],
        fallback: 'decimal', prefix: '', suffix: '.', negative: { prefix: '-', suffix: '' } };
      if (sys.system === 'fixed') entry.ranges = [[sys.first, sys.first + syms.length - 1]];
    }
    // Author overrides on top of the base (extends) or the defaults.
    if (d.negative) entry.negative = d.negative;
    if (d.prefix !== undefined) entry.prefix = d.prefix;
    // SUFFIX NOTE (counter-style-table.mjs): the natives supply the gap after
    // the marker, so a trailing U+0020 is trimmed here as it is for §6.
    if (d.suffix !== undefined) entry.suffix = String(d.suffix).replace(/ +$/, '');
    if (d.range !== undefined) entry.ranges = d.range === 'auto' ? (entry.system === 'fixed' ? entry.ranges : AUTO_RANGE[entry.system]) : d.range;
    if (d.pad) entry.pad = d.pad;
    if (d.fallback) entry.fallback = d.fallback;
    table[name] = entry;
    return entry;
  };
  for (const name of byName.keys()) resolve(name, []);
  return table;
}

// ── the §2 systems only author rules reach (no §6 predefined style uses them) ──

/** §2.1 cyclic: symbols repeat, 1 → first; defined for every integer. */
export function cyclicRepresentation(value, symbols) {
  const n = symbols.length;
  return symbols[(((value - 1) % n) + n) % n];
}
/** §2.2 fixed: one symbol per value from `first`; outside → null (fallback). */
export function fixedRepresentation(value, symbols, first) {
  const i = value - first;
  return i >= 0 && i < symbols.length ? symbols[i] : null;
}
/** §2.5 symbolic: the symbol cycles and REPEATS once more per cycle
 *  (a b c aa bb cc …); magnitude ≥ 1 only (range auto = 1..∞). */
export function symbolicRepresentation(value, symbols) {
  const n = symbols.length;
  return symbols[(value - 1) % n].repeat(Math.ceil(value / n));
}

// ── §7.1 CSSOM: does the document's script change a rule after load? ────────

/** The only statements the recogniser understands — the exact shape of the
 *  15 cssom tests: a layout flush, a sheet lookup, a rule lookup, setters. */
const BOILERPLATE = [/^document\.body\.offsetWidth;?$/, /^(?:const|let|var)\s+(\w+)\s*=\s*document\.getElementById\((['"])([^'"]+)\2\);?$/,
  /^(?:const|let|var)\s+(\w+)\s*=\s*(\w+)\.sheet\.(?:rules|cssRules)\[(\d+)\];?$/];
const SETTER = /^(\w+)\.(name|system|symbols|additiveSymbols|negative|prefix|suffix|range|pad|fallback|speakAs)\s*=\s*(['"])(.*)\3;?$/;

/**
 * `{ kind: 'none' }` — no script; `{ kind: 'foreign' }` — a script the
 * recogniser cannot read (external, `attachShadow`, anything else: the
 * bake bails exactly as it always did); `{ kind: 'cssom', mutates,
 * assignments }` — only CSSCounterStyleRule setters, where `mutates` is true
 * iff at least one assignment TAKES EFFECT under §7.1 (setterTakesEffect),
 * i.e. the post-load rendering differs from the static rule.
 */
export function cssomScriptVerdict(html, rules) {
  // Same shape the matchAll gave: [whole, attrs, body] per block (html-blocks.mjs).
  const bodies = scriptBlocks(html).map((b) => [null, b.attrs, b.body]);
  if (bodies.length === 0) return { kind: 'none' };
  if (bodies.some((m) => /\bsrc\s*=/i.test(m[1]))) return { kind: 'foreign' };
  const sheets = {}, ruleVars = {}, assignments = [];
  for (const [, , body] of bodies) {
    const lines = body.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '').split('\n').map((l) => l.trim()).filter(Boolean);
    for (const line of lines) {
      let m;
      if (BOILERPLATE[0].test(line)) continue;
      if ((m = line.match(BOILERPLATE[1]))) { sheets[m[1]] = m[3]; continue; }
      if ((m = line.match(BOILERPLATE[2]))) { ruleVars[m[1]] = { styleId: sheets[m[2]], index: Number(m[3]) }; continue; }
      if ((m = line.match(SETTER))) {
        const ref = ruleVars[m[1]];
        const target = ref && rules.find((r) => r.styleId === ref.styleId && r.index === ref.index);
        if (!target) return { kind: 'foreign' };                  // a setter on a rule we cannot see
        const sys = target.decls.system ?? { system: 'symbolic' };
        assignments.push({ setter: m[2], value: m[4], takesEffect: setterTakesEffect(m[2], m[4], sys) });
        continue;
      }
      return { kind: 'foreign' };                                  // anything else is a real script
    }
  }
  return { kind: 'cssom', mutates: assignments.some((a) => a.takesEffect), assignments };
}
