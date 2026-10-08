#!/usr/bin/env node
// wave-53 fix pass (S1 S4 = L3 skeptic should-fix 2, XI1) — EXECUTED source pins of L3's
// iOS CALL SITES in apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift.
// The ios-harness XCTest target needs a booted simulator (lane rules forbid one), and
// L5's IcbClip substrings (replayed by ../wave52-composed-canvas/ios-source-pins.mjs)
// cannot see these lines: removing all three L3 call sites stayed GREEN (XI1). This
// script slices the struct exactly as that replay does (ComposedCaptureCanvas →
// DynamicCaptureHooks, `//` lines dropped, so prose cannot satisfy a pin), checks five
// named pins against the live file, then turns each of six in-memory MUTATIONS of it
// red. The source file is only read. Prints its verdicts; appends them to
// ios-callsite-pins.out.txt ONLY with `--record`; exit 1 if any expectation fails.
// Usage: node tools/titan/results/wave53-canvas-root/ios-callsite-pins.mjs [--record]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

// Repo root = four levels up from this results directory.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const SRC = 'apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift';
const source = fs.readFileSync(path.join(ROOT, SRC), 'utf8');
const sha = crypto.createHash('sha256').update(source).digest('hex').slice(0, 16);

/** The ComposedCaptureCanvas struct, code lines only (ios-source-pins.mjs's slicing). */
function bodyCode(src) {
  const start = 'struct ComposedCaptureCanvas: View {';
  const end = 'struct DynamicCaptureHooks: ViewModifier {';
  if (!src.includes(start) || !src.includes(end)) throw new Error('anchors missing');
  return src.split(start).slice(1).join(start).split(end)[0]
    .split('\n').filter((l) => !l.trim().startsWith('//')).join('\n');
}
// First index of a needle (null when absent), occurrence count, and a whitespace-free view.
const at = (hay, needle) => { const i = hay.indexOf(needle); return i < 0 ? null : i; };
const count = (hay, needle) => hay.split(needle).length - 1;
const flat = (s) => s.replace(/\s+/g, '');

/** The five pins (true = holds). Each names the decision the device picture depends on. */
const PINS = {
  // Item A paints ON the propagated colour (attached before it: SwiftUI paints an earlier
  // `.background` in FRONT of a later one), OUTSIDE the inline-axis ICB clip (a uniform stack
  // covers the frame) and INSIDE a root clip (attached before `.rootCanvasClip`).
  rootImageLayerSitsBetweenTheClipAndTheColour: (c) => {
    const clip = at(c, '.clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))');
    const img = at(c, '.background(rootImageBackground)');
    const colour = at(c, '.background(canvasBackground)');
    const rootClip = at(c, '.rootCanvasClip(document.components, frame: Self.padding)');
    return [clip, img, colour, rootClip].every((x) => x !== null) && count(c, '.background(rootImageBackground)') === 1
      && clip < img && img < colour && img < rootClip;
  },
  // §2.11.2 "not painted again" + item B: the split runs over the image-stripped forest, as ONE
  // synthetic table for a table body, inside the wave-52 one-owner margin strip.
  splitStripsTheImageThenBuildsTheTableForest: (c) => flat(c).includes(flat(
    `FixedHoist.split(roots: UABlockMargin.withCanvasOwnedBodyMargin(
       TableBodyForest.rewrite(
         RootBackgroundPropagation.withCanvasOwnedRootBackground(document.components, rootImagePlan)),
       UABlockMargin.canvasBodyMargin(document.components))`)),
  // §3.4: a scroll layer's origin is the root box = the ICB + the margin this canvas owns.
  planReadsTheCanvasOwnedMargin: (c) => flat(c).includes(flat('let m = UABlockMargin.canvasBodyMargin(document.components)'))
    && flat(c).includes(flat('RootBackgroundPropagation.plan(body.properties, marginTop: m.top, marginLeft: m.left,')),
  // css-contain-2 §2: the image reads the SAME containment gate as the colour.
  planReadsTheColourContainmentGate: (c) =>
    flat(c).includes(flat('contained: WPTCanvas.containmentBlocksPropagation(Self.containKeywords(of: body)))')),
  // The paint reads the UNSTRIPPED body bag, framed by the 16-px capture frame.
  paintReadsTheUnstrippedBodyAtTheFrame: (c) =>
    flat(c).includes(flat('RootCanvasBackground(properties: body.properties, plan: plan, frame: Self.padding)'))
    && count(c, 'document.components.first(where: { $0.meta?.role == "body-root" })') >= 2,
};
const run = (src) => Object.fromEntries(Object.entries(PINS).map(([k, f]) => [k, f(bodyCode(src))]));

// The exact source lines the mutations edit (asserted present before any mutation runs).
const IMG_LINE = '        .background(rootImageBackground)\n';
const COLOUR_LINE = '        .background(canvasBackground)\n';
const STRIP = 'RootBackgroundPropagation.withCanvasOwnedRootBackground(document.components, rootImagePlan)';
const REWRITE = `TableBodyForest.rewrite(\n                ${STRIP})`;
/** [id, transform, the pin that must go red]. */
const MUTATIONS = [
  // The L3 skeptic's XI1: every L3 call site removed at once.
  ['XI1', (s) => s.replace(IMG_LINE, '').replace(REWRITE, 'document.components'), 'rootImageLayerSitsBetweenTheClipAndTheColour'],
  ['XI2', (s) => s.replace(IMG_LINE, '').replace(COLOUR_LINE, COLOUR_LINE + IMG_LINE), 'rootImageLayerSitsBetweenTheClipAndTheColour'],
  ['XI3', (s) => s.replace(STRIP, 'document.components'), 'splitStripsTheImageThenBuildsTheTableForest'],
  ['XI4', (s) => s.replace(REWRITE, STRIP), 'splitStripsTheImageThenBuildsTheTableForest'],
  ['XI5', (s) => s.replace('plan: plan, frame: Self.padding)', 'plan: plan, frame: 0)'), 'paintReadsTheUnstrippedBodyAtTheFrame'],
  ['XI6', (s) => s.replace('contained: WPTCanvas.containmentBlocksPropagation(Self.containKeywords(of: body)))', 'contained: false)'),
    'planReadsTheColourContainmentGate'],
];

let ok = [IMG_LINE, COLOUR_LINE, REWRITE].every((n) => source.includes(n)); const log = [];
if (!ok) log.push(`IOS-L3 ${SRC} sha=${sha} mutation anchors MISSING`);
const live = run(source);
const liveGreen = Object.values(live).every(Boolean);
log.push(`IOS-L3-LIVE ${SRC} sha=${sha} pins=${Object.values(live).filter(Boolean).length}/${Object.keys(live).length} verdict=${liveGreen ? 'GREEN' : 'RED'}`
  + (liveGreen ? '' : ` failing=${Object.keys(live).filter((k) => !live[k]).join(',')}`));
ok &&= liveGreen;
for (const [id, mutate, target] of MUTATIONS) {
  const mutated = mutate(source);
  const res = mutated === source ? null : run(mutated);               // an unchanged string proves nothing
  const red = res ? !res[target] : false;
  log.push(`${id} ${SRC} sha=${sha} in-memory target=${target} verdict=${red ? 'RED' : 'GREEN'} restored=n/a(read-only)`);
  ok &&= red;
}
if (process.argv.includes('--record')) fs.appendFileSync(path.join(HERE, 'ios-callsite-pins.out.txt'), log.join('\n') + '\n');
console.log(log.join('\n'));
process.exit(ok ? 0 : 1);
