// TextOverflow.test.ts — pins for the text-overflow extractor, added with
// the CodeQL js/double-escaping fix: the CustomString branch once ran the
// backslash replace TWICE (a leftover from the CodeQL-baseline sed round),
// turning `\` into `\\\\` inside the emitted CSS string literal. These
// tests pin the CORRECT single-pass escaping so the bug cannot return.
import { describe, it, expect } from 'vitest';

import { extractTextOverflow } from '../../src/engine/typography/TextOverflowExtractor';

// Helper: one TextOverflow IR property wrapping the given parser payload.
const prop = (data: unknown) => [{ type: 'TextOverflow', data }];

describe('extractTextOverflow', () => {
  it('maps the keyword payloads to their CSS keywords', () => {
    // Bare enum strings (v1 wire) and tagged objects (v2 wire) both land
    // on the lowercase CSS keyword — mirrors TextOverflowPropertyParser.
    expect(extractTextOverflow(prop('ELLIPSIS')).value).toBe('ellipsis');
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.Clip' })).value).toBe('clip');
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.Ellipsis' })).value).toBe('ellipsis');
  });

  it('emits fade() with and without a length argument', () => {
    // fade(<length>) keeps the pre-serialized length text verbatim.
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.Fade', length: '1em' })).value).toBe('fade(1em)');
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.Fade' })).value).toBe('fade');
  });

  it('escapes CustomString for CSS string-literal output — each char exactly once', () => {
    // Plain text passes through inside double quotes.
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.CustomString', value: '…more' })).value)
      .toBe('"…more"');
    // A double quote in the value must arrive as \" (one backslash).
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.CustomString', value: 'say "hi"' })).value)
      .toBe('"say \\"hi\\""');
    // THE REGRESSION PIN: one input backslash → exactly TWO output
    // backslashes (`\\`), never four — the double-escaping bug produced
    // `\\\\` here because the backslash replace ran twice.
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.CustomString', value: 'a\\b' })).value)
      .toBe('"a\\\\b"');
    // Backslash-before-quote — the exact ordering case the baseline
    // round fixed: escape backslashes FIRST, then quotes, so `\"`
    // becomes `\\\"` and not a broken `\\\\"` or unterminated literal.
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.CustomString', value: '\\"' })).value)
      .toBe('"\\\\\\""');
  });

  it('joins TwoValue as "<start> <end>" with clip defaults', () => {
    // Both sides normalise to lowercase keywords; missing sides → clip.
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.TwoValue', start: 'CLIP', end: 'ELLIPSIS' })).value)
      .toBe('clip ellipsis');
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.TwoValue', end: 'ELLIPSIS' })).value)
      .toBe('clip ellipsis');
  });

  it('drops malformed payloads and lets the last valid write win', () => {
    // Non-string CustomString value is dropped (undefined → no write) …
    expect(extractTextOverflow(prop({ type: 'TextOverflowValue.CustomString', value: 7 })).value)
      .toBeUndefined();
    // … and cascade order applies: the later property overwrites.
    expect(extractTextOverflow([
      { type: 'TextOverflow', data: 'CLIP' },
      { type: 'TextOverflow', data: 'ELLIPSIS' },
    ]).value).toBe('ellipsis');
  });
});
