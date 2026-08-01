/**
 * DecorationSpans — the production `meta.decorations` → DOM policy for the
 * package renderer (wave-22 lane DECOR).
 *
 * THE PROBLEM. When the WPT extractor meets a chain of decoration-only
 * inline wrappers — `<span style="text-decoration:underline;
 * text-decoration-color:blue"><span style="…overline gray"><span
 * style="…line-through green">text</span></span></span>` — it COLLAPSES
 * the subtree into ONE text component (css-text-decor-3 §1.3: the whole
 * chain is one inline formatting run and every ancestor's line paints over
 * the same text). The component keeps a root-wins MERGED flat bag of
 * `text-decoration*` longhands, which is a SUBSET: one line, one colour.
 * Every reader that ignores `meta.decorations` therefore paints one blue
 * underline where the browser painted a blue underline, a gray overline
 * and a green line-through — the exact loss the natives closed with a
 * per-line painter. Web had no reader at all, so it had the same loss.
 *
 * THE FIX, and why it is smaller than the natives'. The two natives must
 * hand-paint bands because Compose/SwiftUI can express only one decoration
 * colour per text node. A browser has no such limit: the ORIGINAL MARKUP
 * is the correct renderer. So this module rebuilds the decorating-box
 * stack as nested wrapper <span>s — outermost-first, one per entry, each
 * carrying its own `text-decoration-line` + `text-decoration-color` — and
 * real Chromium paints all three lines in all three colours exactly as it
 * did before the collapse. No geometry is computed here at all.
 *
 * WHY THE ELEMENT'S OWN LINE IS TURNED OFF. The wire is AUTHORITATIVE, so
 * the element must not paint from its merged flat bag as well: the
 * outermost entry would be painted twice, and — worse — the element's bag
 * carries the run's `text-decoration-style`/`-thickness`, so a `dotted`
 * element line under a `solid` wrapper line would show a solid band over
 * a dotted one. Instead the element declares `text-decoration-line: none`
 * and each wrapper inherits the RUN's style + thickness explicitly (they
 * are per-run, not per-entry: the extractor folds them root-wins into the
 * merged bag — see the `_decorations` banner in extract-fixture.mjs).
 *
 * PRESENT-BUT-EMPTY is still authoritative and paints NOTHING — the same
 * contract the natives' `DecorationColorOps.resolve` enforces.
 *
 * ONE HONEST CAVEAT, stated rather than discovered later. The wrappers are
 * plain inline <span>s, matching the source markup. In the PACKAGE default
 * the content they wrap is a bare text node, so the inline formatting
 * context is exactly the browser-ref's. Under the capture harness's skin,
 * `renderEmptyContent` returns a `display:block` placeholder span, so the
 * wrappers become inline boxes containing a block — the block-in-inline
 * case. Decorations still propagate to in-flow block-level descendants
 * (css-text-decor-3 §1.3 excludes only out-of-flow and ATOMIC inline-level
 * descendants, which a block-in-inline is not), so all three lines paint;
 * but that shape has not been confirmed against a device capture in this
 * lane, and it is the first thing to check if a composed capture disagrees
 * with the ref.
 */

import { createElement } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { IRDecoration } from '../core/ir/IRModels';
// No-silent-fallthrough logging (CLAUDE.md): a line keyword this policy
// doesn't recognise is recorded via the tracker, never dropped silently.
import { logUnhandled } from '../engine/PropertyTracker';

/**
 * The three css-text-decor-3 §2.1 keywords that paint. Byte-parallel with
 * the natives' `DecorationColorOps.LineKind` table: `none` and `blink`
 * paint nothing and never reach a wrapper.
 */
const PAINTABLE_LINES: ReadonlySet<string> = new Set([
  'underline', 'overline', 'line-through',
]);

/**
 * Normalize one wire `line` token to its CSS spelling, or null when it is
 * not one of the three paintable keywords. Accepts the IR's screaming
 * spelling (`LINE_THROUGH`) defensively, exactly like the native twins.
 */
