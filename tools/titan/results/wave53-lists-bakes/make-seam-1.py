#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/make-seam-1.py — writes seam-1.patch (PLAN §3, L1's only seam hunk set).
#
# Applies the wave-53 nested-list implied-close change to a COPY of tools/titan/extract-fixture.mjs (the seam
# file is never edited by a lane) and prints the copy's path; the caller diffs it against HEAD to cut the patch.
# The edit is the measured draft tools/titan/results/wave53-plan/nested-list-extractor.patch-extractor.py, with the
# helper's banner extended (KNOWN GAP + identity argument) and both call-site comments re-trued.
# Usage: make-seam-1.py <copy-of-extract-fixture.mjs>
import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()

HELPER = r'''
// ── wave-53 lane L1 (nested-list-extractor A): the `li` implied-close SCOPE ──
//
// HTML Living Standard §13.2.6.4.7 ("in body", a start tag whose tag name is
// "li"): the implied close walks the stack of open elements from the current
// node and STOPS at a special element that is not address/div/p. A nested
// `<ol>`/`<ul>`/`<menu>`/`<dir>` opened AFTER our `<li>` is such a special
// element, so an `<li>` opener INSIDE it closes the INNER item and never ours.
// The two trigger sites below (walkChildren and scanOwnText) used to take the
// FIRST trigger opener after the open tag; for `<li>Two <ol><li>Eleven…` that
// is `<li>Eleven`, which hoisted the nested items one level up and let the
// nested `</ol>` end the fragment (dropping every later sibling — MEASURED on
// css-lists/counter-reset-reversed-nested: a flat list, `1. One` missing).
//
// Identity by construction: when no scope boundary OPENS before the first
// trigger opener, the loop returns exactly that first match, i.e. the old
// answer. A boundary CLOSER at depth 0 (the parent list's own `</ol>` can
// never be in this fragment; a stray one is ignored) changes nothing either.
//
// KNOWN GAP, named not hidden: the rest of the special category (blockquote,
// table, fieldset, section, …) does not bound the scope here, so
// `<li><blockquote><li>` keeps the old first-match. The wave-53 census
// (tools/titan/results/wave53-plan/nested-list-extractor.census.mjs, all 1435
// corpus tests) found 0 carriers of that shape; `dt`/`dd` likewise keep the
// first-match (0 carriers).
const IMPLIED_CLOSE_SCOPE = { li: ['ol', 'ul', 'menu', 'dir'] };

/** The index of the first auto-close trigger opener for a `tagName` element
 *  whose open tag ends at `from - 1`, skipping triggers nested inside a scope
 *  boundary (IMPLIED_CLOSE_SCOPE) that opened after it; -1 when none. Shared
 *  by walkChildren and scanOwnText so the two scans can never pick different
 *  trigger positions (the wave-50 B4 agreement). Exported for the pins in
 *  tools/titan/extract-fixture-implied-close.test.mjs. */
export function findImpliedClose(html, from, tagName, triggers) {
  // The boundary tags for this element (none for every tag but `li`).
  const boundary = IMPLIED_CLOSE_SCOPE[tagName] ?? [];
  // One combined opener/closer regex over triggers ∪ boundaries, walked once.
  const names = [...new Set([...triggers, ...boundary])];
  const re = new RegExp(`<(/?)(${names.join('|')})\\b`, 'gi');
  re.lastIndex = from;
  // Nesting depth of scope boundaries opened after our element.
  let depth = 0;
  let m;
  while ((m = re.exec(html)) !== null) {
    const tag = m[2].toLowerCase();
    // A boundary opener nests one level; its closer un-nests (never below 0).
    if (boundary.includes(tag)) {
      if (!m[1]) depth++;
      else if (depth > 0) depth--;
      continue;
    }
    // Openers only: a closer explicitly ends whatever is open, it is not an
    // implicit-close trigger. Only a depth-0 opener closes OUR element.
    if (!m[1] && depth === 0 && triggers.has(tag)) return m.index;
  }
  return -1;
}
'''

anchor = 'const AUTO_CLOSE_TRIGGERS = {'
assert s.count(anchor) == 1
s = s.replace(anchor, HELPER.lstrip('\n') + '\n' + anchor)

old1 = '''      if (triggers) {
        // Build one combined regex of `<(triggerA|triggerB|…)\\b` so we walk
        // the haystack once. We deliberately match opener tags only — a
        // closer like `</td>` doesn't implicitly close a sibling `<td>`,
        // it explicitly closes whatever was open.
        const trigRe = new RegExp(
          `<(?:${[...triggers].join('|')})\\\\b`, 'gi',
        );
        trigRe.lastIndex = tagOpenEnd + 1;
        const t = trigRe.exec(html);
        if (t) implicitClose = t.index;
      }'''
new1 = '''      if (triggers) {
        // One combined `<(triggerA|triggerB|…)\\b` walk, openers only — a
        // closer like `</td>` doesn't implicitly close a sibling `<td>`, it
        // explicitly closes whatever was open. Wave-53 lane L1: shared with
        // scanOwnText via findImpliedClose, which also skips an `<li>`
        // opener nested inside a list opened after this one (HTML
        // §13.2.6.4.7 — the implied close stops at that special element).
        implicitClose = findImpliedClose(html, tagOpenEnd + 1, tagName, triggers);
      }'''
assert s.count(old1) == 1, s.count(old1)
s = s.replace(old1, new1)

old2 = '''    if (autoCloseTriggers) {
      // One combined opener regex, walked once — the same construction
      // walkChildren uses, so the two scans can never pick different
      // trigger positions. Openers only: a closer explicitly ends whatever
      // is open and is not an implicit-close trigger.
      const trigRe = new RegExp(`<(?:${[...autoCloseTriggers].join('|')})\\\\b`, 'gi');
      trigRe.lastIndex = tagOpenEnd + 1;
      const t = trigRe.exec(innerHtml);
      if (t) implicitCloseAt = t.index;
    }'''
new2 = '''    if (autoCloseTriggers) {
      // The SAME helper walkChildren calls (wave-53 lane L1), so the two
      // scans can never pick different trigger positions — including the
      // nested-list scope (HTML §13.2.6.4.7) that keeps `<li>A<ol><li>B`
      // from ending `A`'s item at the inner `<li>`.
      implicitCloseAt = findImpliedClose(innerHtml, tagOpenEnd + 1, tagName, autoCloseTriggers);
    }'''
assert s.count(old2) == 1, s.count(old2)
s = s.replace(old2, new2)
open(p, 'w', encoding='utf-8').write(s)
print(p)
