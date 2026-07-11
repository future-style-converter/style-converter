/**
 * useDynamicRules — the harness-side MOUNT of the RuleBuilder stylesheet
 * (spec 06; the "harness composer applies the class + mounts the
 * stylesheet" half of the contract — ComponentRenderer.tsx applies the
 * classes).
 *
 * Called once from App.tsx so every render mode (gallery, capture,
 * fixture) gets the same document stylesheet: selector-bucket rules,
 * media-bucket rules, the root `color-scheme` opt-in for light-dark()
 * values, and — wave 8 (spec 07 §1.2) — the document's `@keyframes`
 * rules built by the ENGINE from `IRDocument.keyframes` (the engine's
 * animation-* appliers emit the per-component `animation-name: <ident>`
 * declarations that bind to them). Bucket-free, keyframe-free documents
 * build an empty rule list and mount nothing — the 327-pair baseline
 * DOM is untouched.
 */

import React from 'react';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';
import { buildRuleList, mountRules } from '@style-converter/web/core/renderer/RuleBuilder';

/**
 * Build + mount the dynamic-styling rules for a (possibly not-yet-
 * loaded) IR document. Idempotent across re-renders: the rule list is
 * memoised on document identity, and mountRules itself no-ops when the
 * joined rule text is unchanged (hot-reload swaps rebuild correctly).
 */
export function useDynamicRules(document: IRDocument | null): void {
  // Rule list derivation is pure — memoise on the decoded document.
  // keyframes ride the SAME decoded document (IRDecode passes the
  // additive v2 key through), so one memo covers both rule families.
  const rules = React.useMemo(
    () => (document ? buildRuleList(document.components, document.keyframes) : []),
    [document],
  );
  // useInsertionEffect is React's designated style-injection slot: it
  // fires before layout effects read the DOM, and is skipped entirely
  // during server rendering — which is exactly mountRules's SSR no-op
  // contract (string export via buildStylesheet covers SSR).
  React.useInsertionEffect(() => {
    mountRules(rules);                                               // idempotent managed <style>
  }, [rules]);
}
