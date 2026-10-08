/**
 * InertOutOfFlowWordJoin — wave-54 lane L6 (unit W1): a PAINT-INERT
 * out-of-flow run member no longer splits an `auto`-hyphenated word.
 *
 * MEASURED (wave53-final css-text/hyphens/hyphens-out-of-flow-002 web
 * f 0.9411, unchanged on 13 runs since wave 35): boxes 4/5 are
 * `high<span abspos>abspos</span>way` under `hyphens: auto; width: 6ch`.
 * The runtime rendered the wire's `meta.runs` faithfully (text `high`, the
 * member at its slot, text `way`), and the host's hyphenator never saw a
 * word with the `high|way` point: both boxes became one 26-px line
 * `highway` overflowing the border, where the ref has `high‐` / `way`
 * (web-out-of-flow-hyphen-box.md §2-§4; CoreFoundation asked both ways
 * reproduces the capture box by box).
 *
 * THE CHANGE: under `hyphens: auto`, `[text P, child M, text N]` with M
 * paint-inert and the split MID-WORD becomes `[text P+N₁, child M, text N₂]`
 * — M moves to the END of the word (N₁ = N up to its first white space,
 * N₂ the rest; on the corpus N₂ is always empty). That is box 7's shape
 * (`highway` then the member), which renders right in the same capture.
 *
 * SPEC: css-text-3 §5.1 — out-of-flow elements introduce no soft wrap
 * opportunity (the test asserts "no effect on automatic hyphenation");
 * §5.4 — `auto` breaks at the language's hyphenation points; CSS 2.1
 * §10.3.7 / §10.6.4 — only the member's STATIC position moves, and the
 * predicate guarantees it paints nothing there or anywhere.
 *
 * STATED LOSSES (never silent: the caller counts every join as
 * RunsPlacement.joinedOutOfFlowMembers, the web twin of Compose's
 * Folded.droppedOutOfFlowMembers, which goes further and drops the member):
 *   - the member's accessible text and its rect follow the word's end, not
 *     its source slot (a reader deviation from spec 03 §4.1 rule 1);
 *   - only the host's OWN `hyphens` gates the join (`hyphens` inherits,
 *     css-text-3 §5.4; census: 0 reached members inherit it) — TODO thread
 *     the inherited value when a carrier needs it;
 *   - inherited paint the predicate cannot see (an ancestor's text-shadow
 *     or -webkit-text-stroke on transparent glyphs) — Compose shares the
 *     limit; 0 corpus members (census). TODO if one appears.
 */

import type { ComposedNode } from './Composer';
import type { ResolvedRun } from './InlineRuns';

/** CSS 2.1 §9.3.1: absolute and fixed take the box out of flow; relative/sticky stay in flow. */
const OUT_OF_FLOW_POSITIONS: ReadonlySet<string> = new Set(['ABSOLUTE', 'FIXED']);

/**
 * The member tags whose text carries no UA styling — Compose InlineRunFold
 * TEXT_MEMBER_TAGS, the ring its inert-drop arm checks BEFORE the predicate.
 * It is what keeps an abspos `<img>` / `<input>` (which paint whatever their
 * `color`) out: moving one of those would move real pixels.
 */
const TEXT_MEMBER_TAGS: ReadonlySet<string> = new Set(['span', 'time', 'data']);

/** CSS document white space (css-text-3 §4.1: spaces, tabs, segment breaks; CSS 2.1 adds form feed). */
const WHITE_SPACE = /[ \t\n\r\f]/;

