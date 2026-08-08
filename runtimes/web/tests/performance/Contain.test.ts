// Contain.test.ts — wave-36 lane M4 pins for the CSS `contain` wire shape.
//
// The pre-wave-36 pin (performancePhase10.test.ts) fed `data: 'STRICT'`, a
// shape the Kotlin reader never emits: ContainProperty is a single-field data
// class over List<ContainValue>, so kotlinx.serialization flattens it to a BARE
// ARRAY.  These pins are transcribed from the wave-35 full-corpus IR
// (tools/titan/runs/wave35-webmap/sections/css-contain/out/tmpOutput.json) so
// the regression cannot come back through a fictional payload.

import { describe, it, expect } from 'vitest';
import { extractContain, parseContain } from '../../src/engine/performance/ContainExtractor';
import { applyContain } from '../../src/engine/performance/ContainApplier';
import { applyPerformancePhase10 } from '../../src/engine/performance/_dispatch';

const css = (data: unknown) => applyContain(extractContain([{ type: 'Contain', data }]));

describe('Contain — real converter wire (bare array)', () => {
  it('["STRICT"] → contain: strict', () => {
    expect(css(['STRICT'])).toEqual({ contain: 'strict' });
  });
  it('["CONTENT"] → contain: content', () => {
    expect(css(['CONTENT'])).toEqual({ contain: 'content' });
  });
  it('["INLINE_SIZE"] → contain: inline-size (underscore kebabed)', () => {
    expect(css(['INLINE_SIZE'])).toEqual({ contain: 'inline-size' });
  });
  it('["LAYOUT","PAINT"] → space-joined in source order', () => {
    expect(css(['LAYOUT', 'PAINT'])).toEqual({ contain: 'layout paint' });
  });
  it('["SIZE","LAYOUT","STYLE","PAINT"] → the strict expansion spelled out', () => {
    expect(css(['SIZE', 'LAYOUT', 'STYLE', 'PAINT']))
      .toEqual({ contain: 'size layout style paint' });
  });
  it('["NONE"] → contain: none', () => {
    expect(css(['NONE'])).toEqual({ contain: 'none' });
  });
});

describe('Contain — defensive shapes and rejection', () => {
  it('space-separated string still works (legacy/hand-authored fixture)', () => {
    expect(css('layout paint')).toEqual({ contain: 'layout paint' });
  });
  it('{values:[…]} object form works (future serializer flag)', () => {
    expect(css({ values: ['PAINT'] })).toEqual({ contain: 'paint' });
  });
  it('duplicate tokens collapse', () => {
    expect(parseContain(['PAINT', 'PAINT', 'LAYOUT'])).toBe('paint layout');
  });
  it('none wins over any co-listed keyword (grammar is exclusive)', () => {
    expect(parseContain(['LAYOUT', 'NONE'])).toBe('none');
  });
  it('unknown keywords are dropped, not smuggled into the declaration', () => {
    expect(parseContain(['LAYOUT', 'BOGUS'])).toBe('layout');
  });
  it('empty / non-list payloads emit nothing', () => {
    expect(parseContain([])).toBeUndefined();
    expect(parseContain(null)).toBeUndefined();
    expect(parseContain(undefined)).toBeUndefined();
    expect(parseContain(42)).toBeUndefined();
    expect(css([])).toEqual({});
  });
  it('last write wins across repeated Contain leaves', () => {
    expect(extractContain([
      { type: 'Contain', data: ['LAYOUT'] },
      { type: 'Contain', data: ['PAINT'] },
    ])).toEqual({ value: 'paint' });
  });
});

describe('Contain — reaches CSS through the phase-10 dispatch', () => {
  it('array payload survives applyPerformancePhase10', () => {
    expect(applyPerformancePhase10([{ type: 'Contain', data: ['LAYOUT', 'PAINT'] }]))
      .toEqual({ contain: 'layout paint' });
  });
});
