// expected-captures.mjs — host-side mirror of the device flatten rules.
//
// All three device harnesses flatten the component tree WITH SUPPRESSION:
// a child under a context-creating parent is rendered inside the parent's
// capture only (a standalone render would lack the parent's clip/blend/
// transform/opacity context and trip false divergence), and a child that
// depends on its backdrop (backdrop-filter; non-normal mix-blend-mode) is
// suppressed standalone for the same reason. The rules live, kept
// IDENTICAL, in Android ScreenshotCaptureScreen.kt (parentCreatesContext +
// dependsOnBackdrop + flattenComponents), web CaptureGallery.tsx and iOS
// ScreenshotCaptureView.swift.
//
// test-all.sh's expected count, meanwhile, was `grep -c '"id"'` over the
// wire — EVERY component, suppressed or not. On any nested fixture the
// poll therefore waited for captures the devices will never produce and
// only finished through the stuck-counter branch ("32 / 42 captured…" on
// composition-test, every run, plus the 10s stall penalty and a scary
// "app may have crashed" warning for a healthy run). Same defect in the
// TITAN feeder's expected-name computation.
//
// This module is that count done honestly: same walk, same rules, over
// the v2 flat wire (children linked by slot.parent). If a rule here
// drifts from the device rule the count mismatches IMMEDIATELY on the
// next nested-fixture run — loud, where the grep was silently wrong.

import { readFileSync } from 'node:fs';

/** Mirror of tryStringValue: IR data is a bare string or {value: string}. */
function stringValue(data) {
  if (typeof data === 'string') return data;
  if (data && typeof data === 'object' && typeof data.value === 'string') return data.value;
  return null;
}

/** Mirror of tryOpacityValue: raw number, {value:n}, or {alpha:n,…}. */
function opacityValue(data) {
  if (typeof data === 'number') return data;
  if (data && typeof data === 'object') {
    if (typeof data.value === 'number') return data.value;
    if (typeof data.alpha === 'number') return data.alpha;
  }
  return null;
}

/** Mirror of parentCreatesContext (ScreenshotCaptureScreen.kt). */
export function parentCreatesContext(component) {
  for (const p of component.properties ?? []) {
    switch (p.type) {
      case 'ClipPath': case 'Mask': case 'MaskImage':
      case 'Filter': case 'BackdropFilter':
      case 'Rotate': case 'Scale': case 'Translate':
        return true;
      case 'Overflow': case 'OverflowX': case 'OverflowY': {
        const v = stringValue(p.data)?.toLowerCase();
        if (v === 'clip' || v === 'hidden') return true;
        break;
      }
      case 'MixBlendMode': {
        const v = stringValue(p.data)?.toLowerCase();
        if (v && v !== 'normal') return true;
        break;
      }
      case 'Transform': {
        if (Array.isArray(p.data) && p.data.length > 0) return true;
        break;
      }
      case 'Opacity': {
        const v = opacityValue(p.data);
        if (v !== null && v < 1.0) return true;
        break;
      }
      default: break;
    }
  }
  return false;
}

/** Mirror of dependsOnBackdrop (ScreenshotCaptureScreen.kt). */
export function dependsOnBackdrop(component) {
  for (const p of component.properties ?? []) {
    if (p.type === 'BackdropFilter') return true;
    if (p.type === 'MixBlendMode') {
      const v = stringValue(p.data)?.toLowerCase();
      if (v && v !== 'normal') return true;
    }
  }
  return false;
}

/**
 * The number of per-component captures the devices will produce for a v2
 * wire document. Same walk as flattenComponents: roots in order, descend
 * unless the parent creates a context, skip backdrop-dependent children.
 */
export function expectedCaptureCount(wire) {
  const components = wire?.components ?? [];
  const childrenOf = new Map();
  for (const c of components) {
    const parent = c.slot?.parent;
    if (parent) {
      if (!childrenOf.has(parent)) childrenOf.set(parent, []);
      childrenOf.get(parent).push(c);
    }
  }
  let count = 0;
  const walk = (c) => {
    count++;
    const kids = childrenOf.get(c.id) ?? [];
    if (kids.length === 0) return;
    if (parentCreatesContext(c)) return;
    for (const k of kids) if (!dependsOnBackdrop(k)) walk(k);
  };
  for (const c of components) if (!c.slot?.parent) walk(c);
  return count;
}

// CLI: node expected-captures.mjs <tmpOutput.json> → prints the count.
if (import.meta.url === `file://${process.argv[1]}`) {
  const file = process.argv[2];
  if (!file) { console.error('usage: expected-captures.mjs <tmpOutput.json>'); process.exit(2); }
  console.log(expectedCaptureCount(JSON.parse(readFileSync(file, 'utf8'))));
}
