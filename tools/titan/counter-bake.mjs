//
// tools/titan/counter-bake.mjs — wave-37 lane W8, THE COUNTER TREE.
//
// THE HOLE THIS CLOSES, measured, in the pipeline.
// -----------------------------------------------
// generated-content-bake.mjs (wave 36) turns a pseudo-element's `content`
// into paintable `_text`, and its header records the population it had to
// walk away from in as many words:
//
//     3,151  counter() / counters()   — NOT this lane (see REFUSALS)
//     …"needs the css-lists-3 counter tree, which is a different (and much
//       larger) lane"
//
// That is 3,151 of the 3,695 `content` declarations in the corpus — by far
// the largest single refusal in the extractor. On the wave36-final depth-48
// gate it is 25 of the 254 failing web cells, the biggest shared mechanism in
// the tail: every css-lists `counter-reset-reversed-*`, `counter-00N`,
// `counter-list-item*` and five css-counter-styles cells capture a BLANK
// pseudo box where the reference paints a number.
//
// This module is that lane. It is a strict PRE-PASS to the generated-content
// bake and never edits it: it resolves each `counter()`/`counters()` call to
// its literal text and splices a CSS <string> back into the SAME `content`
// declaration. The next bake then sees a pure <string> sequence — the shape
// it already resolves — and writes `_text` itself. One producer of `_text`,
// two producers of the string that feeds it.
//
// REFUSALS keep the same contract as its neighbours: if ANY call in a value
// cannot be resolved (unparseable grammar, a counter style outside the §6
// predefined table), the declaration is left BYTE-IDENTICAL. Never a partial
// substitution.
//
// THE SCOPE MODEL — derived from the references, and where it bends.
// css-lists-3 §4.4's counters-set inheritance is not transcribable from prose
// alone (the corpus disagrees with the naive reading twice), so every rule
// below names the reftest that pins it:
//
//   • A `counter-reset` on E creates a counter visible to E, E's descendants,
//     and E's FOLLOWING SIBLINGS and their descendants.
//     PINNED BY counter-reset-reversed-siblings-001a: the reset sits on the
//     first <div>, the second `.counter-use` is that div's SIBLING, and the
//     reference numbers them 4 then 2 — one continuous counter.
//   • EXCEPT that a NESTED instantiation — one where a counter of the same
//     name was already in scope FROM AN ANCESTOR — is not inherited by the
//     following siblings; they keep the outer counter.
//     PINNED BY counter-001/-002/-003: `#test { counter-reset: c }` with a
//     mid-list `<span style="counter-reset: c 98">`; the reference prints
//     …12, 99, 13, 14 — the span's own 99, then the OUTER counter resuming.
//   • …and that exception does NOT apply to `reversed()`.
//     PINNED BY counter-reset-reversed-siblings-003: `counter-reset: foo 10`
//     on the outer div, `reversed(foo)` on an inner one, and the reference
//     numbers the inner div's child 4 and the OUTER div's next child 2 —
//     i.e. the nested reversed counter DID reach the following sibling. A
//     nested-pop there would have printed 8.
//   A two-reference fact, recorded as such rather than smoothed over.
//
// THE REVERSED IMPLIED VALUE — likewise derived. css-lists-3 §4.2's one-line
// reading ("the number of elements in scope that increment it") reproduces
// NONE of the eight reversed references. wave-37 replaced it with the
// empirical `Σ|increment| + |the LAST increment|`, which fits all eight
// because every one of them increments by a NEGATIVE amount and none of them
// carries a `counter-set`. wave-44 lane H2 widened the lane to `<ol reversed>`
// and had to face the populations that do both, so the formula is now
// css-lists-3 §4.4.2's actual walk — a strict GENERALISATION that agrees with
// the old one wherever the old one was pinned:
//
//     num = 0
//     for each element in the counter's scope, in tree order:
//         num += −(its increment)                       ← "incrementNegated"
//         if it counter-SETs this counter:  num += that value;  STOP
//     if the walk ran to the end:  num += the LAST NON-ZERO incrementNegated
//
// (SIGNED, not absolute, on that trailing term.) For the ordinary all-(−1)
// list this is still N+1 — first item prints N, last prints 1. The three
// places it differs from the wave-37 formula are all pinned by references:
//   • POSITIVE increments — li-value-reversed-006a's third list (three
//     `counter-increment: list-item 2` items) has ref `<ol reversed start=-9>`
//     ⇒ −6,−4,−2, i.e. initial −8 = (−2−2−2) + (−2). The old formula said
//     +8 and painted 10,12,14. Its own ref's `<meta name=assert>` states the
//     rule outright: "The last counter-increment value determines the start".
//   • a `counter-set` in scope STOPS the walk — li-value-reversed-001
//     (`<li value=6>` second of three) has ref 7,6,5 ⇒ initial 8 =
//     (+1)+(+1)+6, not the old formula's 4.
//   • `<li value>` IS a counter-set (HTML §15.3.7), which is why
//     css-lists/counter-list-item's third reversed list starts at 32 and not
//     at 6 — the same anchor the natives' ListOrdinal already implements.
// Still verified against all eight wave-37 pins (all-negative, set-free ⇒
// Σ(−inc) + last(−inc) IS Σ|inc| + |last inc|): siblings-001a (-1,-2 → 5 ⇒
// 4,2), siblings-002 (⇒ 1 and 3), siblings-003 (⇒ 4,2), display-none (⇒
// 3,2,1, the display:none item contributing NOTHING), pseudo-001
// (-1,-2,-1,-2 → 8 ⇒ 7,5,4,2), multiple (two counters at once ⇒ 2,4 / 1,2),
// list-item and list-item-start (⇒ 7).
//
// KNOWN OUTLIER, measured and named rather than smoothed over:
// li-value-reversed-019's INNER list (`<li>Four` whose own child `<div>` does
// `counter-set: list-item 3`) wants initial 5; the walk above yields 4, so
// that one item paints 3 where the reference paints 4. Every other reference
// in the `li-value-reversed-*` family that this lane can see agrees with the
// walk. One item on one test is not worth a second, unpinned rule.
//
// NOT MODELLED, and left as a refusal or an untouched declaration rather than
// an approximation:
//   • counter styles outside counter-style-table.mjs's §6 transcription
//     (including author `@counter-style` rules) — refused, not defaulted to
//     decimal, so a fixture never claims a number the document didn't ask for.
//   • `counter-reset` inside a `display: none` subtree — such elements are
//     skipped whole (css-lists-3 §4.6 "counters without boxes"), which the
//     reversed-display-none reference confirms is what Blink does.

