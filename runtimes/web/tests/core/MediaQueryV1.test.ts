// MediaQueryV1.test.ts — pins for the runtime-v1 media grammar validator,
// added with the CodeQL js/polynomial-redos fix: the `and` split now runs
// on a whitespace-COLLAPSED copy of the query (linear) instead of the
// backtracking-prone `\s+and\s+` pattern. These tests pin that the
// grammar's accept/reject behavior survived the rewrite byte-for-byte.
import { describe, it, expect } from 'vitest';

import { parseMediaQueryV1 } from '../../src/core/renderer/MediaQueryV1';

describe('parseMediaQueryV1', () => {
  it('accepts single width and scheme terms', () => {
    // The two v1 features (spec 06 §4), px widths only.
    expect(parseMediaQueryV1('(min-width: 390px)')).toEqual([{ feature: 'min-width', px: 390 }]);
    expect(parseMediaQueryV1('(max-width:250.5px)')).toEqual([{ feature: 'max-width', px: 250.5 }]);
    expect(parseMediaQueryV1('(prefers-color-scheme: dark)')).toEqual([
      { feature: 'prefers-color-scheme', scheme: 'dark' },
    ]);
  });

  it('splits conjunctions on `and` regardless of surrounding whitespace shape', () => {
    // Whitespace runs collapse before the split, so tabs/newlines/multi-
    // space around `and` all parse identically to a single space.
    const expected = [
      { feature: 'min-width', px: 100 },
      { feature: 'max-width', px: 900 },
    ];
    expect(parseMediaQueryV1('(min-width: 100px) and (max-width: 900px)')).toEqual(expected);
    expect(parseMediaQueryV1('(min-width: 100px)   AND \t (max-width: 900px)')).toEqual(expected);
    expect(parseMediaQueryV1('(min-width: 100px)\n and \n(max-width: 900px)')).toEqual(expected);
  });

  it('still requires whitespace around `and` — glued text stays malformed', () => {
    // `)and (` has no whitespace before `and`, so no split happens and the
    // unsplit blob fails both term regexes → whole-bucket-inactive null.
    expect(parseMediaQueryV1('(min-width: 100px)and (max-width: 900px)')).toBeNull();
  });

  it('rejects everything outside the v1 grammar (whole-bucket rule)', () => {
    expect(parseMediaQueryV1('')).toBeNull();                               // empty
    expect(parseMediaQueryV1('screen, (min-width: 100px)')).toBeNull();     // comma list
    expect(parseMediaQueryV1('not (min-width: 100px)')).toBeNull();         // not prefix
    expect(parseMediaQueryV1('(min-width: 10em)')).toBeNull();              // non-px unit
    expect(parseMediaQueryV1('(orientation: landscape)')).toBeNull();       // other feature
    expect(parseMediaQueryV1('(min-width: 100px) and')).toBeNull();         // dangling and
  });

  it('handles pathological space-heavy input in linear time', () => {
    // The old `\s+and\s+` split backtracked polynomially on long space
    // runs (CodeQL js/polynomial-redos). Post-fix this is a linear scan;
    // 100k spaces must parse (to null) effectively instantly.
    const bomb = `(min-width: 100px)${' '.repeat(100_000)}!`;
    const t0 = performance.now();
    expect(parseMediaQueryV1(bomb)).toBeNull();
    expect(performance.now() - t0).toBeLessThan(500);                      // generous CI margin
  });
});
