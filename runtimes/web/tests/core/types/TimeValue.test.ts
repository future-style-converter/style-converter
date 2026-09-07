// Tests for extractTime — shapes from examples/primitives/times.json.
//
// Retro P2e (dangling-pointer sweep): every `examples/primitives/*.json`
// path above is GONE — renamed to `fixtures/primitives/` by restructure
// 02e4c457, then deleted by the 2026-07-08 hard prune 1e0234f6 (#8), with
// nothing to replace it. The shapes enumerated here (and the pins over
// them) are now the only record of that wire contract: read the names as
// history, not as a path to open.
import { describe, it, expect } from 'vitest';
import { extractTime } from '../../../src/engine/core/types/TimeValue';

describe('extractTime', () => {
  it('handles canonical { ms:N }', () => {
    expect(extractTime({ ms: 500 })).toEqual({ milliseconds: 500 });
  });

  it('converts seconds via original when ms missing', () => {
    const out = extractTime({ original: { v: 0.3, u: 'S' } });
    expect(out!.milliseconds).toBeCloseTo(300, 5);                 // 0.3s -> 300ms (float)
  });

  it('handles { ms:N, original:{v,u:"S"} } (ms wins)', () => {
    expect(extractTime({ ms: 300, original: { v: 0.3, u: 'S' } }))
      .toEqual({ milliseconds: 300 });
  });

  it('accepts bare number as ms', () => {
    expect(extractTime(750)).toEqual({ milliseconds: 750 });
  });

  it('handles ms unit explicitly', () => {
    expect(extractTime({ original: { v: 500, u: 'MS' } })).toEqual({ milliseconds: 500 });
  });

  it('returns null on invalid inputs', () => {
    expect(extractTime(null)).toBeNull();
    expect(extractTime({})).toBeNull();
    expect(extractTime({ original: { v: 1, u: 'NOPE' } })).toBeNull();
  });
});
