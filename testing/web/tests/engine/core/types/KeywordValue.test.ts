// Tests for extractKeyword — keyword normalisation behaviour.
import { describe, it, expect } from 'vitest';
import { extractKeyword } from '../../../../src/style/engine/core/types/KeywordValue';

describe('extractKeyword', () => {
  it('passes through lowercase kebab strings', () => {
    expect(extractKeyword('min-content')).toEqual({ normalized: 'min-content' });
  });

  it('lowercases and trims', () => {
    expect(extractKeyword('  FLEX-START  ')).toEqual({ normalized: 'flex-start' });
  });

  it('collapses whitespace/underscores to hyphens', () => {
    expect(extractKeyword('min_content')).toEqual({ normalized: 'min-content' });
    expect(extractKeyword('flex start')).toEqual({ normalized: 'flex-start' });
  });

  it('reads { keyword } key', () => {
    expect(extractKeyword({ keyword: 'auto' })).toEqual({ normalized: 'auto' });
  });

  it('reads { value } key as fallback', () => {
    expect(extractKeyword({ value: 'center' })).toEqual({ normalized: 'center' });
  });

  it('reads { type } key as last resort', () => {
    expect(extractKeyword({ type: 'fit-content' })).toEqual({ normalized: 'fit-content' });
  });

  it('returns null on null/empty/non-string', () => {
    expect(extractKeyword(null)).toBeNull();
    expect(extractKeyword('')).toBeNull();
    expect(extractKeyword({})).toBeNull();
    expect(extractKeyword(42)).toBeNull();
  });
});