export function normalizeLine(token: string | null | undefined): string | null {
  if (typeof token !== 'string') return null;
  // CSS keywords are ASCII case-insensitive (css-values-4 §3.2); `_` → `-`
  // absorbs the IR enum spelling.
  const css = token.toLowerCase().replace(/_/g, '-');
  return PAINTABLE_LINES.has(css) ? css : null;
}

/**
 * The style overrides the DECORATED ELEMENT itself must carry when a wire
 * is present: its own line is off, because the wrappers own every line.
 *
 * Returns null when there is no wire at all — the caller then leaves the
 * element's styles byte-identical, which is what keeps every
 * non-decorated component's DOM unchanged.
 */
export function decorationHostStyle(
  decorations: IRDecoration[] | null | undefined,
): CSSProperties | null {
  // No wire → not a collapsed run → nothing to override.
  if (!Array.isArray(decorations)) return null;
  // Wire present (empty included): the element paints no line of its own.
  return { textDecorationLine: 'none' };
}

/**
 * Wrap `content` in the decorating-box stack the wire describes.
 *
 * `decorations` outermost-first ⇒ entry 0 becomes the OUTERMOST span, so
 * the DOM nesting mirrors the source markup and the browser's own
 * ancestor-below-descendant paint order applies without us ordering
 * anything by hand.
 *
 * `runStyle` supplies the run's shared `text-decoration-style` and
 * `-thickness` (read off the element's already-built styles) so every
 * wrapper draws in the run's style rather than the CSS initial `solid`.
 *
 * Returns `content` untouched when there is no wire, and when the wire is
 * present-but-empty (authoritative "no lines"): in the empty case the
 * host's `text-decoration-line: none` is the entire, correct effect.
 */
export function withDecorationSpans(
  content: ReactNode,
  decorations: IRDecoration[] | null | undefined,
  runStyle?: { textDecorationStyle?: unknown; textDecorationThickness?: unknown },
): ReactNode {
  if (!Array.isArray(decorations) || decorations.length === 0) return content;
  // Build inside-out: start from the content and wrap once per entry from
  // the INNERMOST entry backwards, so entry 0 ends up outermost.
  let node: ReactNode = content;
  for (let i = decorations.length - 1; i >= 0; i--) {
    const entry = decorations[i];
    const line = normalizeLine(entry?.line);
    if (line === null) {
      // Unpaintable keyword (future §2.1 addition, `blink`, junk): no
      // wrapper, and the drop is recorded — never a guessed line.
      logUnhandled('TextDecorationLine', `meta.decorations line '${String(entry?.line)}'`);
      continue;
    }
    const style: CSSProperties = { textDecorationLine: line };
    // Absent colour = currentColor (§2.2 initial) — omit the declaration
    // entirely so the browser applies the initial itself.
    if (typeof entry.color === 'string' && entry.color.length > 0) {
      style.textDecorationColor = entry.color;
    }
    // Style + thickness are the RUN's, not the entry's (see the header):
    // carry them onto each wrapper or the wrapper would draw the CSS
    // initial `solid`/`auto` under a dotted/10px run.
    if (runStyle?.textDecorationStyle !== undefined) {
      style.textDecorationStyle = runStyle.textDecorationStyle as CSSProperties['textDecorationStyle'];
    }
    if (runStyle?.textDecorationThickness !== undefined) {
      style.textDecorationThickness =
        runStyle.textDecorationThickness as CSSProperties['textDecorationThickness'];
    }
    // A plain inline <span> — the element the source markup used, so the
    // inline formatting context is the one the browser-ref laid out.
    // `data-decoration` marks the synthetic box for tooling/tests exactly
    // as `data-float-run` marks the wave-19 float wrappers.
    node = createElement('span', { key: `decor-${i}`, 'data-decoration': line, style }, node);
  }
  return node;
}
