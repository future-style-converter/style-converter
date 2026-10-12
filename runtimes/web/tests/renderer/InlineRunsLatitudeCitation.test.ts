// InlineRunsLatitudeCitation.test.ts — wave-55 lane L7 (unit U4, BACKLOG 0(l)): the web word join cites the rule it
// departs from, and that rule's exception is written in the spec.
//
// WHY. The wave-54 W1 join (InertOutOfFlowWordJoin.ts) paints a paint-inert out-of-flow `runs` member at the end of the
// word it splits instead of at its `runs` position. That departs from spec 03 §4.1 RULE 2 ("a reader that honours
// `runs` paints the entries in order"), not rule 1 ("Order is the payload" — the wire, which keeps the member where it
// was; skeptic N5). The four web sites cited rule 1, and the spec had no paragraph granting the latitude. U4 adds the
// unnumbered "Renderer latitude" paragraph after rule 7 — unnumbered because the tree cites rules 3-6 by number
// (NodeRenderer.ts rule 4, IRDocumentDecoder.kt rule 5, extract-fixture.mjs rule 6) — naming both counters that make
// every use visible: web RunsPlacement.joinedOutOfFlowMembers and Compose InlineRunFold.Folded.droppedOutOfFlowMembers.
//
// Input VERBATIM from tools/titan/runs/wave54-final/sections/css-text/per-test-ir/
// wpt__css-text__hyphens__hyphens-out-of-flow-002.json (sha256 0f1d9251…, byte-identical at wave55-open): box 4's host
// (`high<span abspos transparent>abspos</span>way`, `hyphens: auto`) and its member.
//
// EXECUTED MUTATIONS (red → restore → green, sha256 in tools/titan/results/wave55-instruments/_note.md):
//   U4-a  the warn string back to "… rule 1"                              → (a) red
//   U4-b1 the latitude paragraph inserted as a NUMBERED rule between 2 and 3 → (b) red
//   U4-b2 a counter renamed in the spec (droppedOutOfFlowMembers → droppedMembers) → (b) red

import { describe, it, expect, vi, afterEach } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveRuns } from '../../src/renderer/InlineRuns';
import { composeTree } from '../../src/renderer/Composer';
import { buildStyles } from '../../src/core/renderer/StyleBuilder';
import type { IRDocument } from '../../src/core/ir/IRModels';

/** hyphens-out-of-flow-002 box 4: the host and its one run member, verbatim. */
const BOX4 = [{"id":"wpt__css-text__hyphens__hyphens-out-of-flow-002__4-289","name":"wpt__css-text__hyphens__hyphens-out-of-flow-002__4","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"MarginTop","data":{"px":5}},{"type":"MarginRight","data":{"px":5}},{"type":"MarginBottom","data":{"px":5}},{"type":"MarginLeft","data":{"px":5}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}},{"type":"Hyphens","data":"AUTO"}],"text":"highway","meta":{"role":"ws-after","lang":"en","runs":[{"text":"high"},{"child":"hyphens__hyphens-out-of-flow-002__4__0"},{"text":"way"}]}},
{"id":"hyphens__hyphens-out-of-flow-002__4__0-290","name":"hyphens__hyphens-out-of-flow-002__4__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-out-of-flow-002__4-289"},"text":"abspos","meta":{"sourceTag":"span","lang":"en"}}];

/** The spec, read from the repo (no tool parses it; this pin is its only executable reader). */
const SPEC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../../schema/spec/03-children.md');
/** §4.1 alone: from its heading to the next `## ` heading (§5). */
function section41(): string {
  const md = fs.readFileSync(SPEC, 'utf8');
  const start = md.indexOf('### 4.1 Inline runs');
  // The heading must exist, or every read below would be vacuous.
  expect(start).toBeGreaterThan(-1);
  return md.slice(start, md.indexOf('\n## ', start));
}
// The join's breadcrumb is a console.warn: spied per test, restored after.
afterEach(() => { vi.restoreAllMocks(); });

describe('spec 03 §4.1 renderer latitude — the citation and the paragraph (wave-55 L7 U4)', () => {
  it('(a) the breadcrumb of a word-splitting inert member cites rule 2 (renderer latitude)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    // The production call shape: the host's OWN computed hyphens arms the join (NodeRenderer).
    const host = composeTree({ irVersion: 2, minReaderVersion: 2, components: BOX4 } as unknown as IRDocument)
      .find((n) => n.component.id.endsWith('__4-289'));
    expect(host).toBeDefined();
    const plan = resolveRuns(host!.component.meta?.runs, host!.children, host!.component.id,
      { hyphensAuto: buildStyles(host!.component.properties).hyphens === 'auto' });
    // The member really moved (otherwise there is no breadcrumb to read).
    expect(plan?.joinedOutOfFlowMembers).toBe(1);
    const crumbs = warn.mock.calls.map((c) => String(c[0])).filter((m) => m.startsWith('[InlineRuns]'));
    expect(crumbs).toHaveLength(1);
    // The rule it departs from is the PAINTING rule, under the paragraph that grants it.
    expect(crumbs[0].endsWith('spec 03 §4.1 rule 2 (renderer latitude)')).toBe(true);
  });

  it('(b) §4.1 keeps exactly seven numbered rules, and the unnumbered latitude paragraph names both counters', () => {
    const s = section41();
    // Rules are `N. **…**` items at the start of a line: 1-7, in order, nothing inserted (tree citations stay true).
    const numbered = [...s.matchAll(/^(\d+)\. \*\*/gm)].map((m) => Number(m[1]));
    expect(numbered).toEqual([1, 2, 3, 4, 5, 6, 7]);
    // The paragraph exists once, after rule 7, as a bold lead-in (not a list item).
    const at = s.indexOf('**Renderer latitude');
    expect(at).toBeGreaterThan(s.indexOf('\n7. **'));
    expect(s.indexOf('**Renderer latitude', at + 1)).toBe(-1);
    const para = s.slice(at);
    // It is an exception to rule 2 that changes no wire byte …
    expect(para).toMatch(/reader-side exception to rule 2 \(no wire\s+change\)/);
    // … and every use of it is counted on both runtimes that take it.
    expect(para).toContain('RunsPlacement.joinedOutOfFlowMembers');
    expect(para).toContain('InlineRunFold.Folded.droppedOutOfFlowMembers');
  });
});