/** A JSON-object value (Kotlin `as? JsonObject`): non-null, non-array object. */
function asObject(v: unknown): Record<string, unknown> | null {
  return v !== null && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

/** A JSON primitive's content (Kotlin `JsonPrimitive.contentOrNull`): null for JSON null / non-primitives. */
function primitiveContent(v: unknown): string | null {
  return typeof v === 'string' ? v : (typeof v === 'number' || typeof v === 'boolean') ? String(v) : null;
}

/** ValueExtractors.extractKeyword restated: a bare primitive, else `keyword` then `value`. */
function keywordOf(data: unknown): string | null {
  const o = asObject(data);
  // The wire's bare keyword string ("ABSOLUTE")…
  if (o === null) return primitiveContent(data);
  // …or the `{keyword}` / `{value}` object dialects.
  return primitiveContent(o.keyword) ?? primitiveContent(o.value);
}

/** InertOutOfFlowMember.alphaIsZero restated: `srgb.a` present, numeric and ≤ 0 (absent = opaque → refuse). */
function alphaIsZero(data: unknown): boolean {
  const srgb = asObject(asObject(data)?.srgb);
  // No sRGB object: unreadable colour → refuse, never guess.
  if (srgb === null) return false;
  // Kotlin `floatOrNull` reads a numeric primitive; a decimal string reads the same way, anything else refuses.
  const raw = srgb.a;
  // NaN (absent / non-numeric) compares false below: absent alpha is opaque (css-color-4 §4.1).
  const a = typeof raw === 'number' ? raw
    : (typeof raw === 'string' && /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(raw)) ? Number(raw) : NaN;
  return a <= 0;
}

/**
 * Byte-for-byte restatement of Compose `InertOutOfFlowMember.admits`
 * (runtimes/compose/…/typography/inline/InertOutOfFlowMember.kt:89): no
 * structure of its own, and EVERY property one of `Position` ABSOLUTE|FIXED
 * (required), `Color` with alpha 0 (required), `Hyphens` (tolerated).
 */
export function isInertOutOfFlowMember(member: ComposedNode): boolean {
  // The member's own wire component (properties + meta).
  const c = member.component;
  // Nested nodes, runs or decoration lines could paint: not a single inert box.
  if (member.children.length > 0 || (c.meta?.runs?.length ?? 0) > 0 || (c.meta?.decorations?.length ?? 0) > 0) return false;
  // The two REQUIRED facts, each proven by a declaration on the member itself.
  let outOfFlow = false;
  let transparentInk = false;
  for (const p of c.properties) {
    if (p.type === 'Position') {
      // Out of flow only for absolute/fixed; any other position refuses.
      if (OUT_OF_FLOW_POSITIONS.has(keywordOf(p.data)?.toUpperCase() ?? '')) outOfFlow = true; else return false;
    } else if (p.type === 'Color') {
      // Only fully transparent ink is paint-inert for real glyphs.
      if (alphaIsZero(p.data)) transparentInk = true; else return false;
    } else if (p.type !== 'Hyphens') {
      // Any other declaration (border colours included) could paint or move ink.
      return false;
    }
  }
  // Inert only when BOTH were declared on the member itself.
  return outOfFlow && transparentInk;
}

/** May this member be moved to its word's end? Compose's inert-drop gate: the text tag ring + the predicate. */
export function joinsAcrossWord(member: ComposedNode): boolean {
  // The tag ring first (a replaced/widget member paints whatever its colour), then the predicate.
  const tag = member.component.meta?.sourceTag?.toLowerCase();
  return !!tag && TEXT_MEMBER_TAGS.has(tag) && isInertOutOfFlowMember(member);
}

/**
 * Rewrite a resolved run plan so each paint-inert out-of-flow member that
 * splits a word sits at that word's end. Left to right, so several members
 * in one word all land after it in their original order. Identity (same
 * array, 0 joined) unless `hyphensAuto`.
 */
export function joinWordsAroundInertOutOfFlow(
  entries: ResolvedRun[],
  children: ComposedNode[],
  hyphensAuto: boolean,
): { entries: ResolvedRun[]; joined: number } {
  // Only an `auto` host has a hyphenator to lose (manual U+00AD points live inside the text).
  if (!hyphensAuto) return { entries, joined: 0 };
  // The rewritten plan, built left to right.
  const out: ResolvedRun[] = [];
  // Members deferred to the end of the word that `out`'s last text entry is building.
  const pending: ResolvedRun[] = [];
  // How many members moved (the caller's RunsPlacement.joinedOutOfFlowMembers).
  let joined = 0;
  // A fragment that ENDS / STARTS inside a word: no white space at that edge (mid-word = both).
  const inWordEnd = (t: string) => t.length > 0 && !WHITE_SPACE.test(t[t.length - 1]);
  const inWordStart = (t: string) => t.length > 0 && !WHITE_SPACE.test(t[0]);
  // The word ended: the deferred members land here, in their original order.
  const flush = () => { out.push(...pending); pending.length = 0; };
  // Append text: if it continues the word the deferred members wait on, they land at its first white space.
  const pushText = (t: string) => {
    const last = out[out.length - 1];
    if (pending.length > 0 && last?.kind === 'text' && inWordEnd(last.text) && inWordStart(t)) {
      // The word runs on into this entry: its end is this entry's first white space (or beyond it).
      const cut = t.search(WHITE_SPACE);
      if (cut < 0) { out.push({ kind: 'text', text: t }); return; }
      out.push({ kind: 'text', text: t.slice(0, cut) }); flush(); out.push({ kind: 'text', text: t.slice(cut) });
      return;
    }
    // A new word (or no member waiting): members first, then the text as it was.
    flush(); out.push({ kind: 'text', text: t });
  };
  for (let i = 0; i < entries.length; i++) {
    // This entry, the text it would follow, and the entry after it.
    const e = entries[i];
    const last = out[out.length - 1];
    const next = entries[i + 1];
    if (e.kind === 'child' && last?.kind === 'text' && next?.kind === 'text'
      && inWordEnd(last.text) && inWordStart(next.text) && joinsAcrossWord(children[e.index])) {
      // Mid-word inert member: glue the word's continuation onto the text before it, defer the member.
      pending.push(e); joined++;
      // The continuation's word part (up to its first white space) joins the text before the member.
      const cut = next.text.search(WHITE_SPACE);
      out[out.length - 1] = { kind: 'text', text: last.text + (cut < 0 ? next.text : next.text.slice(0, cut)) };
      // The word ends inside the continuation: the member lands there, the rest of the run follows.
      if (cut >= 0) { flush(); out.push({ kind: 'text', text: next.text.slice(cut) }); }
      i++; // `next` is consumed
      continue;
    }
    // Anything else: text may continue the word; a child ends it (deferred members land first).
    if (e.kind === 'text') pushText(e.text); else { flush(); out.push(e); }
  }
  // The run ended inside a word: its deferred members close the plan.
  flush();
  // No join: hand back the caller's own array, so the plan is untouched by construction.
  return joined === 0 ? { entries, joined } : { entries: out, joined };
}
