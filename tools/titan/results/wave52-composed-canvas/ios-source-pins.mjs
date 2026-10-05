#!/usr/bin/env node
// wave-52 lane L2 — EXECUTED replay of the iOS harness source-scan pins
// (apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift).
// That XCTest target needs a booted simulator, which lane rules forbid, so
// this script applies the SAME slicing (struct ComposedCaptureCanvas → struct
// DynamicCaptureHooks, `//` lines dropped) and the SAME six assertions to the
// live CaptureCanvas.swift — then to seven in-memory MUTATIONS of it, each of
// which must turn at least one assertion red. The source file is only read.
// Prints its verdicts; appends them to mutations.log ONLY with `--record`
// (re-running it must not dirty the evidence — skeptic should-fix 3); exit 1
// if any expectation fails.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const SRC = 'apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift';
const source = fs.readFileSync(path.join(ROOT, SRC), 'utf8');
const sha = crypto.createHash('sha256').update(source).digest('hex').slice(0, 16);

/** composedBodyCode(): the struct slice, code lines only (the XCTest helper). */
function bodyCode(src) {
  const start = 'struct ComposedCaptureCanvas: View {';
  const end = 'struct DynamicCaptureHooks: ViewModifier {';
  if (!src.includes(start) || !src.includes(end)) throw new Error('anchors missing');
  const region = src.split(start).slice(1).join(start).split(end)[0];
  return region.split('\n').filter((l) => !l.trim().startsWith('//')).join('\n');
}
const at = (hay, needle) => { const i = hay.indexOf(needle); return i < 0 ? null : i; };
const count = (hay, needle) => hay.split(needle).length - 1;

/** The seven XCTest functions, as named predicates (true = the test passes). */
const PINS = {
  testClipShapeIsAppliedExactlyOnceWithTheFrame: (c) =>
    count(c, '.clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))') === 1
    && !c.includes('IcbClipBand(frame: pad') && !c.includes('IcbClipBand(frame: resolvedPadding'),
  testClipPrecedesTheCanvasBackground: (c) => {
    const clip = at(c, '.clipShape(WPTCanvas.IcbClipBand'); const bg = at(c, '.background(canvasBackground)');
    return clip !== null && bg !== null && clip < bg;
  },
  testBothOverlayHalvesAreInsideTheClip: (c) => {
    const clip = at(c, '.clipShape(WPTCanvas.IcbClipBand');
    const behind = at(c, '.background(alignment: .topLeading)'); const above = at(c, '.overlay(alignment: .topLeading)');
    return [clip, behind, above].every((x) => x !== null) && behind < clip && above < clip
      && count(c, 'FixedHoistOverlay(components: paint.') === 2;
  },
  testFlowStackIsInsideTheClip: (c) => {
    const clip = at(c, '.clipShape(WPTCanvas.IcbClipBand');
    const pad = at(c, '.padding(EdgeInsets(top: pad.top + marginInset.top');
    return clip !== null && pad !== null && pad < clip;
  },
  testRc1StaticPositionRootPaintsAboveTheFlow: (c) => {
    const anchor = at(c, '.modifier(StaticPositionAnchor())'); const z = at(c, '.zIndex(1)');
    return anchor !== null && z !== null && anchor < z && count(c, '.zIndex(1)') === 1;
  },
  testRc1LiftIsGatedByTheWholeListRule: (c) => {
    const gate = at(c, 'if aboveFlow[idx] {'); const z = at(c, '.zIndex(1)');
    if (gate === null || z === null || !(gate < z)) return false;
    const elseAt = at(c.slice(gate), '} else {');
    return c.includes('let aboveFlow = UABlockMargin.composedRootsPaintingAboveFlow(split.flow)')
      && elseAt !== null && z - gate < elseAt && count(c, 'aboveFlow: aboveFlow)') === 3;
  },
  testSplitReadsTheOneOwnerBodyMargin: (c) =>
    c.includes('FixedHoist.split(roots: UABlockMargin.withCanvasOwnedBodyMargin(')
    && c.includes('UABlockMargin.canvasBodyMargin(document.components)')
    && c.includes('.padding(EdgeInsets(top: pad.top + marginInset.top,')
    && c.includes('.map(UABlockMargin.withUaBlockMarginOnHoistedRoot))'),
};
const run = (src) => Object.fromEntries(Object.entries(PINS).map(([k, f]) => [k, f(bodyCode(src))]));

/** One in-memory mutation: [id, transform, the pin that must go red]. */
const clipLine = '        .clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))\n';
const OVERLAY_ABOVE = [
  '        .overlay(alignment: .topLeading) {',
  '            if !paint.above.isEmpty {',
  '                FixedHoistOverlay(components: paint.above,',
  '                                  canvasFrame: Self.padding)',
  '            }',
  '        }',
].join('\n') + '\n';
const MUTATIONS = [
  ['IOS-M1', (s) => s.replace(clipLine, '').replace('        .background(canvasBackground)\n', '        .background(canvasBackground)\n' + clipLine),
    'testClipPrecedesTheCanvasBackground'],
  ['IOS-M2', (s) => {
    // The positive-z overlay block, moved back AFTER the root clip (its wave-51 spot).
    const block = OVERLAY_ABOVE; const clipAt = '        .rootCanvasClip(document.components, frame: Self.padding)\n';
    return s.replace(block, '').replace(clipAt, clipAt + block);
  }, 'testBothOverlayHalvesAreInsideTheClip'],
  ['IOS-M3', (s) => s.replace('                        .zIndex(1)\n', ''), 'testRc1StaticPositionRootPaintsAboveTheFlow'],
  ['IOS-M4', (s) => s.replace('FixedHoist.split(roots: UABlockMargin.withCanvasOwnedBodyMargin(', 'FixedHoist.split(roots: ((')
    , 'testSplitReadsTheOneOwnerBodyMargin'],
  ['IOS-M5', (s) => s.replace('\n            .map(UABlockMargin.withUaBlockMarginOnHoistedRoot))', ')'),
    'testSplitReadsTheOneOwnerBodyMargin'],
  // T3 skeptic fix: the gate forced open, then the gate list reverted to the first cut.
  ['IOS-M6', (s) => s.replace('                if aboveFlow[idx] {\n', '                if true {\n'),
    'testRc1LiftIsGatedByTheWholeListRule'],
  ['IOS-M7', (s) => s.replace('let aboveFlow = UABlockMargin.composedRootsPaintingAboveFlow(split.flow)',
    'let aboveFlow = split.flow.map { FixedHoist.rendersInFlowAsStaticPosition($0) }'),
    'testRc1LiftIsGatedByTheWholeListRule'],
];

let ok = true; const log = [];
const live = run(source);
const liveGreen = Object.values(live).every(Boolean);
log.push(`IOS-LIVE ${SRC} sha=${sha} pins=${Object.values(live).filter(Boolean).length}/${Object.keys(live).length} verdict=${liveGreen ? 'GREEN' : 'RED'}`);
ok &&= liveGreen;
for (const [id, mutate, target] of MUTATIONS) {
  const mutated = mutate(source);
  const res = mutated === source ? null : run(mutated);              // an unchanged string proves nothing
  const red = res ? !res[target] : false;
  log.push(`${id} ${SRC} sha=${sha} in-memory target=${target} verdict=${red ? 'RED' : 'GREEN'} restored=n/a(read-only)`);
  ok &&= red;
}
if (process.argv.includes('--record')) fs.appendFileSync(path.join(HERE, 'mutations.log'), log.join('\n') + '\n');
console.log(log.join('\n'));
process.exit(ok ? 0 : 1);
