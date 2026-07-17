/**
 * BlockFontLabel — the PURE LAYOUT engine for the harness-label block
 * font (atlas embedded in ./BlockFont.gen, checksum cb3c6e411c7b2859).
 *
 * The three platforms replace their placeholder-label TEXT rendering
 * (font-stack glyphs, which never rasterize identically across
 * Chromium / Compose / CoreText) with this shared bit-grid layout:
 * per set atlas bit, one 1x1 px filled rect at INTEGER coordinates —
 * so the same label produces byte-identical geometry everywhere and
 * the ~50 text-bearing 327-pair fixtures stop capping at SSIM 0.90-0.949.
 *
 * This module is framework-free on purpose (no React, no DOM): the
 * harness skin builds the actual <svg>/<rect> elements from the layout
 * it returns, and the vitest suite pins the math without rendering.
 */

// The embedded atlas — GENERATED, never edited by hand; the checksum
// pin test (tests/renderer/BlockFontLabel.test.ts) hashes the canonical
// serialization below against BLOCK_FONT_CHECKSUM to catch any drift.
import {
  BLOCK_FONT_ADVANCE,
  BLOCK_FONT_CELL_H,
  BLOCK_FONT_CELL_W,
  BLOCK_FONT_GLYPHS,
  BLOCK_FONT_LINE_HEIGHT,
} from './BlockFont.gen';

/**
 * Shared-spec label origin: the block's top-left sits at (8, 6) from the
 * component's top-left, in the SAME coordinate space the old text span
 * occupied (so component padding etc. keeps shifting it identically on
 * every platform). Pinned cross-platform — all three runtimes hardcode
 * the same pair, so the rects land on the same device pixels.
 */
export const BLOCK_LABEL_ORIGIN_X = 8;
/** Vertical half of the shared-spec origin (see BLOCK_LABEL_ORIGIN_X). */
export const BLOCK_LABEL_ORIGIN_Y = 6;

/**
 * Shared-spec fill — the light placeholder-label color all three
 * platforms already used for the dark-bg branch (iOS
 * Color(white:0.93).opacity(0.7)). The block font pins it as the ONLY
 * label color: a single fill on every platform is what keeps the rects
 * byte-identical (the old per-platform bg-luminance flip stays for
 * real TEXT content, which the block font never touches).
 */
export const BLOCK_LABEL_FILL = 'rgba(237, 237, 237, 0.7)';

/**
 * Rebuild the canonical atlas serialization from the EMBEDDED constants
 * — byte-identical to what tools/visual/gen-block-font.mjs hashed when
 * it emitted BlockFont.gen.ts. The per-platform pin test sha256s this
 * and compares the 16-hex-char prefix against BLOCK_FONT_CHECKSUM, so
 * any hand-edit of the generated file (or a regeneration that only
 * landed on one platform) fails loudly instead of silently diverging.
 */
export function blockFontCanonicalSerialization(): string {
  // Glyph keys sorted by codepoint — the generator's deterministic order.
  const chars = Object.keys(BLOCK_FONT_GLYPHS).sort((a, b) => a.charCodeAt(0) - b.charCodeAt(0));
  // `<charCode>:<row,row,…>` per glyph, ';'-joined — the generator's shape.
  return chars.map((c) => `${c.charCodeAt(0)}:${BLOCK_FONT_GLYPHS[c].join(',')}`).join(';')
    // Metric suffix pins cell size / advance / line-height into the hash too.
    + `|${BLOCK_FONT_CELL_W}x${BLOCK_FONT_CELL_H}|adv${BLOCK_FONT_ADVANCE}|lh${BLOCK_FONT_LINE_HEIGHT}`;
}

/**
 * Shared-spec input transform: uppercase the label (the atlas carries
 * capitals only), then map every character WITHOUT an atlas glyph to
 * '-' — a visible tofu marker rather than a silent drop, so a label
 * with unexpected characters still shows where they were.
 */
export function normalizeBlockLabel(label: string): string {
  // toUpperCase() is locale-independent for ASCII — the only range the
  // atlas covers; anything fancier falls into the '-' mapping anyway.
  return Array.from(label.toUpperCase())
    // Unknown char → '-' (atlas-guaranteed glyph); known chars pass through.
    .map((ch) => (BLOCK_FONT_GLYPHS[ch] ? ch : '-'))
    // Re-join into the normalized single-line string the layout consumes.
    .join('');
}

