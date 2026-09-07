// speechPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applySpeechPhase10 } from '../../src/engine/speech/_dispatch';

describe('applySpeechPhase10', () => {
  it('empty input → empty output', () => {
    expect(applySpeechPhase10([])).toEqual({});
  });
  it('Speak → never', () => {
    expect(applySpeechPhase10([{ type: 'Speak', data: 'NEVER' }]))
      .toEqual({ speak: 'never' });
  });
  // speak-header / speak-numeral / speak-punctuation are CSS 2 aural/table
  // properties dropped from css-speech-1 with no IR class under
  // irmodels/properties/speech/, so those type strings can never reach this
  // dispatch; the three cases that synthesised them tested unreachable
  // triplets, now deleted (A6#9). Pin the ignore behaviour instead.
  it('IR types the converter cannot emit (speak-header/-numeral/-punctuation) are ignored', () => {
    expect(applySpeechPhase10([
      { type: 'SpeakHeader', data: 'ONCE' },
      { type: 'SpeakNumeral', data: 'DIGITS' },
      { type: 'SpeakPunctuation', data: 'CODE' },
    ])).toEqual({});
  });
});
