// @vitest-environment jsdom
//
// KeyframeRules.test — JSDOM end-to-end for the ENGINE-built @keyframes
// path (schema/spec/07-animations.md §1.2): IRDecode passes the additive
// v2 `keyframes` key through, RuleBuilder serializes each stop through
// the SAME buildStyles engine as base properties, useDynamicRules mounts
// everything into ONE managed <style>, and the renderer's inline
// `animation-name` declaration binds to the mounted rule by ident.
//
// Division of labor after wave 8: the engine owns rule TEXT (see
// runtimes/web/tests/core/RuleBuilder.test.ts for the exact-text pins);
// the harness owns the document and the mount — THIS suite pins that the
// mounted CSSOM actually carries CSSKeyframesRule objects and that the
// binding ident is byte-equal end-to-end.

import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { ComposedNode } from '../../src/sdui/Composer';
import { decodeIRDocument } from '@style-converter/web/core/ir/IRDecode';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';
import {
  buildRuleList,
  mountRules,
  MANAGED_STYLE_ID,
} from '@style-converter/web/core/renderer/RuleBuilder';

// Leaf ComposedNode factory — the renderer's input shape.
const node = (component: IRComponent): ComposedNode => ({ component, children: [] });

// A motion-fade-alike RAW v2 wire document: one component referencing a
// document-level keyframes set — the byte shapes the converter emits
// (schema/conformance/fixtures/v2/keyframes.json).
const rawDoc = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [{
    id: 'mk-fade',
    name: 'MK_Fade',
    properties: [
      { type: 'BackgroundColor', data: { srgb: { r: 0.2, g: 0.6, b: 0.86 } } },
      { type: 'AnimationName', data: [{ type: 'identifier', name: 'motion-fade' }] },
      { type: 'AnimationDuration', data: {
        type: 'app.irmodels.properties.animations.AnimationDurationProperty.AnimationDurationValue.Durations',
        durations: [{ ms: 1000, original: { v: 1, u: 'S' } }],
      } },
    ],
  }],
  keyframes: {
    'motion-fade': [
      { offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0, original: { type: 'number', value: 0 } } }] },
      { offset: 1, properties: [{ type: 'Opacity', data: { alpha: 1, original: { type: 'number', value: 1 } } }] },
    ],
  },
};

// Reset the managed <style> element between tests — mountRules is
// idempotent per document, and tests must not leak rules into each other.
afterEach(() => { document.getElementById(MANAGED_STYLE_ID)?.remove(); document.body.innerHTML = ''; });

describe('decoded keyframes → mounted CSSKeyframesRule', () => {
  it('mounts the @keyframes rule in the SAME managed stylesheet as bucket rules', () => {
    const doc = decodeIRDocument(rawDoc);
    // The exact App.tsx path: useDynamicRules memoizes this call and
    // hands the list to mountRules inside useInsertionEffect.
    mountRules(buildRuleList(doc.components, doc.keyframes), document);
    const sheet = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement).sheet!;
    expect(sheet.cssRules).toHaveLength(1); // the keyframes rule (no buckets here)
    const rule = sheet.cssRules[0] as CSSKeyframesRule;
    // A real CSSOM keyframes rule — insertRule parsed it, nothing was
    // silently skipped by mountRules' catch.
    expect(rule.constructor.name).toBe('CSSKeyframesRule');
    expect(rule.name).toBe('motion-fade');
    expect(rule.cssText).toContain('0% { opacity: 0; }');
    expect(rule.cssText).toContain('100% { opacity: 1; }');
  });

  it('the rendered component binds by ident: inline animation-name === rule name', () => {
    const doc = decodeIRDocument(rawDoc);
    document.body.innerHTML = renderToStaticMarkup(<ComponentRenderer node={node(doc.components[0])} />);
    mountRules(buildRuleList(doc.components, doc.keyframes), document);
    const el = document.querySelector('[data-component-id="mk-fade"]') as HTMLElement;
    // The engine's AnimationName applier emitted the authored ident inline…
    expect(el.style.animationName).toBe('motion-fade');
    expect(el.style.animationDuration).toBe('1s');
    // …and the mounted rule header carries the SAME ident — the binding
    // the browser resolves when it starts the CSSAnimation.
    const rule = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement).sheet!.cssRules[0] as CSSKeyframesRule;
    expect(rule.name).toBe(el.style.animationName);
  });

  it('keyframe-free documents mount nothing (327-pair baseline footprint)', () => {
    const doc = decodeIRDocument({
      irVersion: 2, minReaderVersion: 2,
      components: [{ id: 'static', name: 'Static', properties: [] }],
    });
    mountRules(buildRuleList(doc.components, doc.keyframes), document);
    expect(document.getElementById(MANAGED_STYLE_ID)).toBeNull(); // zero DOM footprint
  });

  it('a dangling animation-name renders base styles untouched (defined no-op)', () => {
    const doc = decodeIRDocument({
      irVersion: 2, minReaderVersion: 2,
      components: [{
        id: 'ghost-box', name: 'GhostBox',
        properties: [
          { type: 'BackgroundColor', data: { srgb: { r: 0, g: 0, b: 0 } } },
          { type: 'AnimationName', data: [{ type: 'identifier', name: 'no-such-set' }] },
        ],
      }],
      // keyframes present but NOT defining the referenced name.
      keyframes: { other: [{ offset: 0, properties: [{ type: 'Opacity', data: { alpha: 1 } }] }] },
    });
    document.body.innerHTML = renderToStaticMarkup(<ComponentRenderer node={node(doc.components[0])} />);
    mountRules(buildRuleList(doc.components, doc.keyframes), document);
    const el = document.querySelector('[data-component-id="ghost-box"]') as HTMLElement;
    // Base styles intact, the reference forwarded verbatim (the browser
    // resolves the miss to "nothing animates" — spec 07 §1.3), and the
    // unrelated set still mounted.
    expect(el.style.backgroundColor).toBe('rgb(0, 0, 0)');
    expect(el.style.animationName).toBe('no-such-set');
    const sheet = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement).sheet!;
    expect((sheet.cssRules[0] as CSSKeyframesRule).name).toBe('other');
  });
});
