// CanvasTableBody.ts — wave-53 lane L3 (item B): a `display: table` BODY lays
// its children out as a table on the composed web canvas.
//
// WHY: the extractor emits html+body as ONE synthetic `body-root` whose element
// children are SIBLING roots, so the body's `display: table` reached only an
// empty box and the children stacked inside the canvas's block `flow-root`
// wrapper. MEASURED on wave52-ship CSS2/css21-errata/s-11-1-1b-006 web P 0.9941
// (a wrong picture): the caption div's 10-px margin pushed the td's square to
// image rows 66-85, where the reference (Chrome laying a real table body out)
// has 56-75. Chrome IS the reference engine, so the web fix is to make the
// box that stands in for the body — ComposedCaptureGallery's
// `data-capture-flow` wrapper — a table box: Chrome then runs CSS 2.1 §17.2.1
// (anonymous row + cells around the non-row children) itself, the td's margin
// stops applying (§8.3), and the empty body-root node lands in the first
// anonymous cell at 0×0. The native twins (Compose / SwiftUI TableBodyForest)
// build that same row/cell tree explicitly.

import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
// No-silent-fallthrough breadcrumb for a border-spacing leaf this cannot serialise.
import { logUnhandled } from '@style-converter/web/engine/PropertyTracker';

/** The wrapper's table-box declarations for a table body. */
export interface CanvasTableBody {
  display: 'table' | 'inline-table';   // css-display-3 §2.3, from the body's own Display
  borderSpacing?: string;              // §17.6.1, from the body's BorderSpacing (absent = initial 0)
}

/** The body Display enum leaves that make the body a table box. */
const TABLE_BODY: Readonly<Record<string, CanvasTableBody['display']>> = {
  TABLE: 'table', INLINE_TABLE: 'inline-table',
};

/** A component's LAST declared `Display` enum leaf, uppercased (undefined when absent). */
function displayOf(c: IRComponent): string | undefined {
  const d = [...(c.properties as Array<{ type: string; data?: unknown }>)].reverse()
    .find((p) => p.type === 'Display')?.data;
  return typeof d === 'string' ? d.toUpperCase() : undefined;
}

/** One concrete px length leaf (`{px:N}` or `{original:{px:N}}`) as CSS, or undefined. */
function pxCss(d: unknown): string | undefined {
  const o = d as { px?: unknown; original?: { px?: unknown } } | null;
  const px = typeof o?.px === 'number' ? o.px : o?.original?.px;
  return typeof px === 'number' ? `${px}px` : undefined;
}

/**
 * The raw `BorderSpacing` leaf (BorderSpacingProperty.Spacing: `single` with
 * the length inlined, or `two-values` {horizontal, vertical}) as CSS. Read
 * here because the runtime's phase-10 table serialiser drops the `single`
 * shape (keywordOrRaw has no length arm) — a separate, pre-existing web gap
 * this canvas does not widen.
 */
function borderSpacingCss(body: IRComponent): string | undefined {
  const leaf = [...(body.properties as Array<{ type: string; data?: unknown }>)].reverse()
    .find((p) => p.type === 'BorderSpacing')?.data as { type?: unknown; horizontal?: unknown; vertical?: unknown } | undefined;
  if (leaf === undefined) return undefined;                          // initial 0 — nothing to write
  if (leaf.type === 'single') {
    const one = pxCss(leaf);                                          // the length is inlined
    if (one) return one;
  } else if (leaf.type === 'two-values') {
    const h = pxCss(leaf.horizontal); const v = pxCss(leaf.vertical);
    if (h && v) return `${h} ${v}`;                                   // horizontal first (§17.6.1)
  }
  logUnhandled('BorderSpacing', 'canvas table body: a non-px border-spacing is not serialised');
  return undefined;
}

/**
 * The table-body declarations for one per-test document, or null when the
 * document has no body-root or the body is not a table box — then the
 * gallery's markup is exactly what it was (1434 of the 1435 documents).
 */
export function resolveCanvasTableBody(doc: IRDocument): CanvasTableBody | null {
  // Same lookup rule as every canvas resolver — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return null;
  const display = TABLE_BODY[displayOf(bodyRoot) ?? ''];
  if (!display) return null;                                          // not a table body
  const borderSpacing = borderSpacingCss(bodyRoot);
  return borderSpacing === undefined ? { display } : { display, borderSpacing };
}
