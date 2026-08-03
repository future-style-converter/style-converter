// tools/titan/counter-style-table.mjs — wave-27 lane CBAKE, part 1 of 2.
//
// The css-counter-styles-3 §6 PREDEFINED COUNTER STYLES, transcribed as
// data. Split out of counter-style-bake.mjs purely for the CLAUDE.md size
// rule (≤200 lines/file): this module is the table, that one is the
// algorithms + the fixture pass.
//
// TRANSCRIPTION CONTRACT (why each field is shaped the way it is):
//   • `system`  — the §2 counter system that formats an integer.
//   • `symbols` — the ordered symbol list the system consumes.
//   • `add`     — `[weight, symbol]` pairs for `system: additive`, in the
//                 spec's DESCENDING weight order (§2.6 requires it).
//   • `range`   — `[min, max]` of representable counter values (§4). A
//                 value outside it falls back (§7.1.4) to `fallback`.
//   • `suffix`  — the §3.1.5 `suffix` descriptor, ALREADY TRIMMED of its
//                 trailing space (see SUFFIX NOTE below).
//   • `fallback`— the §3.1.7 `fallback` descriptor; initial is `decimal`.
//
// SUFFIX NOTE — the one deliberate deviation from a literal transcription.
// The spec's initial `suffix` is "\2E\20" (a full stop THEN A SPACE), and
// the §6.1 bullet styles use " ". Both native runtimes already own that
// trailing gap themselves — Compose renders `Text("$marker ")` with a 4dp
// end padding and SwiftUI an `HStack(spacing: 4)` — and their existing
// tables (ListMarkerText.marker / StyleListApplier.getMarker) emit "1.",
// never "1. ". Emitting the space here would double it. So every suffix
// below is the spec's minus the trailing U+0020, which makes this table a
// strict superset of the two native tables rather than a rival to them.
//
// SCOPE: §6.1 (bullets) is deliberately ABSENT — those markers are
// ordinal-INDEPENDENT, the natives already paint them, and re-baking them
// would move every disc/circle/square baseline in the corpus for no
// measured gain. The bake documents that boundary instead of silently
// covering it (see counter-style-bake.mjs `MARKER_FAMILY_OUT_OF_SCOPE`).

/** Ten consecutive digit codepoints starting at `base` — every §6.2
 *  "numeric" predefined style is exactly one such block, so generating
 *  them beats transcribing 190 characters that a typo could silently
 *  corrupt. Verified against the live WPT expectations (bengali 10 →
 *  "১০", cambodian 1860 → "១៨៦០", arabic-indic 1 → "١"). */
const digits = (base) => Array.from({ length: 10 }, (_, i) => String.fromCodePoint(base + i));

/** One code-point run as a symbol array (alphabetic systems). */
const run = (from, to) => Array.from({ length: to - from + 1 }, (_, i) => String.fromCodePoint(from + i));

/** Split a literal string into its per-character symbol list. Used where
 *  the spec's symbol order is NOT a contiguous code-point run. */
const chars = (s) => [...s];

// ── §6.2 numeric systems ────────────────────────────────────────────────────
// Every entry is `system: numeric` over a 0-9 digit block; suffix is the
// initial "." (space trimmed). `cjk-decimal` is the one numeric style with
// its own suffix descriptor ("、", U+3001 IDEOGRAPHIC COMMA) — that comma
// carries no trailing space in the spec either, so it survives whole.
const NUMERIC = {
  'decimal': digits(0x0030),            // §6.2 decimal — plain ASCII 0-9
  'arabic-indic': digits(0x0660),       // U+0660..0669 ARABIC-INDIC DIGIT
  'bengali': digits(0x09E6),            // U+09E6..09EF BENGALI DIGIT
  'cambodian': digits(0x17E0),          // U+17E0..17E9 KHMER DIGIT
  'khmer': digits(0x17E0),              // §6.2: khmer is an ALIAS of cambodian
  'devanagari': digits(0x0966),         // U+0966..096F DEVANAGARI DIGIT
  'gujarati': digits(0x0AE6),           // U+0AE6..0AEF GUJARATI DIGIT
  'gurmukhi': digits(0x0A66),           // U+0A66..0A6F GURMUKHI DIGIT
  'kannada': digits(0x0CE6),            // U+0CE6..0CEF KANNADA DIGIT
  'lao': digits(0x0ED0),                // U+0ED0..0ED9 LAO DIGIT
  'malayalam': digits(0x0D66),          // U+0D66..0D6F MALAYALAM DIGIT
  'mongolian': digits(0x1810),          // U+1810..1819 MONGOLIAN DIGIT
  'myanmar': digits(0x1040),            // U+1040..1049 MYANMAR DIGIT
  'oriya': digits(0x0B66),              // U+0B66..0B6F ORIYA DIGIT
  'persian': digits(0x06F0),            // U+06F0..06F9 EXTENDED ARABIC-INDIC
  'tamil': digits(0x0BE6),              // U+0BE6..0BEF TAMIL DIGIT
  'telugu': digits(0x0C66),             // U+0C66..0C6F TELUGU DIGIT
  'thai': digits(0x0E50),               // U+0E50..0E59 THAI DIGIT
  'tibetan': digits(0x0F20),            // U+0F20..0F29 TIBETAN DIGIT
};

