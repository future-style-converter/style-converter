// PropertyTracker.test.ts — pins the log-once unknown/unhandled contract
// (CLAUDE.md: "No silent fallthroughs … log it via the PropertyTracker").
// Web mirrors the Compose runtime's PropertyTracker object semantics, with
// one web-specific twist under test here: warnings fire ONCE per
// (type, context) so the React render loop can't flood the console.
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import {
  markHandled, markUnhandled, logUnhandled,
  getReport, isHandled, isUnhandled, getOccurrences, reset,
} from '../../src/engine/PropertyTracker';

beforeEach(() => reset());        // isolate every test from tracker state
afterEach(() => vi.restoreAllMocks());

describe('PropertyTracker — set semantics (Compose parity)', () => {
  it('handled wins over unhandled, in either order', () => {
    markUnhandled('Foo');
    markHandled('Foo');           // later success clears the failure flag
    expect(isHandled('Foo')).toBe(true);
    expect(isUnhandled('Foo')).toBe(false);
    markUnhandled('Foo');         // a later failure does NOT shadow success
    expect(isUnhandled('Foo')).toBe(false);
  });
  it('counts occurrences across both paths', () => {
    markHandled('Bar');
    markUnhandled('Bar');
    expect(getOccurrences('Bar')).toBe(2);
  });
  it('reports coverage, sorted lists and top-unhandled', () => {
    markHandled('B'); markHandled('A'); markUnhandled('Z'); markUnhandled('Z');
    const r = getReport();
    expect(r.handled).toEqual(['A', 'B']);
    expect(r.unhandled).toEqual(['Z']);
    expect(r.coverage).toBeCloseTo(2 / 3);
    expect(r.totalOccurrences).toBe(4);
    expect(r.topUnhandled).toEqual([['Z', 2]]);
  });
  it('empty tracker reports full coverage', () => {
    expect(getReport().coverage).toBe(1);
  });
});

describe('PropertyTracker — log-once warnings', () => {
  it('warns exactly once per type, but keeps counting occurrences', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    logUnhandled('Mystery');
    logUnhandled('Mystery');
    logUnhandled('Mystery');
    expect(warn).toHaveBeenCalledTimes(1);
    expect(getOccurrences('Mystery')).toBe(3);
  });
  it('distinct contexts warn separately (still once each)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    logUnhandled('Generic', 'border-top-left-radius');
    logUnhandled('Generic', 'border-top-left-radius');
    logUnhandled('Generic', 'clip-rule');
    expect(warn).toHaveBeenCalledTimes(2);
  });
  it('reset() re-arms the log-once guard', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    logUnhandled('Mystery');
    reset();
    logUnhandled('Mystery');
    expect(warn).toHaveBeenCalledTimes(2);
  });
});
