// Tests for MarginTrimExtractor & MarginTrimApplier.
import { describe, it, expect } from 'vitest';
import { extractMarginTrim } from '../../../src/style/engine/spacing/MarginTrimExtractor';
import { applyMarginTrim } from '../../../src/style/engine/spacing/MarginTrimApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractMarginTrim', () => {
  it('parses bare SCREAMING_SNAKE_CASE keywords', () => {
    expect(extractMarginTrim([p('MarginTrim', 'NONE')])).toEqual({ value: 'none' });
    expect(extractMarginTrim([p('MarginTrim', 'BLOCK')])).toEqual({ value: 'block' });
    expect(extractMarginTrim([p('MarginTrim', 'INLINE')])).toEqual({ value: 'inline' });
  });

  it('normalises hyphenated compound keywords', () => {
    expect(extractMarginTrim([p('MarginTrim', 'BLOCK_START')])).toEqual({ value: 'block-start' });
    expect(extractMarginTrim([p('MarginTrim', 'BLOCK_END')])).toEqual({ value: 'block-end' });
    expect(extractMarginTrim([p('MarginTrim', 'INLINE_START')])).toEqual({ value: 'inline-start' });
    expect(extractMarginTrim([p('MarginTrim', 'INLINE_END')])).toEqual({ value: 'inline-end' });
  });

  it('returns null when no MarginTrim is present', () => {
    expect(extractMarginTrim([p('MarginTop', { px: 10 })])).toBeNull();
  });

  it('rejects unsupported compound tokens', () => {
    // "block inline" is not a parsed IR shape in current fixtures; we skip it.
    expect(extractMarginTrim([p('MarginTrim', 'block inline')])).toBeNull();
  });

  it('ignores non-string IR payloads defensively', () => {
    expect(extractMarginTrim([p('MarginTrim', { keyword: 'NONE' })])).toBeNull();
  });
});

describe('applyMarginTrim', () => {
  it('emits the marginTrim CSS property', () => {
    expect(applyMarginTrim({ value: 'block-start' })).toEqual({ marginTrim: 'block-start' });
  });
});
