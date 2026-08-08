// LineClampExtractor.ts — folds `LineClamp` IR properties into a LineClampConfig.
// Family: line-clamp.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { LineClampConfig, LINE_CLAMP_PROPERTY_TYPE, LineClampPropertyType } from './LineClampConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isLineClampProperty(type: string): type is LineClampPropertyType {
  return type === LINE_CLAMP_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // LineClamp: {type:'none'} | {type:'lines', count:N} | {type:'auto'}
  // Native key is line-clamp; applier also emits the -webkit-box trio.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'auto') return 'auto';                             // css-overflow-4 §5.1 height-driven clamp
  if (o.type === 'lines' && typeof o.count === 'number') return o.count;
  return undefined;
}

// ── `line-clamp: auto` used-value resolution ────────────────────────────────
//
// css-overflow-4 §5.1: `auto` clamps at the LAST line box that still fits the
// element's block-size constraint, so the clamp COUNT is a used value — the IR
// deliberately carries no number (`{"type":"auto"}`).  Chrome (the harness
// browser, and every shipping engine as of the 2026 corpus) implements no
// `line-clamp` longhand at all — only legacy `-webkit-line-clamp: <integer>` —
// so the web runtime has to hand it an integer or nothing happens.  This block
// derives that integer from the component's OWN sibling IR properties, which
// the extractor already receives in full.
//
// The `lh` case is exact and needs no font metrics: `max-height: 4.5lh` is 4.5
// line boxes BY DEFINITION, so the count is floor(4.5) with no division and no
// dependence on how the line-height itself resolves.  62 of the corpus's 67
// `line-clamp: auto` tests are written that way.

// One line-height in px, or null when it is not statically knowable.
// Shapes come from LineHeightPropertyParser (see LineHeightExtractor's survey):
//   { original:{type:'length', px:N} }  — absolute length (the corpus's `32px`)
//   { multiplier:N, original:{type:'number', value:N} } — unitless × font-size
// `normal` is deliberately NOT guessed: its used value is a font metric the
// runtime cannot see, and a wrong guess would clamp at the wrong line.
function lineHeightPx(properties: IRPropertyLike[]): number | null {
  let lh: number | null = null;                                     // last-write-wins, like every other extractor
  let fontSize: number | null = null;                               // needed only for the unitless flavour
  for (const p of properties) {
    if (p.type === 'FontSize' && p.data && typeof p.data === 'object') {
      const px = (p.data as Record<string, unknown>).px;            // FontSize normalises to px in the IR
      if (typeof px === 'number') fontSize = px;
    }
    if (p.type !== 'LineHeight' || !p.data || typeof p.data !== 'object') continue;
    const o = p.data as Record<string, unknown>;
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig && orig.type === 'length' && typeof orig.px === 'number') { lh = orig.px; continue; }
    if (typeof o.multiplier === 'number') { lh = -o.multiplier; continue; }  // negative marker: multiplier, resolved below
    lh = null;                                                      // 'normal' / percentage / unresolved → give up
  }
  if (lh === null) return null;
  if (lh >= 0) return lh;                                           // already absolute px
  const mult = -lh;                                                 // undo the marker
  return fontSize === null ? null : mult * fontSize;                // unitless × used font-size
}

// The element's block-size constraint, expressed in LINE BOXES when possible.
// Returns null when nothing constrains the block size (then `auto` clamps
// nothing — spec-correct, and the four corpus tests with no max-height are
// exactly the "does not clamp" assertions).
function constraintInLines(properties: IRPropertyLike[]): number | null {
  // Read the three block-size longhands the corpus actually uses.  Each is
  // captured as either { original:{v,u:'LH'} } (line-relative) or { px:N }.
  const read = (d: unknown): { lines?: number; px?: number } | null => {
    if (!d || typeof d !== 'object') return null;
    const o = d as Record<string, unknown>;
    const orig = o.original as Record<string, unknown> | undefined;
    // `lh` / `rlh` are line-box units — 4.5lh IS 4.5 line boxes, exactly.
    if (orig && typeof orig.v === 'number' && typeof orig.u === 'string' &&
        (orig.u.toUpperCase() === 'LH' || orig.u.toUpperCase() === 'RLH')) {
      return { lines: orig.v };
    }
    if (typeof o.px === 'number') return { px: o.px };               // absolute → needs a line-height
    return null;                                                     // %, calc(), viewport units → unresolved
  };
  let maxH: { lines?: number; px?: number } | null = null;           // max-height (or height) — the clamp bound
  let minH: { lines?: number; px?: number } | null = null;           // min-height overrides a smaller max-height
  for (const p of properties) {
    if (p.type === 'MaxHeight' || p.type === 'MaxBlockSize') maxH = read(p.data) ?? maxH;
    else if (p.type === 'Height' || p.type === 'BlockSize') maxH = maxH ?? read(p.data);
    else if (p.type === 'MinHeight' || p.type === 'MinBlockSize') minH = read(p.data) ?? minH;
  }
  if (!maxH) return null;                                            // unconstrained → `auto` never clamps
  // CSS 2.1 §10.7: min-height WINS over a smaller max-height, so the used
  // block size — and therefore the clamp point — is the larger of the two.
  const toLines = (v: { lines?: number; px?: number }): number | null => {
    if (typeof v.lines === 'number') return v.lines;
    const lh = lineHeightPx(properties);
    return lh && lh > 0 && typeof v.px === 'number' ? v.px / lh : (v.px === 0 ? 0 : null);
  };
  const maxLines = toLines(maxH);
  if (maxLines === null) return null;
  const minLines = minH ? toLines(minH) : null;
  return minLines !== null && minLines > maxLines ? minLines : maxLines;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractLineClamp(properties: IRPropertyLike[]): LineClampConfig {
  const cfg: LineClampConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isLineClampProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  // Resolve `auto` once, AFTER the cascade fold, so a later `line-clamp:none`
  // in the same component cancels it exactly the way CSS does.
  if (cfg.value === 'auto') {
    cfg.auto = true;                                                  // remembered even when unresolved
    const lines = constraintInLines(properties);
    // Floor: a partial line box does not fit, so `max-height:4.5lh` clamps at
    // 4 (the css-overflow-4 "last line before the clamp point" rule).  A
    // count of 0 is legal here (`max-height:0`) but is NOT a legal
    // -webkit-line-clamp value, so the applier clips instead — see there.
    if (lines !== null && Number.isFinite(lines) && lines >= 0) cfg.autoLines = Math.floor(lines);
  }
  return cfg;
}