/** One 1x1 px filled rect, in BLOCK-LOCAL coordinates (see layout doc). */
export interface BlockLabelRect {
  /** X of the rect's top-left, relative to the block's own top-left. */
  x: number;
  /** Y of the rect's top-left, relative to the block's own top-left. */
  y: number;
}

/** The computed label block — everything a renderer needs to draw it. */
export interface BlockLabelLayout {
  /** The normalized, truncated character run actually being drawn. */
  chars: string;
  /** All set-bit rects, block-local; place the block at (ORIGIN_X, ORIGIN_Y). */
  rects: BlockLabelRect[];
  /** Tight block width in px: (n-1)*advance + cellW (0 when no chars fit). */
  width: number;
  /** Block height in px: one cell row (0 when no chars fit). */
  height: number;
}

/**
 * Lay out one label as the shared-spec block: normalize, truncate to the
 * component width, then emit one rect per set atlas bit.
 *
 * Coordinates are BLOCK-LOCAL (the block's own top-left is 0,0): the
 * spec's absolute rule "cell origin x = 8 + i*ADVANCE, y = 6" is
 * recovered by positioning the whole block at (BLOCK_LABEL_ORIGIN_X,
 * BLOCK_LABEL_ORIGIN_Y) — which is exactly how the harness places the
 * <svg>. Keeping the rects origin-relative lets the svg carry tight
 * width/height while the margin supplies the shared origin.
 *
 * @param componentWidth the component's px width for the shared-spec
 *   truncation `8 + n*ADVANCE <= componentWidth - 8`; pass Infinity
 *   (the default) when the box is content-sized (fit-content hugs the
 *   label, so nothing can overflow and no truncation applies).
 */
export function layoutBlockLabel(label: string, componentWidth: number = Infinity): BlockLabelLayout {
  // Shared-spec transform first — truncation counts NORMALIZED chars.
  const chars = normalizeBlockLabel(label);
  // Truncation: largest n with 8 + n*ADVANCE <= componentWidth - 8
  // (symmetric 8 px insets), never negative, never past the label end.
  const maxChars = Number.isFinite(componentWidth)
    ? Math.max(0, Math.floor((componentWidth - 2 * BLOCK_LABEL_ORIGIN_X) / BLOCK_FONT_ADVANCE))
    : chars.length;                                     // unconstrained box → keep everything
  // Drop TRAILING characters only (no ellipsis — the spec forbids one).
  const kept = chars.slice(0, Math.min(chars.length, maxChars));
  // Accumulate one rect per set bit across the kept run.
  const rects: BlockLabelRect[] = [];
  for (let i = 0; i < kept.length; i++) {
    // Glyph rows for this char — guaranteed present post-normalization.
    const rows = BLOCK_FONT_GLYPHS[kept[i]];
    // Cell origin advances by the fixed pitch (spec: x = i*ADVANCE, block-local).
    const cellX = i * BLOCK_FONT_ADVANCE;
    for (let row = 0; row < BLOCK_FONT_CELL_H; row++) {
      for (let col = 0; col < BLOCK_FONT_CELL_W; col++) {
        // Bit 4 of the row int is the LEFTMOST column (generator contract),
        // so column c reads bit (CELL_W - 1 - c).
        if ((rows[row] >> (BLOCK_FONT_CELL_W - 1 - col)) & 1) {
          // One 1x1 px rect per set bit — integer coords, no antialiasing.
          rects.push({ x: cellX + col, y: row });
        }
      }
    }
  }
  // Tight extents: the last cell contributes CELL_W, not a full ADVANCE
  // (the inter-glyph gap has no pixels, so the svg needn't cover it).
  const width = kept.length > 0 ? (kept.length - 1) * BLOCK_FONT_ADVANCE + BLOCK_FONT_CELL_W : 0;
  // Single line → one cell of height; zero when nothing survived truncation.
  const height = kept.length > 0 ? BLOCK_FONT_CELL_H : 0;
  // Hand back everything the renderer and the tests need.
  return { chars: kept, rects, width, height };
}
