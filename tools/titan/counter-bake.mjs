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
// THE REVERSED IMPLIED VALUE — likewise derived. css-lists-3 §4.2 says an
// omitted `reversed()` integer means "the number of elements in scope that
// increment it", which reproduces NONE of the eight reversed references (they
// all come out one short, and the two with non-unit increments come out far
// short). The value the references actually demand is
//
//     initial = Σ|increment| over the scope  +  |the LAST increment|
//
// which for the ordinary all-(-1) list is N+1 — i.e. the first item prints N
// and the last prints 1, the whole point of a reversed counter. Verified
// against all eight: siblings-001a (-1,-2 → 5 ⇒ 4,2), siblings-002 (⇒ 1 and
// 3), siblings-003 (⇒ 4,2), display-none (⇒ 3,2,1, the display:none item
// contributing NOTHING), pseudo-001 (-1,-2,-1,-2 → 8 ⇒ 7,5,4,2), multiple
// (two counters at once ⇒ 2,4 / 1,2), list-item and list-item-start (⇒ 7).
//
// NOT MODELLED, and left as a refusal or an untouched declaration rather than
// an approximation:
//   • `<ol reversed>` — the `reversed` attribute does not ride `_attrs`
//     (LIST_ATTR_KEYS is `start`/`value`), so a reversed HTML list numbers
//     forward here. Adding it is a wire change, not an extractor change.
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

/** The implicit UA `counter-reset` a list container contributes, as if it had
 *  been authored. `<ol start=N>` seeds the counter at N-1 so the first item
 *  prints N (HTML §4.4.5). */
function uaResetsFor(node) {
  const tag = String(node?._tag ?? '').toLowerCase();
  if (!LIST_CONTAINER_TAGS.has(tag)) return [];
  const start = Number.parseInt(String(node?._attrs?.start ?? ''), 10);
  return [{ name: 'list-item', value: Number.isFinite(start) ? start - 1 : 0, reversed: false }];
}

/** Look up the innermost live counter of `name`, or null. */
const innermost = (set, name) => {
  const stack = set[name];
  return stack && stack.length ? stack[stack.length - 1] : null;
};

function setCounter(set, name, value) {
  const inst = innermost(set, name);
  if (inst) inst.value = value;
  else (set[name] ??= []).push({ name, value, reversed: false, increments: [] });
}

function bumpCounter(set, name, delta) {
  let inst = innermost(set, name);
  if (!inst) {
    inst = { name, value: 0, reversed: false, increments: [] };
    (set[name] ??= []).push(inst);
  }
  inst.value += delta;
  // Recorded for the reversed pre-pass; harmless on a forward counter.
  inst.increments.push(delta);
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
    (set[entry.name] ??= []).push({
      name: entry.name, value: entry.value ?? 0, reversed: entry.reversed, increments: [],
    });
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
        const inst = {
          name: entry.name,
          reversed: entry.reversed,
          increments: [],
          key,
          value: entry.value !== null ? entry.value
            : (entry.reversed ? (implied.get(key) ?? 0) : 0),
        };
        (own[entry.name] ??= []).push(inst);
        created.push(inst);
        // The nested-pop: see the SCOPE MODEL banner. Reversed instantiations
        // are exempt (siblings-003).
        if (outer && !entry.reversed) nestedPopped.push(entry.name);
      }
      // ── counter-set, then counter-increment ─────────────────────────
      // Both create the counter on the fly when it does not exist yet
      // (css-lists-3 §4.4 — implicitly instantiated on the root).
      if (!applyPairs(own, node.properties?.['counter-set'], 0, 'set')) { ok = false; return; }
      const liValue = Number.parseInt(String(node?._attrs?.value ?? ''), 10);
      const listItem = isListItem(node);
      // HTML §4.4.8 `<li value=N>` sets the ordinal outright before the
      // implicit increment, so the item itself prints N.
      if (listItem && Number.isFinite(liValue)) setCounter(own, 'list-item', liValue - 1);
      const incRaw = node.properties?.['counter-increment'];
      if (listItem && incRaw === undefined) {
        // The UA `display: list-item` increment. Its SIGN follows the counter
        // it targets: a reversed list counts down.
        const li = innermost(own, 'list-item');
        bumpCounter(own, 'list-item', li && li.reversed ? -1 : 1);
      }
      if (!applyPairs(own, incRaw, 1, 'increment')) { ok = false; return; }
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
    // css-lists-3 §5.1: an undefined counter behaves as if instantiated on
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
    const incs = inst.increments;
    implied.set(inst.key, incs.length === 0
      ? 0
      : incs.reduce((a, b) => a + Math.abs(b), 0) + Math.abs(incs[incs.length - 1]));
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
