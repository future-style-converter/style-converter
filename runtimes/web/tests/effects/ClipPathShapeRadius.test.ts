// ClipPathShapeRadius.test.ts — pins the `<shape-radius>` keyword path
// through the web extractor (CSS Shapes 1 §3.1). Mirrors the matching
// Kotlin suite at src/test/kotlin/.../ClipPathShapeRadiusTest.kt so the
// invariant trips on both sides if either regresses.
//
// Pre-fix bug: the extractor hard-coded `.replace('()', '(closest-side)')`
// to mask the Kotlin parser silently dropping `<shape-radius>` keywords.
// Now the parser emits `{r:"farthest-side"}` (string primitive) for the
// keyword form and the extractor must pass it through verbatim — not
// re-write everything to `closest-side`.

import { describe, it, expect } from 'vitest';
import { extractClipPath } from '../../src/engine/effects/clip/ClipPathExtractor';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractClipPath <shape-radius> keywords', () => {

  it('circle(farthest-side) — keyword survives verbatim (swarm-003 root cause)', () => {
    // Wire form after the parser fix: `r` is a JSON string primitive
    // carrying the spec keyword. The extractor must emit it as-is.
    const ir = { type: 'circle', r: 'farthest-side' };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(farthest-side)');
  });

  it('circle(closest-side) — keyword passthrough', () => {
    const ir = { type: 'circle', r: 'closest-side' };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(closest-side)');
  });

  it('circle(50%) length form still produces percentage (backward compat)', () => {
    // Legacy wire form: `r` is an IRLength object — must keep working
    // byte-identically since every existing fixture uses this shape.
    const ir = { type: 'circle', r: { original: { v: 50, u: 'PERCENT' } } };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(50%)');
  });

  it('circle(40px) length form (px) round-trips', () => {
    const ir = { type: 'circle', r: { px: 40 } };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(40px)');
  });

  it('ellipse(closest-side farthest-side) — per-axis keywords', () => {
    // Both axes are keywords; each must land independently.
    const ir = { type: 'ellipse', rx: 'closest-side', ry: 'farthest-side' };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('ellipse(closest-side farthest-side)');
  });

  it('ellipse(40px closest-side) — mixed keyword + length per axis', () => {
    const ir = { type: 'ellipse', rx: { px: 40 }, ry: 'closest-side' };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('ellipse(40px closest-side)');
  });

  it('circle(farthest-side) at center — keyword + position', () => {
    // Position uses the wrapped form too — verify the `at` clause still
    // composes when `r` is a keyword (the `replace('()', ...)` hack the
    // old code used would have collided here on a non-empty arg list).
    const ir = {
      type: 'circle',
      r: 'farthest-side',
      pos: {
        x: { original: { v: 50, u: 'PERCENT' } },
        y: { original: { v: 50, u: 'PERCENT' } },
      },
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(farthest-side at 50% 50%)');
  });

  it('circle with geometry-box keeps keyword + box keyword', () => {
    // Combined `{geometry-box, shape}` form — the exact shape the WPT
    // fixture `clip-path-borderBox-1a` emits. The expected CSS is
    // `circle(farthest-side) border-box`.
    const ir = {
      'geometry-box': 'border-box',
      shape: { type: 'circle', r: 'farthest-side' },
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('circle(farthest-side) border-box');
  });
});