import { counterRepresentation } from './counter-style-bake.mjs';
import {
  parseCounterReset, parseCounterPairs, findCounterCalls, asCssString,
} from './counter-functions.mjs';

export const COUNTER_BAKED_REASON = 'counter-tree-baked';

// THE SHADOW-TREE BAIL — measured, and the one regression this lane found.
//
// css-lists-3 §4.4 numbers counters over the FLATTENED tree: a `<slot>`
// re-parents its assigned light-DOM nodes, so the document order this walker
// reads off the source markup is not the order the counters see. The corpus
// has exactly one such test in the depth-48 gate,
// css-lists/counter-list-item-slot-order.html, whose three `<li slot=…>`
// elements appear in source order 3, 2, 1 and render as 1, 2, 2.1. Baking it
// PAINTED "1. / 2. / 3." against the reference's "1. / 2. / 2.1" and cost a
// passing cell (28→41 became 28→40); refusing it restores the cell and is
// the honest answer — the numbering is not computable from this input.
//
// Same shape as counter-style-bake.mjs's DYNAMIC_SIGNAL_RX / MARKER_OVERRIDE_RX
// bails: recognise the idiom in the AUTHORED source, refuse the whole
// document, leave every declaration byte-identical.
// `slot=` (the ASSIGNMENT side) is in the alternation as well as `<slot>` and
// the two shadow-root spellings: the slot-order test declares its `<slot>`s
// from a script string, so only the light-DOM `slot="list3"` attributes are
// visible in the authored markup a regex can see.
const SHADOW_TREE_RX = /attachShadow\s*\(|shadowrootmode|<slot\b|\bslot\s*=/i;

/** Tags whose UA stylesheet carries `counter-reset: list-item 0`. */
const LIST_CONTAINER_TAGS = new Set(['ol', 'ul', 'menu', 'dir']);

/** css-display-3: only a `display: list-item` box increments `list-item`. An
 *  <li> with no `display` declaration is one (the UA default); an <li> that
 *  declares something else is not; any element that declares `list-item` is —
 *  which is what counter-reset-reversed-list-item measures with seven
 *  `<span class="li">`s. */
function isListItem(node) {
  const display = node?.properties?.display;
  if (typeof display === 'string' && display.trim()) {
    return display.toLowerCase().split(/\s+/).includes('list-item');
  }
  return String(node?._tag ?? '').toLowerCase() === 'li';
}

/** css-lists-3 §4.6 — a box that is not generated takes no part in counting.
 *  `visibility: hidden` DOES count (it generates a box); only `display: none`
 *  drops out. Both halves are on counter-reset-reversed-display-none. */
function isDisplayNone(node) {
  return String(node?.properties?.display ?? '').trim().toLowerCase() === 'none';
}

/** wave-44 lane H2 — HTML §4.4.5 boolean-attribute semantics for
 *  `<ol reversed>`: PRESENCE is the whole value (`reversed="false"` still
 *  reverses). The v2 wire carries it as a literal `true`
 *  (extract-fixture.mjs LIST_BOOLEAN_ATTR_KEYS, wave-44 lane U5), but a
 *  hand-authored or pre-U5 bag may spell it as the raw attribute string;
 *  both mean present. Only a literal JS `false` — which nothing emits, but
 *  which is the one unambiguous "explicitly not present" marker — is not. */
function hasReversedAttr(node) {
  const v = node?._attrs?.reversed;
  return v !== undefined && v !== false;
}

/** The implicit UA `counter-reset` a list container contributes, as if it had
 *  been authored. `<ol start=N>` seeds the counter at N-1 so the first item
 *  prints N (HTML §4.4.5).
 *
 *  wave-44 lane H2 — `<ol reversed>` instantiates a REVERSED `list-item`
 *  counter instead (css-lists-3 §4.4.2 via the HTML §15.3.7 presentational
 *  hint), which flips the implicit per-item increment to −1 (see the walk).
 *  Its seed is the mirror of the forward one: `start=N` seeds N+1 so the
 *  first item prints N after its −1 step (li-value-reversed-011:
 *  `<ol reversed start=3>` ⇒ 3,2,1), and an ABSENT `start` seeds `null` —
 *  the "compute it from the scope" marker the reversed implied-value walk
 *  above resolves in pass 1. */
function uaResetsFor(node) {
  const tag = String(node?._tag ?? '').toLowerCase();
  if (!LIST_CONTAINER_TAGS.has(tag)) return [];
  const start = Number.parseInt(String(node?._attrs?.start ?? ''), 10);
  if (hasReversedAttr(node)) {
    return [{
      name: 'list-item',
      value: Number.isFinite(start) ? start + 1 : null,
      reversed: true,
    }];
  }
  return [{ name: 'list-item', value: Number.isFinite(start) ? start - 1 : 0, reversed: false }];
}

/** Look up the innermost live counter of `name`, or null. */
const innermost = (set, name) => {
  const stack = set[name];
  return stack && stack.length ? stack[stack.length - 1] : null;
};

/** One counter INSTANCE, with the css-lists-3 §4.4.2 accumulators the
 *  reversed pre-pass reads (see the IMPLIED VALUE banner). The three extra
 *  fields are inert on a forward counter — nothing but the reversed branch
 *  of bakeCounters ever looks at them. */
function newInstance(name, value, reversed, key = null) {
  return {
    name, value, reversed, key,
    negSum: 0,       // Σ of −increment over the walk so far
    lastNeg: 0,      // the LAST NON-ZERO −increment (signed) — the trailing term
    stopped: false,  // a counter-set has frozen the walk
    implied: 0,      // the frozen num, once `stopped`
  };
}

function setCounter(set, name, value) {
  const inst = innermost(set, name);
  if (!inst) { (set[name] ??= []).push(newInstance(name, value, false)); return; }
  // §4.4.2: the walk stops at the FIRST element that counter-sets this
  // counter, adding the set VALUE to what it has accumulated so far. Recorded
  // before the assignment because `negSum` must not include anything after.
  if (inst.reversed && !inst.stopped) {
    inst.implied = inst.negSum + value;
    inst.stopped = true;
  }
  inst.value = value;
}

function bumpCounter(set, name, delta) {
  let inst = innermost(set, name);
  if (!inst) {
    inst = newInstance(name, 0, false);
    (set[name] ??= []).push(inst);
  }
  inst.value += delta;
  // §4.4.2 accumulation for the reversed pre-pass; a forward counter and a
  // walk already stopped by a counter-set both ignore it.
  if (inst.reversed && !inst.stopped) {
    inst.negSum += -delta;
    // "the last NON-ZERO incrementNegated" — a `counter-increment: x 0`
    // element takes part in the walk but cannot be the trailing term.
    if (delta !== 0) inst.lastNeg = -delta;
  }
}

/** The counters set in force INSIDE one pseudo-element bag: a copy of the
 *  originating element's, with the bag's own counter-reset/-set/-increment
 *  applied. Returns null when a declaration there is unparseable. */
function pseudoSet(bag, own) {
  const props = bag?.properties ?? {};
  if (props['counter-reset'] === undefined && props['counter-set'] === undefined
      && props['counter-increment'] === undefined) {
    return own;
  }
  const set = {};
  for (const [k, v] of Object.entries(own)) set[k] = v.slice();
  const reset = props['counter-reset'] === undefined ? [] : parseCounterReset(props['counter-reset']);
  if (reset === null) return null;
  for (const entry of reset) {
    // A reversed counter instantiated on a PSEUDO is not something any
    // corpus reference exercises; its implied value would need its own key
    // in the two-pass measurement, so it starts at its explicit value or 0.
    (set[entry.name] ??= []).push(
      newInstance(entry.name, entry.value ?? 0, entry.reversed));
  }
  if (!applyPairs(set, props['counter-set'], 0, 'set')) return null;
  if (!applyPairs(set, props['counter-increment'], 1, 'increment')) return null;
  return set;
}

/** Apply a `counter-set` / `counter-increment` declaration. Returns false on
 *  an unparseable value, which aborts the whole bake (a refusal). */
function applyPairs(set, raw, dflt, kind) {
  if (raw === undefined) return true;
  const pairs = parseCounterPairs(raw, dflt);
  if (pairs === null) return false;
  for (const p of pairs) {
    if (kind === 'set') setCounter(set, p.name, p.value);
    else bumpCounter(set, p.name, p.value);
  }
  return true;
}

/**
 * Walk the built component tree in document order, maintaining the
 * css-lists-3 counters set, calling `onUse(bag, set)` at every pseudo bag.
 *
 * The walk is run TWICE with identical structure: pass 1 lets every reversed
 * counter accumulate the increments that resolve to it, pass 2 replays with
 * the implied initial values those increments imply. `implied` is keyed by
 * `"<node index>#<name>#<n-th reset entry on that node>"`, a key both passes
 * derive the same way because the walk order is deterministic.
 *
 * @returns `{ ok, created }` — `created` is every counter INSTANCE the walk
 *   instantiated, in creation order, each carrying its own `key`.
 */
function walk(components, implied, onUse) {
  const created = [];
  let nodeSeq = 0;
  let ok = true;

  const visit = (map, inherited) => {
    let cur = inherited;
    for (const node of Object.values(map ?? {})) {
      if (!node || typeof node !== 'object') continue;
      // A non-generated box neither counts nor lets its subtree count.
      if (isDisplayNone(node)) continue;
      const nodeIdx = nodeSeq++;
      // Copy-on-write: each element gets its own name→stack map, but the
      // INSTANCES inside are shared by reference, which is what keeps a
      // descendant's increment visible to the originator's next sibling.
      const own = {};
      for (const [k, v] of Object.entries(cur)) own[k] = v.slice();
      // ── counter-reset (instantiate) ─────────────────────────────────
      // UA list resets come first so an authored `counter-reset` on the same
      // <ol> still shadows them, as an author sheet shadows the UA.
      const declared = node.properties?.['counter-reset'];
      const authored = declared === undefined ? [] : parseCounterReset(declared);
      if (authored === null) { ok = false; return; }
      const nestedPopped = [];
      const entries = [...uaResetsFor(node), ...authored];
      for (let e = 0; e < entries.length; e++) {
        const entry = entries[e];
        const outer = innermost(own, entry.name);
        const key = `${nodeIdx}#${entry.name}#${e}`;
        const inst = newInstance(
          entry.name,
          entry.value !== null ? entry.value
            : (entry.reversed ? (implied.get(key) ?? 0) : 0),
          entry.reversed,
          key,
        );
        (own[entry.name] ??= []).push(inst);
        created.push(inst);
        // The nested-pop: see the SCOPE MODEL banner. Reversed instantiations
        // are exempt (siblings-003).
        if (outer && !entry.reversed) nestedPopped.push(entry.name);
      }
      // ── counter-set, then counter-increment ─────────────────────────
      // Both create the counter on the fly when it does not exist yet
      // (css-lists-3 §4.4 — implicitly instantiated on the root).
      const liValue = Number.parseInt(String(node?._attrs?.value ?? ''), 10);
      const listItem = isListItem(node);
      const incRaw = node.properties?.['counter-increment'];
      if (listItem && incRaw === undefined) {
        // The UA `display: list-item` increment. Its SIGN follows the counter
        // it targets: a reversed list counts down.
        const li = innermost(own, 'list-item');
        bumpCounter(own, 'list-item', li && li.reversed ? -1 : 1);
      }
      if (!applyPairs(own, incRaw, 1, 'increment')) { ok = false; return; }
      // HTML §4.4.8 `<li value=N>` — a `counter-set: list-item N` hint
      // (HTML §15.3.7), so the item prints N outright.
      //
      // wave-44 lane H2 moved this AFTER the increment. It used to write
      // `N − 1` before it, which survives a forward +1 step to the same N but
      // is wrong twice over once reversed lists are in the lane: a −1 step
      // would land on N − 2, and a DECLARED `counter-increment` on the same
      // <li> (li-value-reversed-013: `<li value=3 style="counter-increment:
      // list-item -2">`, reference 5,3,2) was applied after the pre-offset
      // set and moved the item off its own value. Setting N after the step
      // is byte-identical for every forward list and correct for both.
      // It is also what makes the §4.4.2 implied-value walk see this
      // element's own increment BEFORE its set, which the walk requires.
      if (listItem && Number.isFinite(liValue)) setCounter(own, 'list-item', liValue);
      // ── counter-set, LAST ────────────────────────────────────────────
      // wave-44 lane H2 moved this from before counter-increment to after
      // it. MEASURED in the pinned headless Chromium (`<ol start=11><li
      // style="counter-set: list-item 8">` paints marker 8 then 9, and the
      // same li with an added `counter-increment: list-item 5` still paints
      // 8): the set is applied AFTER the increment, so it wins outright.
      // li-value-reversed-008's reference says the same thing statically
      // (`<li style="counter-set: list-item 8">8` inside `<ol start=11>`),
      // and -022's reference needs it for both the item value and the
      // reversed implied walk. The old order silently subtracted the
      // element's own increment from every counter-set item.
      // UN-REFERENCED CORNER, named not hidden: an element carrying BOTH a
      // `<li value>` hint and an author `counter-set` for list-item applies
      // the author declaration last (correct cascade — a presentational hint
      // loses), but the §4.4.2 walk anchors on whichever ran FIRST, i.e. the
      // hint. No corpus reference exercises the pair.
      if (!applyPairs(own, node.properties?.['counter-set'], 0, 'set')) { ok = false; return; }
      // ── uses, in css-content-3 §2.1 document order ──────────────────
      // A pseudo-element is a real box in the counter tree: it may carry its
      // own counter-reset/-set/-increment, and its INCREMENTS are visible to
      // everything after it (counter-reset-reversed-pseudo-001 numbers a
      // ::before and an ::after of the same element 7 then 5). A reset there
      // is scoped to the pseudo alone, which is why each gets its own map
      // copy while the instances inside stay shared.
      const usePseudo = (name) => {
        const bag = node._pseudo?.[name];
        if (!bag) return;
        const scoped = pseudoSet(bag, own);
        if (scoped === null) { ok = false; return; }
        onUse(bag, scoped);
      };
      usePseudo('marker');
      usePseudo('before');
      if (!ok) return;
      visit(node.children, own);
      if (!ok) return;
      usePseudo('after');
      if (!ok) return;
      // What the FOLLOWING SIBLING inherits: this element's own set, minus
      // the nested non-reversed instantiations.
      const next = {};
      for (const [k, v] of Object.entries(own)) next[k] = v.slice();
      for (const name of nestedPopped) next[name].pop();
      cur = next;
    }
  };
  visit(components, {});
  return { ok, created };
}

/**
 * Resolve one pseudo bag's `content`, splicing literal text in place of each
 * counter call. NOTHING is written here: a successful rewrite is QUEUED on
 * `queue` and applied by the caller only once the whole document resolved,
 * so a refusal three bags later still leaves the fixture byte-identical.
 * `queue` null means "measure only" (pass 1).
 *
 * @returns false on a refusal.
 */
function resolveBag(bag, set, queue) {
  if (!bag || typeof bag !== 'object') return true;
  const content = bag.properties?.content;
  if (typeof content !== 'string' || !/counters?\s*\(/i.test(content)) return true;
  const calls = findCounterCalls(content);
  if (calls === null) return false;
  if (calls.length === 0) return true;
  if (queue === null) return true;
  let out = '';
  let cursor = 0;
  for (const call of calls) {
    const stack = set[call.name] ?? [];
    // css-lists-3 §4.7: an undefined counter behaves as if instantiated on
    // the root with value 0.
    const values = stack.length ? stack.map((c) => c.value) : [0];
    // `counters()` walks the whole nesting chain outermost-first; `counter()`
    // reads the innermost one only.
    const parts = call.fn === 'counters' ? values : [values[values.length - 1]];
    const texts = [];
    for (const v of parts) {
      const rep = counterRepresentation(call.style, v);
      if (rep === null) return false;          // style outside the §6 table
      texts.push(rep);
    }
    out += content.slice(cursor, call.start) + asCssString(texts.join(call.sep ?? ''));
    cursor = call.end;
  }
  out += content.slice(cursor);
  queue.push({ bag, value: out });
  return true;
}

/**
 * Bake one fixture in place.
 *
 * @param fixture `{ components, _wpt }` as extractFixture built it.
 * @param html    the AUTHORED test source, for the shadow-tree bail. Omit to
 *                skip that gate (unit tests supply fixtures only).
 * @returns `{ status: 'skipped'|'bailed'|'refused'|'baked', resolved }`
 */
export function bakeCounters(fixture, html = '') {
  const components = fixture?.components;
  // Cheap gate: no `counter` substring anywhere ⇒ nothing to do, and the
  // fixture is untouched by construction.
  if (!components || !JSON.stringify(components).includes('counter')) {
    return { status: 'skipped', resolved: 0 };
  }
  // A slot-flattened tree is not the tree this walker can see — see the
  // SHADOW-TREE BAIL banner. Bail LOUDLY rather than paint a wrong ordinal.
  if (SHADOW_TREE_RX.test(html)) {
    markLossy(fixture);
    return { status: 'bailed', resolved: 0, reason: 'shadow tree / slot' };
  }
  // ── PASS 1 — let every reversed counter measure its own scope ────────
  const pass1 = walk(components, new Map(), (bag, set) => { resolveBag(bag, set, null); });
  if (!pass1.ok) return refuse(fixture);
  const implied = new Map();
  for (const inst of pass1.created) {
    if (!inst.reversed) continue;
    // css-lists-3 §4.4.2 (see the IMPLIED VALUE banner): a walk stopped by a
    // counter-set carries its own frozen num; a walk that ran to the end adds
    // the last non-zero incrementNegated (SIGNED — li-value-reversed-006a's
    // positive-increment lists have NEGATIVE initial values). Nothing
    // incremented ⇒ negSum 0 + lastNeg 0 ⇒ 0, the pre-wave-44 answer for an
    // empty scope (li-value-reversed-006d's `<ol reversed></ol>`).
    implied.set(inst.key, inst.stopped ? inst.implied : inst.negSum + inst.lastNeg);
  }
  // ── PASS 2 — resolve with the implied values in hand ─────────────────
  const queue = [];
  let refused = false;
  const pass2 = walk(components, implied, (bag, set) => {
    if (resolveBag(bag, set, queue) === false) refused = true;
  });
  if (!pass2.ok || refused) return refuse(fixture);
  // All-or-nothing: only now does anything get written.
  for (const { bag, value } of queue) bag.properties.content = value;
  const resolved = queue.length;
  if (resolved > 0) {
    fixture._wpt ??= {};
    fixture._wpt.counterBaked = true;
    markLossy(fixture);
  }
  return { status: 'baked', resolved };
}

function refuse(fixture) {
  markLossy(fixture);
  return { status: 'refused', resolved: 0 };
}

/** Same stamp discipline as counter-style-bake: only a fixture that already
 *  carries a `lossy` field (i.e. the test half, never the ref half) is
 *  touched, so a refusal stays byte-identical everywhere else. */
function markLossy(fixture) {
  if (!fixture?._wpt || !('lossy' in fixture._wpt)) return;
  fixture._wpt.lossy = true;
  fixture._wpt.lossyReasons =
    [...new Set([...(fixture._wpt.lossyReasons ?? []), COUNTER_BAKED_REASON])];
}