// ── §6.2 alphabetic systems ─────────────────────────────────────────────────
// `system: alphabetic` is bijective base-N over the symbol list (§2.4):
// a…z, aa, ab, … — NOT a cyclic index. `lower-greek` deliberately omits
// final sigma (ς), exactly as the spec's 24-symbol list does. The kana
// styles carry the "、" suffix descriptor.
const ALPHABETIC = {
  'lower-alpha': run(0x0061, 0x007A),   // a…z
  'upper-alpha': run(0x0041, 0x005A),   // A…Z
  'lower-greek': chars('αβγδεζηθικλμνξοπρστυφχψω'),   // 24 symbols, no ς
  'hiragana': chars('あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわゐゑをん'),
  'hiragana-iroha': chars('いろはにほへとちりぬるをわかよたれそつねならむうゐのおくやまけふこえてあさきゆめみしゑひもせす'),
  'katakana': chars('アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヰヱヲン'),
  'katakana-iroha': chars('イロハニホヘトチリヌルヲワカヨタレソツネナラムウヰノオクヤマケフコエテアサキユメミシヱヒモセス'),
};

/** The kana + cjk styles whose `suffix` descriptor is U+3001 (§6.2). */
const IDEOGRAPHIC_COMMA_STYLES = new Set([
  'cjk-decimal', 'hiragana', 'hiragana-iroha', 'katakana', 'katakana-iroha',
]);

// ── §6.2 additive systems ───────────────────────────────────────────────────
// `[weight, symbol]` pairs, descending. Roman is the classic subtractive-
// notation table; armenian/georgian are positional myriad tables. Each is
// paired with the spec's `range` because an additive system can only spell
// values its weights cover — outside it the style FALLS BACK (§7.1.4),
// which is precisely what css3-counter-styles-008 asserts (armenian 9999 →
// "ՔՋՂԹ", 10000 → the decimal fallback "10000").
const ROMAN_LOWER = [
  [1000, 'm'], [900, 'cm'], [500, 'd'], [400, 'cd'], [100, 'c'], [90, 'xc'],
  [50, 'l'], [40, 'xl'], [10, 'x'], [9, 'ix'], [5, 'v'], [4, 'iv'], [1, 'i'],
];

/** Build an armenian/georgian-shaped additive table from four myriad rows
 *  (thousands, hundreds, tens, units), each a 9-symbol string in ASCENDING
 *  digit order (the way the alphabets are conventionally written out).
 *  Each row is reversed on emit so the whole table comes out in the strictly
 *  DESCENDING weight order §2.6's additive algorithm requires. */
function positional(thousands, hundreds, tens, units) {
  const rows = [[1000, thousands], [100, hundreds], [10, tens], [1, units]];
  return rows.flatMap(([place, syms]) =>
    chars(syms).map((sym, i) => [place * (i + 1), sym]).reverse());
}

const ARMENIAN_UPPER = positional('ՌՍՎՏՐՑՒՓՔ', 'ՃՄՅՆՇՈՉՊՋ', 'ԺԻԼԽԾԿՀՁՂ', 'ԱԲԳԴԵԶԷԸԹ');
const ARMENIAN_LOWER = positional('ռսվտրցւփք', 'ճմյնշոչպջ', 'ժիլխծկհձղ', 'աբգդեզէըթ');
// Georgian additionally spells 10000 with ჵ (U+10F5), so its range reaches
// 19999 — the extra weight is prepended to the positional body.
const GEORGIAN = [[10000, 'ჵ'],
  ...positional('ჩცძწჭხჴჯჰ', 'რსტჳფქღყშ', 'იკლმნჲოპჟ', 'აბგდევზჱთ')];
