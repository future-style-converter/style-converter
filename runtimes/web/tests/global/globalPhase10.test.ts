// globalPhase10.test.ts — Phase-10 long-tail smoke tests + the wave-52
// (lane L11, brief colour-not-reaching-text F1) ORDER-AWARE `all` pins.
//
// The three property lists below are VERBATIM wave51-fix per-test IR
// (tools/titan/runs/wave51-fix/sections/…/per-test-ir/), so each pin speaks
// for the gate cell it names.
//
// MUTATION RECORD (executed 2026-10-05 by mutate-web.sh in
// tools/titan/results/wave52-all-reset-postload-colour/, each file restored
// byte-exact — sha256 before == after, mutations.log): see that log for the
// per-mutation failing rows (W1 `all` back at the tail of StyleBuilder,
// W2 the before-`all` drop removed, W3 the §3.1 exemption removed, W4 the
// FIRST `all` governing instead of the last).
import { describe, it, expect } from 'vitest';
import { applyGlobalPhase10, applyAllReset } from '../../src/engine/global/_dispatch';
import { buildStyles } from '../../src/core/renderer/StyleBuilder';
import { declarationsToCss } from '../../src/core/renderer/CssText';
import { applyTextColor } from '../../src/engine/color/ColorApplier';
import { extractTextColor } from '../../src/engine/color/ColorExtractor';
import type { IRProperty } from '../../src/core/ir/IRModels';

// VERBATIM: css-cascade/all-prop-initial-color, the span `…__0__0-015`.
const INITIAL_COLOR_SPAN = [{"type":"All","data":"INITIAL"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}] as IRProperty[];
// VERBATIM: css-cascade/all-prop-001, the `.test` div `…__1-002`.
const ALL_PROP_001_TEST = [{"type":"Direction","data":"RTL"},{"type":"UnicodeBidi","data":"BIDI_OVERRIDE"},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontVariantCaps","data":"SMALL_CAPS"},{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Float","data":"LEFT"},{"type":"LetterSpacing","data":{"px":0,"original":{"type":"length","original":{"v":1,"u":"EM"}}}},{"type":"Display","data":"LIST_ITEM"},{"type":"TextAlign","data":"CENTER"},{"type":"Width","data":{"type":"length","original":{"v":0.5,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":10,"u":"EM"}}},{"type":"OverflowX","data":"SCROLL"},{"type":"OverflowY","data":"SCROLL"},{"type":"All","data":"INITIAL"}] as IRProperty[];
// VERBATIM: css-display/display-contents-button, the button `…__2-036`.
const DISPLAY_CONTENTS_BUTTON = [{"type":"All","data":"INITIAL"},{"type":"FontKerning","data":"NONE"},{"type":"FontFeatureSettings","data":{"type":"features","features":[{"tag":"kern","value":0}]}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderRightWidth","data":{"px":10}},{"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderLeftWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Display","data":"CONTENTS"}] as IRProperty[];
// A plain red `color` declaration in the wire's srgb shape.
const RED = { type: 'Color', data: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' } } as IRProperty;

describe('applyGlobalPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyGlobalPhase10([])).toEqual({});
  });
  it('All → unset', () => {
    expect(applyGlobalPhase10([{ type: 'All', data: 'UNSET' }]))
      .toEqual({ all: 'unset' });
  });
});

describe('wave 52 — order-aware all (css-cascade-4 §6.4 / §3.1)', () => {
  it('all-prop-initial-color: `all` is the FIRST key and the green follows it', () => {
    const styles = buildStyles(INITIAL_COLOR_SPAN);
    // The browser applies keys in order: shorthand first, then the longhand.
    expect(Object.keys(styles)[0]).toBe('all');
    expect(styles.all).toBe('initial');
    // The span's own green — exactly what the colour applier emits for it.
    const green = applyTextColor(extractTextColor([INITIAL_COLOR_SPAN[1]]));
    expect(styles.color).toBe(green.color);
    // The stylesheet path serialises in the same order: `all` leads.
    expect(declarationsToCss(styles, false).trim().startsWith('all:')).toBe(true);
  });

  it('`color: red; all: initial` — the declaration BEFORE `all` is dropped', () => {
    const styles = buildStyles([RED, { type: 'All', data: 'INITIAL' } as IRProperty]);
    expect(Object.keys(styles)).toEqual(['all']);
  });

  it('all-prop-001: only direction + unicode-bidi survive the 30 earlier declarations', () => {
    const styles = buildStyles(ALL_PROP_001_TEST);
    expect(Object.keys(styles)[0]).toBe('all');
    // §3.1: the two exempt longhands ride along; every red declaration is gone.
    expect(styles.direction).toBe('rtl');
    expect(styles.unicodeBidi).toBeDefined();
    expect(styles.color).toBeUndefined();
    expect(styles.backgroundColor).toBeUndefined();
    expect(styles.display).toBeUndefined();
  });

  it('display-contents-button: everything after `all` is emitted after it', () => {
    const styles = buildStyles(DISPLAY_CONTENTS_BUTTON);
    const keys = Object.keys(styles);
    expect(keys[0]).toBe('all');
    // Its `display: contents` now follows the reset instead of being erased by it.
    expect(styles.display).toBe('contents');
    expect(keys.indexOf('display')).toBeGreaterThan(0);
  });

  it('the LAST `all` governs and splits the list', () => {
    const list = [
      { type: 'All', data: 'INITIAL' }, RED, { type: 'All', data: 'UNSET' }, { type: 'Direction', data: 'RTL' },
    ] as IRProperty[];
    // RED sits before the governing (second) `all` → dropped.
    expect(applyAllReset(list).map((p) => p.type)).toEqual(['Direction']);
    expect(buildStyles(list).all).toBe('unset');
  });

  it('All-free lists are returned as the same instance', () => {
    const list = [RED];
    expect(applyAllReset(list)).toBe(list);
    expect(Object.keys(buildStyles(list))[0]).not.toBe('all');
  });
});
