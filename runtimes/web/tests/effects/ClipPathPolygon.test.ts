// ClipPathPolygon.test.ts — pins polygon serialisation across the
// IRLengthPercentage axis kinds the Kotlin parser now produces. Mirrors
// the same five invariants as src/test/kotlin/.../ClipPathPolygonParserTest.kt
// so a regression on either side trips a red dot.

import { describe, it, expect } from 'vitest';
import { extractClipPath } from '../../src/engine/effects/clip/ClipPathExtractor';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractClipPath polygon', () => {
  it('pure percent polygon (legacy raw-number wire form) → all "%" points', () => {
    // Legacy wire format: each x/y is a raw JSON number, interpreted as percent.
    const ir = {
      type: 'polygon',
      points: [
        { x: 0,   y: 0 },
        { x: 100, y: 0 },
        { x: 100, y: 100 },
        { x: 0,   y: 100 },
      ],
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)');
  });

  it('pure px polygon → all "px" points', () => {
    // New wire form: each x/y is an IRLength object like {px: N}.
    const ir = {
      type: 'polygon',
      points: [
        { x: { px: 0 },   y: { px: 0 } },
        { x: { px: 100 }, y: { px: 0 } },
        { x: { px: 100 }, y: { px: 100 } },
        { x: { px: 0 },   y: { px: 100 } },
      ],
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('polygon(0px 0px, 100px 0px, 100px 100px, 0px 100px)');
  });

  it('WPT blending-offset polygon — the swarm-002 root cause', () => {
    // Six vertices of an inverted-L. Vertex 0 is `{px: 0}` because the
    // parser maps unitless `0` to Length(0px) so it lives in the same
    // coordinate space as the explicit px vertices.
    const ir = {
      type: 'polygon',
      points: [
        { x: { px: 0 },   y: { px: 0 } },
        { x: { px: 100 }, y: { px: 0 } },
        { x: { px: 100 }, y: { px: 30 } },
        { x: { px: 30 },  y: { px: 30 } },
        { x: { px: 30 },  y: { px: 100 } },
        { x: { px: 0 },   y: { px: 100 } },
      ],
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe(
      'polygon(0px 0px, 100px 0px, 100px 30px, 30px 30px, 30px 100px, 0px 100px)'
    );
  });

  it('mixed % + px on different vertices preserves per-axis kind', () => {
    const ir = {
      type: 'polygon',
      points: [
        { x: 0,           y: 0 },
        { x: { px: 100 }, y: 50 },
        { x: 50,          y: { px: 100 } },
      ],
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    expect(cfg.value).toBe('polygon(0% 0%, 100px 50%, 50% 100px)');
  });

  it('em / rem length coordinates serialise with their original unit', () => {
    // IRLengthSerializer encodes relative units as {original: {v, u}} with no `px`.
    // The shared `extractLength` helper recognises that shape and emits e.g. `1.5em`.
    const ir = {
      type: 'polygon',
      points: [
        { x: { original: { v: 0, u: 'PX' } },    y: { original: { v: 0, u: 'PX' } } },
        { x: { original: { v: 2, u: 'EM' } },    y: { original: { v: 0, u: 'PX' } } },
        { x: { original: { v: 2, u: 'EM' } },    y: { original: { v: 1.5, u: 'REM' } } },
      ],
    };
    const cfg = extractClipPath([p('ClipPath', ir)]);
    // Use a regex-style match because IRLength tolerates either `0` or `0px`
    // for the zero case via toCssLength's normalisation.
    expect(cfg.value).toMatch(/^polygon\(.*2em 0.*2em 1\.5rem.*\)$/);
  });
});
