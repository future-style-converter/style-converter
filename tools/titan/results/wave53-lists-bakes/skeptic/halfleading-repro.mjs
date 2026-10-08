// tools/titan/results/wave53-lists-bakes/skeptic/halfleading-repro.mjs — L1 skeptic repro: planMarker places each marker
// glyph at `first.run.y + q.y`, where q.y is the glyph's top RELATIVE TO THE PROBE SPAN's box. The span is appended to
// <body> without a line-height, so it inherits BODY's line-height and q.y is that line's half-leading — while
// `first.run.y` (the walker's Range rect) is already the text's content-area top. Same font ⇒ same content-area top, so
// the right answer is `first.run.y + (q.y − q0.y)`. Pure function, model inputs: q.y = 14 is what a body
// `line-height: 48px` gives a 20 px Inter content area at 16 px ((48 − 20) / 2); counter-suffix itself has q.y ≈ 0
// (body inherits the pinned 1.25 → 20 px = the content area), so the corpus target is NOT affected.
import { planMarker } from '../../../bidi-marker-bake.mjs';
const style = { color: 'rgb(0, 0, 0)', fontFamily: 'Inter', fontSize: '16px', fontStyle: 'normal', fontWeight: '400',
  letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none', fontVariantNumeric: 'tabular-nums' };
const facts = (qy) => ({ direction: 'rtl', position: 'outside', type: 'decimal', image: false, contentStart: 48, contentEnd: 112,
  style, text: '1. ', textModelled: false, cdpBox: { x: 112, y: 192, width: 18.8, height: 24 },
  glyphs: { width: 18.8, chars: [{ c: '1', rects: [{ x: 8.9, y: qy, w: 9.9, h: 20 }] },
    { c: '.', rects: [{ x: 4.48, y: qy, w: 4.42, h: 20 }] }, { c: ' ', rects: [{ x: 0, y: qy, w: 4.48, h: 20 }] }] } });
const first = { run: { y: 194 }, style };                  // the item's text run: content-area top at y194 (li top 192 + 2)
for (const qy of [0, 14]) {
  const m = planMarker(facts(qy), { x: 48, y: 192 }, first);
  console.log(`SK probe-span half-leading q.y=${qy}: text run top 2px, marker run tops`, m.runs.map((r) => `${r.text}@${r.props.top}`).join(' '));
}
