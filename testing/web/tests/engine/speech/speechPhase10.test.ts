// speechPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applySpeechPhase10 } from '../../../src/style/engine/speech/_dispatch';

describe('applySpeechPhase10', () => {
  it('empty input → empty output', () => {
    expect(applySpeechPhase10([])).toEqual({});
  });
  it('Speak → never', () => {
    expect(applySpeechPhase10([{ type: 'Speak', data: 'NEVER' }]))
      .toEqual({ speak: 'never' });
  });
  it('SpeakHeader → once', () => {
    expect(applySpeechPhase10([{ type: 'SpeakHeader', data: 'ONCE' }]))
      .toEqual({ speakHeader: 'once' });
  });
  it('SpeakNumeral → digits', () => {
    expect(applySpeechPhase10([{ type: 'SpeakNumeral', data: 'DIGITS' }]))
      .toEqual({ speakNumeral: 'digits' });
  });
  it('SpeakPunctuation → code', () => {
    expect(applySpeechPhase10([{ type: 'SpeakPunctuation', data: 'CODE' }]))
      .toEqual({ speakPunctuation: 'code' });
  });
});
