// BlockFontLabel.test.ts — the web platform's pin suite for the shared
// block-font placeholder labels (cross-platform drift guard + layout math).
//
//   1. CHECKSUM PIN (mandatory on every platform): rebuild the canonical
//      atlas serialization from the EMBEDDED constants and sha256 it —
//      the 16-hex prefix must equal BLOCK_FONT_CHECKSUM. Any hand-edit
//      of BlockFont.gen.ts, or a regeneration that landed on only one
//      platform, fails here instead of silently diverging pixels.
//   2. Layout pins: rect count for a known string, the shared truncation
//      rule, unknown-char → '-' mapping, origin/geometry invariants.
import { describe, it, expect } from 'vitest';
// node:crypto supplies sha256 in the test — the runtime module stays
// browser-safe by only building the canonical STRING, never hashing.
import { createHash } from 'node:crypto';
import {
  BLOCK_FONT_ADVANCE,
  BLOCK_FONT_CELL_H,
  BLOCK_FONT_CELL_W,
  BLOCK_FONT_CHECKSUM,
  BLOCK_FONT_GLYPHS,
} from '../../src/renderer/BlockFont.gen';
import {
  BLOCK_LABEL_ORIGIN_X,
  BLOCK_LABEL_ORIGIN_Y,
  blockFontCanonicalSerialization,
  layoutBlockLabel,
  normalizeBlockLabel,
} from '../../src/renderer/BlockFontLabel';

describe('block font atlas checksum (cross-platform drift guard)', () => {
  it('recomputed sha256 of the embedded constants equals the embedded checksum', () => {
    // The generator's exact recipe: sha256 over the canonical
    // serialization, hex, first 16 chars (tools/visual/gen-block-font.mjs).
    const recomputed = createHash('sha256')
      .update(blockFontCanonicalSerialization())
      .digest('hex')
      .slice(0, 16);
    // Must match the constant the generator embedded — proves the atlas
    // bytes on THIS platform are the ones every other platform hashed.
    expect(recomputed).toBe(BLOCK_FONT_CHECKSUM);
  });

  it('pins the atlas checksum literal (belt-and-braces vs a full regen)', () => {
    // The task-pinned value: a regeneration that changes the atlas must
    // be a deliberate cross-platform event, not a drive-by.
    expect(BLOCK_FONT_CHECKSUM).toBe('cb3c6e411c7b2859');
  });
});

describe('layoutBlockLabel — rect generation', () => {
  it('emits one rect per set bit for "BIW 10" (77 rects)', () => {
    // Hand-computed popcounts from the atlas rows:
    // B=20, I=11, W=17, space=0, '1'=10, '0'=19 → 77 total.
    const layout = layoutBlockLabel('BIW 10');
    expect(layout.rects).toHaveLength(77);
    // All 6 normalized chars survive (no width constraint given).
    expect(layout.chars).toBe('BIW 10');
    // Tight extents: (n-1)*advance + cellW wide, one cell tall.
    expect(layout.width).toBe(5 * BLOCK_FONT_ADVANCE + BLOCK_FONT_CELL_W); // 35
    expect(layout.height).toBe(BLOCK_FONT_CELL_H);                          // 7
  });

  it('produces integer block-local coordinates, bit 4 = leftmost column', () => {
    // 'T' row 0 is 31 (11111): five rects at y=0, x=0..4 — pins both the
    // bit order (bit 4 → column 0) and the integer-only contract.
    const layout = layoutBlockLabel('T');
    const row0 = layout.rects.filter((r) => r.y === 0).map((r) => r.x);
    expect(row0).toEqual([0, 1, 2, 3, 4]);
    // Every coordinate is a non-negative integer (no antialiasing, ever).
    for (const r of layout.rects) {
      expect(Number.isInteger(r.x)).toBe(true);
      expect(Number.isInteger(r.y)).toBe(true);
    }
    // Second char's cell starts exactly one advance further right.
    const two = layoutBlockLabel('TT');
    expect(two.rects.filter((r) => r.y === 0).map((r) => r.x))
      .toEqual([0, 1, 2, 3, 4, 6, 7, 8, 9, 10]); // 6 = BLOCK_FONT_ADVANCE
  });

  it('lowercases map to their uppercase glyphs (shared transform)', () => {
    // The spec's transform is uppercase-first — 'abc' and 'ABC' are the
    // same block, rect for rect.
    expect(layoutBlockLabel('abc').rects).toEqual(layoutBlockLabel('ABC').rects);
  });
});

