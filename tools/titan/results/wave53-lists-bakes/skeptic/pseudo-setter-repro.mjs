// tools/titan/results/wave53-lists-bakes/skeptic/pseudo-setter-repro.mjs — L1 skeptic repro: the `${nodeIdx}::${name}`
// step-4 attribution key for a PSEUDO setter is unpinned (mutation SN3C survives the lane's suites). This prints the
// bake's numbers for a reversed counter whose first setter is a ::before (css-lists-3 §4.4.2 arithmetic in comments).
import { bakeCounters } from '../../../counter-bake.mjs';
const cmp = (properties = {}, extra = {}) => ({ properties, ...extra });
const contents = (m, out = []) => { for (const n of Object.values(m ?? {})) { for (const pe of ['before', 'after']) { const c = n._pseudo?.[pe]?.properties?.content; if (c !== undefined) out.push(c); } contents(n.children, out); } return out; };
// P1: a{inc -1} a::before; b{inc -1} b::before{counter-set: foo 10}; c{inc -1} c::before.
// §4.4.2 walk: a 1 → b 1 (=2) → b::before sets 10 (=12), break; step 4 + last non-zero (b's 1) = 13 ⇒ 12, 10, 9.
const P1 = { ol: cmp({ 'counter-reset': 'reversed(foo)' }, { children: {
  a: cmp({ 'counter-increment': 'foo -1' }, { _pseudo: { before: { properties: { content: 'counter(foo)' } } } }),
  b: cmp({ 'counter-increment': 'foo -1' }, { _pseudo: { before: { properties: { 'counter-set': 'foo 10', content: 'counter(foo)' } } } }),
  c: cmp({ 'counter-increment': 'foo -1' }, { _pseudo: { before: { properties: { content: 'counter(foo)' } } } }),
} }) };
bakeCounters({ components: P1 });
console.log('P1 pseudo setter after its element\'s own step:', JSON.stringify(contents(P1)), '(spec arithmetic: 12, 10, 9)');
// P2: one ::before both INCREMENTS and SETS (the bake applies a bag's set BEFORE its increment — pseudoSet).
// §4.4.2: a::before incNeg 1 (=1) → b::before incNeg 2 (=3), sets 10 (=13), break; step 4 + last non-zero (b::before's 2) = 15.
const P2 = { ol: cmp({ 'counter-reset': 'reversed(foo)' }, { children: {
  a: cmp({}, { _pseudo: { before: { properties: { 'counter-increment': 'foo -1', content: 'counter(foo)' } } } }),
  b: cmp({}, { _pseudo: { before: { properties: { 'counter-increment': 'foo -2', 'counter-set': 'foo 10', content: 'counter(foo)' } } } }),
  c: cmp({}, { _pseudo: { before: { properties: { 'counter-increment': 'foo -1', content: 'counter(foo)' } } } }),
} }) };
bakeCounters({ components: P2 });
console.log('P2 one ::before that steps AND sets:', JSON.stringify(contents(P2)), '(spec initial 15 ⇒ a::before 14; the bake\'s initial is visible in a\'s number)');
