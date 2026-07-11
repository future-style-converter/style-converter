/**
 * useDocumentRules — the package-owned stylesheet LIFECYCLE for a
 * rendered IR document (issue #41; rule semantics in spec 06 + 07 §1.2).
 *
 * Builds the document's rule list with the engine's RuleBuilder —
 * selector-bucket rules, media-bucket rules, the `color-scheme` root
 * opt-in for light-dark(), and the document `@keyframes` rules — and
 * mounts it into <head> via the managed `<style>` element, keeping it
 * in sync with document identity and REMOVING it when the consuming
 * component unmounts (a real app must not leak rules after it stops
 * rendering the document).
 *
 * HARNESS DIVERGENCE: the harness keeps its own useDynamicRules with NO
 * unmount cleanup — the capture page owns its document for the whole
 * browser session, and tearing rules down mid-capture would only add a
 * failure mode; a package consumer embedding DocumentRenderer inside a
 * larger app needs the cleanup.
 *
 * Single-document contract: mountRules owns ONE managed element per
 * Document (MANAGED_STYLE_ID), so render at most one IR document per
 * page/Document at a time — same contract the engine mount always had.
 */

import { useInsertionEffect, useMemo } from 'react';
import type { IRDocument } from '../core/ir/IRModels';
import { buildRuleList, mountRules, MANAGED_STYLE_ID } from '../core/renderer/RuleBuilder';

/**
 * Mount + maintain the document stylesheet. SSR-safe: rule derivation is
 * pure, and insertion effects never run during server rendering — use
 * `buildStylesheet(doc.components, doc.keyframes)` for the SSR string.
 *
 * @param doc       decoded IR document (null while loading — mounts nothing).
 * @param targetDoc optional explicit Document (iframes/tests); defaults
 *                  to the global one inside mountRules.
 */
export function useDocumentRules(doc: IRDocument | null, targetDoc?: Document): void {
  // Rule derivation is pure — memoise on document identity. keyframes
  // ride the same decoded document, so one memo covers both families.
  const rules = useMemo(
    () => (doc ? buildRuleList(doc.components, doc.keyframes) : []),
    [doc],
  );
  // useInsertionEffect is React's designated style-injection slot: it
  // fires before layout effects read the DOM and is skipped during SSR.
  useInsertionEffect(() => {
    // Idempotent managed <style> mount (mountRules no-ops on identical
    // rule text, so re-renders with a stable document are free).
    mountRules(rules, targetDoc);
    // Lifecycle: on unmount (or before re-mounting for a NEW document)
    // remove the managed element entirely so no stale rules survive the
    // renderer. Resolve the Document the same way mountRules does.
    return () => {
      const host = targetDoc ?? (typeof document !== 'undefined' ? document : undefined);
      host?.getElementById(MANAGED_STYLE_ID)?.remove();
    };
  }, [rules, targetDoc]);
}
