// Issue38Claims.test.ts — pins for the registry-claims audit (issue #38).
// Every property here USED to be a coverage-only claim in PropertyRegistry:
// membership in migratedProperties suppressed the legacy fallback while no
// engine applier existed, so the value silently vanished. Each now has a
// real Config/Extractor/Applier triplet; these tests drive buildStyles
// end-to-end so the dispatch wiring is covered too. Wire shapes verified
// against actual converter output (single-field data classes flatten:
// data is the bare enum string / array / track-size object).
import { describe, it, expect } from 'vitest';
import { buildStyles } from '../../src/core/renderer/StyleBuilder';

const p = (type: string, data: unknown) => ({ type, data });
// Widened keys (not in csstype) come back as plain string entries.
const s = (props: Array<{ type: string; data: unknown }>) =>
  buildStyles(props) as Record<string, unknown>;

describe('BoxSizing — real one-key emitter', () => {
  it('BORDER_BOX → border-box', () => {
    expect(s([p('BoxSizing', 'BORDER_BOX')]).boxSizing).toBe('border-box');
  });
  it('CONTENT_BOX → content-box', () => {
    expect(s([p('BoxSizing', 'CONTENT_BOX')]).boxSizing).toBe('content-box');
  });
});

describe('ColorScheme — real one-key emitter', () => {
  it('joins the scheme list in declaration order', () => {
    expect(s([p('ColorScheme', ['LIGHT', 'DARK'])]).colorScheme).toBe('light dark');
    expect(s([p('ColorScheme', ['DARK', 'ONLY'])]).colorScheme).toBe('dark only');
  });
  it('normal passes through', () => {
    expect(s([p('ColorScheme', ['NORMAL'])]).colorScheme).toBe('normal');
  });
});

describe('DynamicRangeLimit — real (widened) emitter', () => {
  it('kebab-cases the enum token', () => {
    expect(s([p('DynamicRangeLimit', 'CONSTRAINED_HIGH')]).dynamicRangeLimit)
      .toBe('constrained-high');
    expect(s([p('DynamicRangeLimit', 'STANDARD')]).dynamicRangeLimit).toBe('standard');
  });
});

describe('MarkerSide — real (widened) emitter', () => {
  it('kebab-cases the enum token', () => {
    expect(s([p('MarkerSide', 'LEFT_RIGHT')]).markerSide).toBe('left-right');
    expect(s([p('MarkerSide', 'MATCH')]).markerSide).toBe('match');
  });
});

describe('ScrollStartTarget axis variants — real (widened) emitters', () => {
  it('block / inline / x / y all emit their own key', () => {
    expect(s([p('ScrollStartTargetBlock', 'AUTO')]).scrollStartTargetBlock).toBe('auto');
    expect(s([p('ScrollStartTargetInline', 'NONE')]).scrollStartTargetInline).toBe('none');
    expect(s([p('ScrollStartTargetX', 'NONE')]).scrollStartTargetX).toBe('none');
    expect(s([p('ScrollStartTargetY', 'AUTO')]).scrollStartTargetY).toBe('auto');
  });
});

describe('BoxOrient — real -webkit-box-orient emitter', () => {
  it('kebab-cases onto the WebkitBoxOrient key', () => {
    expect(s([p('BoxOrient', 'INLINE_AXIS')]).WebkitBoxOrient).toBe('inline-axis');
    expect(s([p('BoxOrient', 'VERTICAL')]).WebkitBoxOrient).toBe('vertical');
  });
});

describe('GridAutoTrack — real (widened) emitter, GridExtractor untouched', () => {
  it('renders minmax with a px min and an fr max (sealed-class wire shape)', () => {
    const styles = s([p('GridAutoTrack', {
      type: 'minmax',
      min: { type: 'length', px: 10.0 },
      max: { type: 'length', original: { v: 1.0, u: 'FR' } },
    })]);
    expect(styles.gridAutoTrack).toBe('minmax(10px, 1fr)');
  });
  it('renders the auto object form', () => {
    expect(s([p('GridAutoTrack', { type: 'auto' })]).gridAutoTrack).toBe('auto');
  });
});
