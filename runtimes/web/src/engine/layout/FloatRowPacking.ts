// FloatRowPacking.ts — CSS 2.1 §9.5 float-run SEGMENTATION for the WPT
// capture skin (wave-19 lane FLOAT). Third twin of the natives' pure
// packers (Compose layout/FloatRowPacking.kt ↔ iOS FloatRowPacking.swift):
// the web engine only needs the SEGMENTATION half (pins P1/P2) — the
// packing itself (P3-P5) is the browser's own float layout, run inside a
// `display:flow-root; width:max-content` wrapper the skin injects per run.
//
// Why a wrapper at all: the captured IR wraps the test roots in SYNTHETIC
// 100px frames (extractor artifact), so the browser's honest float layout
// wraps the justify-self-001 rows at 100px (3/2/3/2/3/1) while the
// browser-ref — whose containing block is the ≥360px BODY — paints
// 3/2/5/4. A max-content wrapper gives each clear-delimited run a
// containing block exactly as wide as the run, so rows break ONLY at the
// `<br clear>` markers (the natives' availableWidth=∞ twin, pin P4), and
// the over-wide run overflows the synthetic frame visibly (CSS
// overflow:visible), matching the ref crop.

import type { CSSProperties } from 'react';
import { extractClear } from './ClearExtractor';
import { extractFloat } from './FloatExtractor';
import type { IRPropertyLike } from './_shared';

// P6 — the composed-WPT ref line-box pin, in CSS px: 16px ref font ×
// REF line-height 1.25 (capture-browser-ref REF_LINE_HEIGHT == Compose
// REF_DEFAULT_FONT_LINE_HEIGHT_RATIO == iOS wptRefLineBoxPx 20). The
// ref's `<br clear:both>` occupies one empty line box, so each
// br-terminated run advances max(run height, 20px) — expressed on the
// wrapper as a min-height (flow-root height already includes the floats'
// bottom margin edges, CSS 2.1 §10.6.7).
export const FLOAT_ROW_STRUT_PX = 20;

/** Per-sibling facts the segmenter consumes (pins P1/P2). */
export interface FloatChildFacts {
  // P1: Float ∈ {left, inline-start} — the engine's kebab keywords.
  floatsLeft: boolean;
  // P2: childless + no float + Clear ∈ {both, left, inline-start} —
  // the `<br clear>` IR shape. clear:right does NOT clear a left run
  // (§9.5.2: clearance only past same-side floats).
  clearBreaksLeft: boolean;
}

/**
 * Derive facts from a child's typed IR list — through the engine's own
 * Float/Clear extractors (single parsing owners, kebab-cased keywords).
 * `hasChildren` guards the clear-break shape: a clear on a CONTENT box
 * is real layout, only the childless `<br>` marker is a pure row break.
 */
export function floatChildFacts(properties: IRPropertyLike[], hasChildren: boolean): FloatChildFacts {
  const float = extractFloat(properties).value;                     // kebab keyword or undefined
  const clear = extractClear(properties).value;                     // kebab keyword or undefined
  // Unknown/absent float keywords fold to the CSS initial `none`
  // (invalid declaration → initial value) — same else-arm Compose's
  // FloatExtractor applies, so all three twins agree on junk keywords
  // (skeptic cross-probe pin). The four floating keywords are the full
  // FloatPropertyParser emission set.
  const floats = float === 'left' || float === 'inline-start'
    || float === 'right' || float === 'inline-end';
  return {
    // P1 — left-family floats only; right/inline-end keep today's path (F4).
    floatsLeft: float === 'left' || float === 'inline-start',
    // P2 — the break marker must not itself float and must be childless.
    clearBreaksLeft:
      !hasChildren &&
      !floats &&
      (clear === 'both' || clear === 'left' || clear === 'inline-start'),
  };
}

/** One segment of the child list, in sibling order (twin of the natives'). */
export interface FloatSegment {
  // Child indices (into the original sibling list) in order.
  indices: number[];
  // True for a packable float run (always ≥2 indices then).
  isRun: boolean;
  // P2/P6: the sibling immediately after the run is a clear-break
  // marker — the wrapper gets the line-box strut (min-height).
  strutted: boolean;
}

/**
 * P1 — split the sibling list into maximal float runs and singles.
 * A run is ≥2 CONSECUTIVE left-floating siblings; anything else (clear
 * marker, right float, in-flow box) ends the streak and renders as a
 * single. Lone left-floats stay singles too — the browser already lays
 * one float correctly, keeping the gated surface minimal (F4).
 * Byte-parallel with the natives' FloatRowPacking.segment.
 */
export function segmentFloatRuns(children: FloatChildFacts[]): FloatSegment[] {
  // Output accumulator — segments in sibling order.
  const out: FloatSegment[] = [];
  // Current streak of consecutive left-float indices.
  let streak: number[] = [];
  // Flush the open streak: a run when ≥2, singles otherwise. `next` is
  // the index AFTER the streak (or null at the end) — it decides the
  // strut (P2: a trailing clear-break marker).
  const flush = (next: number | null): void => {
    if (streak.length >= 2) {
      // The strut fires only when the run is terminated by a clear-break
      // sibling (the `<br clear>` line box, P6).
      const strutted = next !== null && children[next].clearBreaksLeft;
      out.push({ indices: streak, isRun: true, strutted });
    } else {
      // 0 or 1 floats — each keeps the plain child path.
      for (const i of streak) out.push({ indices: [i], isRun: false, strutted: false });
    }
    streak = [];
  };
  // Single pass over the siblings, building streaks.
  children.forEach((c, i) => {
    if (c.floatsLeft) {
      // Extend the current left-float streak.
      streak.push(i);
    } else {
      // Streak broken by this sibling — flush, then emit the breaker
      // itself as a single (clear markers render as 0-height divs).
      flush(i);
      out.push({ indices: [i], isRun: false, strutted: false });
    }
  });
  // Trailing streak: no next sibling → never strutted.
  flush(null);
  return out;
}

/**
 * The run wrapper's inline style (see the file header for the WHY):
 *  • flow-root — the wrapper establishes a BFC so it CONTAINS its
 *    floats (CSS 2.1 §10.6.7: height includes the floats' bottom margin
 *    edges) and stacks as a normal block between runs;
 *  • width:max-content — the run's containing block is exactly the
 *    row's summed margin boxes, so the browser never wraps mid-run
 *    (row breaks happen only at the clear markers — pin P4's ∞ twin);
 *  • minHeight (strutted runs only) — the br line-box floor (pin P6).
 */
export function floatRunWrapperStyle(strutted: boolean): CSSProperties {
  return {
    display: 'flow-root',                                            // contain own floats (§10.6.7)
    width: 'max-content',                                            // never wrap mid-run (P4)
    ...(strutted ? { minHeight: `${FLOAT_ROW_STRUT_PX}px` } : {}),   // br line-box strut (P6)
  };
}