describe('layoutBlockLabel — truncation (8 + n*ADVANCE <= width - 8)', () => {
  it('keeps 24 chars at componentWidth 160', () => {
    // floor((160 - 16) / 6) = 24 — the shared-spec bound, no ellipsis.
    const layout = layoutBlockLabel('A'.repeat(30), 160);
    expect(layout.chars).toBe('A'.repeat(24));
    // Rect count scales exactly: popcount('A' rows) = 18 per glyph.
    expect(layout.rects).toHaveLength(24 * 18);
    // The kept block still fits the shared insets: 8 + 24*6 = 152 <= 152.
    expect(BLOCK_LABEL_ORIGIN_X + 24 * BLOCK_FONT_ADVANCE).toBeLessThanOrEqual(160 - 8);
    // …and one more char would NOT have fit (proves n is maximal).
    expect(BLOCK_LABEL_ORIGIN_X + 25 * BLOCK_FONT_ADVANCE).toBeGreaterThan(160 - 8);
  });

  it('drops trailing characters only (prefix preserved)', () => {
    // Distinct glyphs so a wrong slice direction would change the rects.
    const layout = layoutBlockLabel('ABCDEFGHIJKLMNOPQRSTUVWXYZ', 160);
    expect(layout.chars).toBe('ABCDEFGHIJKLMNOPQRSTUVWX'); // first 24, no '…'
  });

  it('yields an empty layout when nothing fits', () => {
    // width 20 → floor((20-16)/6) = 0 chars — empty block, zero extents.
    const layout = layoutBlockLabel('HELLO', 20);
    expect(layout.chars).toBe('');
    expect(layout.rects).toHaveLength(0);
    expect(layout.width).toBe(0);
    expect(layout.height).toBe(0);
  });

  it('never truncates an unconstrained (content-sized) box', () => {
    // Infinity is the fit-content contract: the box hugs the label.
    const layout = layoutBlockLabel('X'.repeat(100), Infinity);
    expect(layout.chars).toHaveLength(100);
  });
});

describe('unknown-character mapping', () => {
  it("maps '@' (no atlas glyph) to the '-' glyph", () => {
    // Normalization is where the mapping lives…
    expect(normalizeBlockLabel('@')).toBe('-');
    // …and the layout proves it rect-for-rect: '-' is rows [0,0,0,14,0,0,0]
    // → exactly three rects on row 3 at columns 1..3.
    const layout = layoutBlockLabel('@');
    expect(layout.rects).toEqual([{ x: 1, y: 3 }, { x: 2, y: 3 }, { x: 3, y: 3 }]);
    // The '@' layout is identical to a literal '-' label.
    expect(layout.rects).toEqual(layoutBlockLabel('-').rects);
  });

  it('known characters pass through normalization unchanged', () => {
    // Uppercase + digits + the atlas punctuation are all glyph-backed.
    expect(normalizeBlockLabel('MW 10% A.B_C')).toBe('MW 10% A.B_C');
    // A mixed run: unknowns become '-', knowns (post-uppercase) survive.
    expect(normalizeBlockLabel('a+b')).toBe('A-B');
  });
});

describe('shared-spec constants', () => {
  it('pins the (8, 6) origin and the atlas metrics', () => {
    // The origin every platform hardcodes — moving it is a 3-platform
    // baseline event, so a test failure here is the intended alarm.
    expect(BLOCK_LABEL_ORIGIN_X).toBe(8);
    expect(BLOCK_LABEL_ORIGIN_Y).toBe(6);
    // Cell/advance sanity straight from the generated atlas.
    expect(BLOCK_FONT_CELL_W).toBe(5);
    expect(BLOCK_FONT_CELL_H).toBe(7);
    expect(BLOCK_FONT_ADVANCE).toBe(6);
    // Every glyph carries exactly CELL_H rows, each within 5-bit range —
    // a malformed row would silently shift columns at draw time.
    for (const [ch, rows] of Object.entries(BLOCK_FONT_GLYPHS)) {
      expect(rows, `glyph ${JSON.stringify(ch)}`).toHaveLength(BLOCK_FONT_CELL_H);
      for (const row of rows) {
        expect(row).toBeGreaterThanOrEqual(0);
        expect(row).toBeLessThan(1 << BLOCK_FONT_CELL_W);
      }
    }
  });
});