// Hebrew's table is NOT positional: §6.2 spells 15/16 as טו/טז (avoiding
// the divine name) and 17-19 with an explicit yod prefix, then geresh-
// marked thousands. Transcribed verbatim, descending.
const HEBREW = [
  [10000, 'י׳'], [9000, 'ט׳'], [8000, 'ח׳'], [7000, 'ז׳'], [6000, 'ו׳'],
  [5000, 'ה׳'], [4000, 'ד׳'], [3000, 'ג׳'], [2000, 'ב׳'], [1000, 'א׳'],
  [400, 'ת'], [300, 'ש'], [200, 'ר'], [100, 'ק'], [90, 'צ'], [80, 'פ'],
  [70, 'ע'], [60, 'ס'], [50, 'נ'], [40, 'מ'], [30, 'ל'], [20, 'כ'],
  [19, 'יט'], [18, 'יח'], [17, 'יז'], [16, 'טז'], [15, 'טו'], [10, 'י'],
  [9, 'ט'], [8, 'ח'], [7, 'ז'], [6, 'ו'], [5, 'ה'], [4, 'ד'], [3, 'ג'],
  [2, 'ב'], [1, 'א'],
];

const ADDITIVE = {
  // Roman's spec `range` is `1 3999` — beyond that the table cannot spell.
  'lower-roman': { add: ROMAN_LOWER, range: [1, 3999] },
  'upper-roman': { add: ROMAN_LOWER.map(([w, s]) => [w, s.toUpperCase()]), range: [1, 3999] },
  'armenian': { add: ARMENIAN_UPPER, range: [1, 9999] },
  'upper-armenian': { add: ARMENIAN_UPPER, range: [1, 9999] },
  'lower-armenian': { add: ARMENIAN_LOWER, range: [1, 9999] },
  'georgian': { add: GEORGIAN, range: [1, 19999] },
  'hebrew': { add: HEBREW, range: [1, 10999] },
};

/**
 * The assembled §6 table this bake claims. Every entry is
 * `{ system, symbols?, add?, range, suffix, fallback, pad? }`.
 *
 * A name ABSENT from this map is not "decimal by default" — the bake
 * treats it as UNSUPPORTED and declines to stamp (see the bake's
 * `counter-style-unsupported` reason), which leaves the two natives on
 * their own tables instead of substituting a wrong string.
 */
export const PREDEFINED = Object.fromEntries([
  // numeric: `range` is `auto` = [-∞, ∞] for numeric systems (§4), so no
  // value ever falls back; represented as null and read as "always in range".
  ...Object.entries(NUMERIC).map(([name, symbols]) => [name, {
    system: 'numeric', symbols, range: null, fallback: 'decimal',
    suffix: IDEOGRAPHIC_COMMA_STYLES.has(name) ? '、' : '.',
  }]),
  // cjk-decimal is numeric over the ideographic zero-nine block (§6.2) —
  // and it is the ONE numeric style with an explicit `range: 0 infinity`
  // rather than `auto`, so a negative value falls back to `decimal` and is
  // spelled in ASCII. Blink agrees: `<ol start="-2" style="list-style-type:
  // cjk-decimal">` paints "-2、" "-1、", NOT "-二、" (MEASURED).
  ['cjk-decimal', {
    system: 'numeric', symbols: chars('〇一二三四五六七八九'),
    range: [0, Infinity], fallback: 'decimal', suffix: '、',
  }],
  // decimal-leading-zero: `system: extends decimal; pad: 2 "0"` (§6.2).
  ['decimal-leading-zero', {
    system: 'numeric', symbols: NUMERIC.decimal, range: null,
    fallback: 'decimal', suffix: '.', pad: { length: 2, symbol: '0' },
  }],
  // alphabetic: `range` auto = [1, ∞] (§4 — the system cannot spell 0).
  ...Object.entries(ALPHABETIC).map(([name, symbols]) => [name, {
    system: 'alphabetic', symbols, range: [1, Infinity], fallback: 'decimal',
    suffix: IDEOGRAPHIC_COMMA_STYLES.has(name) ? '、' : '.',
  }]),
  // §6.2 latin aliases — same symbol list, distinct keyword (§6.2 note).
  ['lower-latin', { system: 'alphabetic', symbols: ALPHABETIC['lower-alpha'], range: [1, Infinity], fallback: 'decimal', suffix: '.' }],
  ['upper-latin', { system: 'alphabetic', symbols: ALPHABETIC['upper-alpha'], range: [1, Infinity], fallback: 'decimal', suffix: '.' }],
  // additive: explicit spec ranges, decimal fallback.
  ...Object.entries(ADDITIVE).map(([name, { add, range }]) => [name, {
    system: 'additive', add, range, fallback: 'decimal', suffix: '.',
  }]),
]);

/** css-counter-styles-3 §6.1 — the ordinal-INDEPENDENT bullet styles plus
 *  `none`. Recognised so the bake can tell "out of scope by design" apart
 *  from "unknown keyword", and report each honestly. */
export const BULLET_STYLES = new Set([
  'disc', 'circle', 'square', 'disclosure-open', 'disclosure-closed', 'none',
]);
