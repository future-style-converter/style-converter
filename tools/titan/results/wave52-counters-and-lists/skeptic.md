# Wave 52 · L6 counters-and-lists — skeptic review (executed repros)

Reviewer: L6 skeptic, 2026-10-05 18:35–19:05 CEST. Tree: campaign/wave52 @ 7d9c22a7 + every lane's uncommitted work.
Inputs read: PLAN.md §0/§2 L6/§3/§4/§5/§9, `_note.md` (ends `STATUS: COMPLETE` — lane complete), every file in this directory.
Read-only except (a) mutations restored sha256-byte-exact, (b) the lane's seam patches applied under the per-file lock
`tools/titan/runs/wave52-lock/<basename>` and restored with `git show HEAD:<path> > <path>`. No devices. Scratch builds/renders
happened in copies under the session scratchpad; nothing below depends on them surviving (numbers are copied here).

## Verdict

The lane's numbers reproduce: every pin, every mutation I re-ran, every census count, every seam `git apply --check`, both seam
pairs green under the lock, and the predicted pictures. ONE must-fix: the T2 pin (`BorderSideZeroTallBandTest`) does not pin
the paint path it names — reverting `sideGeometry`'s BOTTOM wiring to the pre-fix formula leaves all six tests green (executed).
Two should-fix (Compose seam-1/seam-3 wiring has no executable pin although the SeamReachabilityTest source-level idiom exists;
5 passing cssom `-invalid` web cells move under T3+T7 and are not named — I replayed them: plan = ref). Nits listed at the end.

## 1. Pins re-run (all green)

| suite | command | result | lane's figure |
|---|---|---|---|
| node | `node --test tools/titan/counter-style-bake.test.mjs tools/titan/counter-style-author.test.mjs` | 45/45 | 45/45 |
| web vitest | `(cd runtimes/web && npx vitest run tests/lists tests/renderer)` | 13 files, 143/143 | 143/143 |
| web-harness | `(cd apps/web-harness && npx vitest run tests/sdui)` | 22 files, 192/192 | 192/192 |
| Compose (no seam) | `:runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.lists.*' 'com.styleconverter.runtime.borders.*' '*MulticolFloatStrip*'` | 31 classes, 281/0 (JUnit XML 18:36:38; `MulticolFloatStripRefRowsTest` 4/4) | 281/0 |
| Swift (no seam, Catalyst, private DerivedData) | `-only-testing` KoreanHangulFormal/ListItemMarkerGate/ListMarkerAbsposItemRaster/ListMarkerEmptyItem/ListMarkerInFlowItemRaster/ListMarkerOutsideHang/ListMarker | 59/59, TEST SUCCEEDED | (43 at 17:10) |
| L12 hunk, isolated HEAD copy (git archive + 2 results JSON + node_modules symlink) | `node --test tools/titan/inject-wpt-block.test.mjs` before / after `git apply hunk-for-L12-1.patch` | 120/120 → 120/120 | 120/120 |
| L12 hunk on a COPY of L12's in-progress file | same | 130/130 → 130/130 | "applies, offset +18" |

## 2. Mutations re-executed independently (own helper, sha256 checked; all restores byte-exact)

| # | file · mutation | expected | observed | restore sha (prefix) |
|---|---|---|---|---|
| 1 | `ListStyleTypeApplier.ts` — inline form dropped (`&& !hasChildren && false`) | lane: 3 red | **3 red** (plan inline, armenian-008, cssom) | 19267176… = before |
| 2 | `NodeRenderer.ts` — `inheritedListStylePosition` not threaded to children (NOT a lane mutation) | wiring pinned? | **2 red** (armenian-008, cssom) — wiring IS pinned | f6d6cff4… = before |
| 3 | `counter-style-descriptors.mjs` — `pad` accepts a negative length | lane: 5 red | **5 red** (tests 2, 5, 8, 41, 45) | bee90743… = before |
| 4 | `BorderSideApplier.kt` — helper `max(inset, extent−inset)` → `extent−inset` | lane: 4 red | **4 red**, `6 tests completed, 4 failed` | ec0d87f9… = before |
| 5 | `BorderSideApplier.kt` — `sideGeometry` BOTTOM back to `size.height − width/2`, helper untouched (NOT a lane mutation) | must go red if T2 is pinned | **0 red — `BUILD SUCCESSFUL`** → the T2 pin is vacuous for the paint path (finding M1) | ec0d87f9… = before |

## 3. Census — re-derived with my OWN script (Python, below; never reads census.json, never imports the runtime rule)

| family | lane | skeptic | agree |
|---|---|---|---|
| docs / components / scored cells | 1435 / 10 708 / 4117 | 1435 / 10 708 / 4117 | yes |
| T1 korean-hangul-formal carriers | 1 comp / 1 test | 1 / 1 (counter-suffix `__0__3`; its two `<li>` carry NO markerText — so T1 really is the Android path) | yes |
| T2 parent definite size < child far border | 3 rows / 3 tests | 3 / 3 (multicol-003 3px, balancing-003 5px, fieldset-001 10px); deeper-ancestor chain: 0 | yes |
| T2 own size < own border ("extended") | 36 rows / 10 tests | 36 / 10 — and every one is inert: box-sizing-026 (BORDER_BOX 10px, 50px borders) Android green square = ref bbox (16,88)-(115,187) exactly; the rest are content-box (laid-out extent ≥ border) | yes |
| T3 markerText carriers | 194 / 26 | 194 / 26 | yes |
| T3 shape 1 (range-limited additive) | 44 / 7 | 44 / 7 | yes |
| T3 shipped form inline / string | 39 / 5 | 39 / 5 (string: counter-suffix hebrew ×4 outside, marker-text-matches-armenian ×1 with child) | yes |
| T3 inline-form carriers that also carry a `pseudos.marker` bag (would lose the marker: `list-style-type:none` + no synthetic span) | — | **0** | n/a |
| T3 shape 2 on today's wire | 0 | 0 | yes |
| T5 outside `<li>` under ol/ul/menu/dir, marker non-empty, not abs/fixed | 100 / 19 | 100 / 19; 38 native cells, 17 native P, 9 thin (<0.98) — identical list | yes |
| T5 / T3 / T2 tests on the watchlist (plan watchlist ∪ watchlist-additions, scorer substring rule) | — | 0 unwatched; `WATCH=watchlist-additions.txt watchlist-check.mjs` → `unmatched 0` | yes |
| T7 sources with `@counter-style` | 26 | 26 (19 with `<script`) | yes |
| T7 new bake outcome over those 26 | baked 12 / bailed 11 / skipped 3 | 12 / 11 / 3 (7 `requires-script-mutation`, 3 `dynamic counters`, 1 `<script>`) | yes |
| T7 **differential** HEAD bake vs tree bake over ALL 1434 fixtures with a source | "8 newly stamped" | **8 stamp changes, all cssom `-invalid`**, strings = refs (additive A./B./C., fallback A./B./iii., name A–C + X–Z, negative (3).(2).(1)., pad 001–003., prefix-suffix (A)(B)(C), range A./B./3., symbols A./B./C.); 0 other docs change a stamp; `_wpt` changes only `+requires-script-mutation` on the 7 valid setters (informational: `applyNaScoreGate` reads lossyReasons only for SCORE_EXCLUDED tags) | yes |
| T7 native empty inside `<li>` | 12 now (1 test); 63 / 16 tests after F-E | 12 (name-case-sensitivity); 51 placeholder pairs in 15 tests → 63 / 16 | yes |

## 4. Seam patches

`git apply --check` (scratch copies of the HEAD files): seam-3 on HEAD ok; seam-1 on HEAD+3 ok (also applies on bare HEAD);
seam-4 on HEAD ok; seam-2 on HEAD **fails** (as documented — it needs seam-4) and on HEAD+4 ok; hunk-for-L12-1 on HEAD ok and on the
live L12 file ok. Integration check: all 12 other-lane ComponentRenderer.{kt,swift} seam patches (L11 1/2, L3 1/2, L10 1, L9 1/2,
L4 1, L8 1/2/3/4) applied first, then L6's four still apply clean — no overlap with RenderListItemMarker / markerPlacement.

Under the lock (sha at HEAD verified before, restored sha equal, lock released):
- `ComponentRenderer.kt` 18:45:35–18:45:42: seam-3 + seam-1 applied, `lists.* borders.sides.* core.renderer.*` → 369 tests, 3 failed:
  `LineClampUnderPreWave39Test` ×1 + `PlaceholderOverflowMarkerTest` ×2. Attribution run with the seam file at HEAD: the SAME 3 fail
  (22 tests, 3 failed) → L9's in-flight line-clamp work, not L6.
- `ComponentRenderer.swift` 18:46:08–18:47:06: seam-4 + seam-2 applied (+ their two raster test files, removed after), 11 suites /
  86 tests, TEST SUCCEEDED; `ListMarkerEmptyItemRasterTests bands: (16,27) (32,43) (48,59)`, `ListMarkerOutsideHangRasterTests row-1
  groups: (48,51) (56,57) (64,85)` — identical to seam-2/4.verify.log.

## 5. Predicted-flip cells — wave51-fix PNGs vs the frozen ref (`white-black-ink-font-lh-imgpad-htmlpins`)

L12-B's re-frozen `…-htmlpins-rootbg-uamargin` refs are pixel-identical to the frozen ones for all five cells (0 px differ).
- **armenian-008 web f 0.9435 → P (HIGH)**: run PNG has tofu rows (5 ink bands); ref 6 bands (`10000.` / `10000` wrapped). Lane's
  `replay/…008.plan.png` vs frozen ref: 22 px differ, bands identical. Plausible. Natives: rows measured ios 154 px / android 155 px
  for `10000. 10000`, row 3 143 px vs ref 146 px — the adjudication-A hunk's measurement is accurate.
- **cssom pad / prefix-suffix `-invalid` web (MED-HIGH, needs L5 F-E)**: run PNGs "1. 2. 3." at 100-px pitch; ref 001./(A) at 20 px.
  `replay/*.plan.png` vs ref: **0 px differ**. Plausible.
- **cssom pad / prefix-suffix / negative natives ×6 (MED-LOW)**: Catalyst raster under seam-4: 3 rows at 16-px pitch vs ref 20 →
  rows 2/3 off by 4/8 px. The MED-LOW label is honest; Android pitch unmeasured (no JVM layout).
- **counter-suffix ios f 0.9285 → P (MED)**: my own scratch replay (copy of runtimes/swiftui + Package.swift, verbatim wave51-fix
  per-test IR decoded through `IRDocument`, rendered A/B without/with seam-4+seam-2; both copies also carried L10's in-flight flex hunk,
  which is identical in A and B): rows 1–8 markers move from x 65–80 to x 46–58 (decimal/hebrew; ref 46–58), 29–49 (CJK; ref 33–52),
  42–57 (korean; ref 42–57); text from x 82/83 to 64/65 (ref 64/65); rows 9–12 (RTL, no markers upstream) are unchanged by the hang
  (no marker appears on the wrong side). Same replay on css-lists/counter-list-item-2: marker 39–49 (ref 38–50), text 56–63 (ref 57–64).
  Plausible.
- T2 (no flip): Android orange band rows 158–160 / 166–170 vs ref, iOS and web 161–163 / 171–175 — exactly w px high, as stated.
- NOT in the lane's list: the 5 other cssom `-invalid` web cells (additive-symbols / fallback / name / range / symbols, today
  P 0.9884 / 0.9879 / 0.9755 DEG / 0.9882 / 0.988) also take T3's inline form after T7 stamps them. My replay (lane's png-replay.mjs
  copied, CASES = those 5 with the stamps from my differential): plan bands == ref bands, ssim.js 1.0 on all five (finding S2).

## 6. Ownership, rules

- Every changed path carrying an L6 marker is in the "own:" list or is a NEW file in the lane's own subtree no other lane owns
  (`lists/ListMarkerOutsideHang.{kt,swift}`, `lists/ListMarkerEmptyItem.{kt,swift}`, `counter-style-author.mjs`,
  `counter-style-descriptors.mjs` + tests). `inject-wpt-block.mjs` (L12) carries NO armenian-008 add (hunk delivered, not applied).
  Both seam files at HEAD. No foreign edit.
- New files ≤ 200 lines (max 200: counter-style-descriptors.mjs). No `backdrop-filter` reference, no WPT test-name literal in any
  lane code path (comments stripped). Device A/B hash sentence present in seam-1/seam-2 headers, both ListMarkerOutsideHang headers
  and note §6.

## 7. Findings

**M1 (must-fix) — T2 pin is vacuous for the paint path.** `BorderSideZeroTallBandTest` only calls the helper
`innerEdgeStrokeCentre`; mutation 5 (sideGeometry BOTTOM reverted, helper kept) → all 6 green. The plan's verification line asks for
"T2 paint test (100×0 box, border-bottom:3px solid → ink rows [0,3), none at y<0)". Cheap fix: lift `sideGeometry`/`doubleGeom`
into an internal pure function of `(side, width|inset, Size)` called by the DrawScope extension, and pin THAT for BOTTOM/END/double
(the existing six assertions move onto it); re-run mutation 5 → must go red.

**S1 (should-fix) — Compose seam wiring has no executable pin.** seam-3 (lands with L5 F-E, not a device A/B) and seam-1 add no
test, and no existing JVM test changes outcome with them (with: 369 run, the only 3 failures are L9's, which fail identically with the
seam file at HEAD; without: the lane-pin run is 281/0). The repo's own idiom for this is source-level
(`core/renderer/SeamReachabilityTest.kt`: reachability BFS from ScreenshotCaptureScreen + "seams still spliced" `contains` checks).
Ship a source pin inside seam-3/seam-1 the way seam-4/seam-2 ship their raster pins (ListMarkerEmptyItem.kt and
ListMarkerOutsideHang.kt are otherwise dead modules until the orchestrator applies the patches — the wave-49 A2 class).

**S2 (should-fix) — 5 passing web cells move unnamed.** cssom additive-symbols / fallback / name / range / symbols `-invalid` web
take the inline form once T7 stamps them; note §7 names only pad / prefix-suffix / negative. Measured here (replay = ref, 1.0); add them
to §7 as picture-correctness movers (they are already covered by the `css-counter-styles/cssom/` watch line).

Nits: (N1) the shipped headers of ListMarkerOutsideHang.kt/.swift and both `hangsOutside` docs still say "127 items in 26 tests";
the lane's own measured number is 100 / 19. (N2) `bakedMarkerListStyleType`'s `<string>` guard (`startsWith('"')`) never fires on
the real wire: the converter strips the quotes (`ListStyleType` values `"marker "` ×14, `"2. "` ×2, `"::marker "` ×2 in wave51-fix),
and CSS-wide keywords (`inherit` / `initial` / `unset` / `revert`) are not in PREDEFINED either — both would be read as AUTHOR names
if a bake ever stamped such an item; 0 carriers today. (N3) existing files grown further past the ~300-line split rule:
BorderSideApplier.kt 542 (+58), NodeRenderer.ts 432 (+58), counter-style-bake.mjs 447. (N4) Swift
`ListMarkerOutsideHangLayout` `guard subviews.count == 2 else { return .zero }` silently collapses the item if the contract ever
breaks (unreachable today: ComponentHost and the marker Text are single subviews) — log it. (N5) the "own:" list should be amended
in the PR record with the six new files. (N6) counter-style-author/descriptors carry runs of 14–15 uncommented code lines.

## 8. My census script (verbatim, for re-derivation)

```python
#!/usr/bin/env python3
# L6 skeptic — independent census over wave51-fix per-test IR (does NOT import the lane's census.mjs,
# census.json, or the runtime TS rule; every predicate is re-stated from the spec / runtime source).
import json, glob, os, re, sys, collections
ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
RUN = f'{ROOT}/tools/titan/runs/wave51-fix/sections'

# ---- cells (verdict = wptPass, scored = numeric ssim and not scoreExcluded) ----
cells = {}
for man in glob.glob(f'{RUN}/*/manifest.json'):
    sec = man.split('/')[-2]
    m = json.load(open(man))
    for key, r in m['wpt']['results'].items():
        for plat, d in (r.get('browserRef') or {}).get('diffs', {}).items():
            p = plat.replace('-ref', '')
            if isinstance(d, dict) and isinstance(d.get('ssim'), (int, float)) and not d.get('scoreExcluded'):
                cells[(key, p)] = ('P' if d.get('wptPass') is True else 'f', d['ssim'])
def cells_of(key, plats=('web', 'ios', 'android')):
    return {p: cells.get((key, p)) for p in plats if (key, p) in cells}

docs = []
for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
    stem = os.path.basename(f)[:-5]
    parts = stem[len('wpt__'):].split('__')
    key = 'css/' + '/'.join(parts) + '.html'
    comps = json.load(open(f)).get('components', [])
    docs.append((key, comps))
ncomp = sum(len(c) for _, c in docs)

def prop(c, t):
    for p in c.get('properties') or []:
        if p.get('type') == t: return p.get('data')
    return None
def has(c, t): return any(p.get('type') == t for p in c.get('properties') or [])
def px(v):
    if isinstance(v, dict):
        if isinstance(v.get('px'), (int, float)): return v['px']
    if isinstance(v, (int, float)): return v
    return None
def tag(c): return ((c.get('meta') or {}).get('sourceTag') or '').lower()

out = collections.OrderedDict()
out['docs'] = len(docs); out['components'] = ncomp; out['scoredCells'] = len(cells)

# ---- T1 ----
t1 = [(k, c['name']) for k, cs in docs for c in cs if isinstance(prop(c, 'ListStyleType'), str)
      and prop(c, 'ListStyleType').lower().replace('_', '-') == 'korean-hangul-formal']
out['T1'] = {'components': len(t1), 'tests': sorted({k for k, _ in t1})}

# ---- T2: boxes whose Compose laid-out extent can fall below their own far-edge stroke ----
def border_w(c, side):
    st = prop(c, f'Border{side}Style')
    st = (st if isinstance(st, str) else (st or {}).get('keyword', '') if isinstance(st, dict) else '')
    if not st or st.upper() in ('NONE', 'HIDDEN'): return 0
    w = px(prop(c, f'Border{side}Width'))
    return 3 if w is None else w     # `medium`
t2_parent, t2_anc, t2_own = [], [], []
for k, cs in docs:
    ids = {c['id']: c for c in cs}
    for c in cs:
        bb, br = border_w(c, 'Bottom'), border_w(c, 'Right')
        if not (bb or br): continue
        par = ids.get((c.get('slot') or {}).get('parent'))
        # own definite size below own far border (border-box clamp)
        for side, w, P in (('BOTTOM', bb, 'Height'), ('END', br, 'Width')):
            if not w: continue
            for PP in (P, 'Max' + P):
                v = px(prop(c, PP))
                if v is not None and v < w: t2_own.append((k, c['name'], side, PP, v, w))
        # parent / any ancestor definite size below the border, child without own size on that axis
        for side, w, P in (('BOTTOM', bb, 'Height'), ('END', br, 'Width')):
            if not w or has(c, P): continue
            n, depth = par, 1
            while n is not None:
                vs = [px(prop(n, PP)) for PP in (P, 'Max' + P)]
                vs = [v for v in vs if v is not None]
                if vs and min(vs) < w:
                    (t2_parent if depth == 1 else t2_anc).append((k, c['name'], side, depth, min(vs), w)); break
                if has(n, P): break            # an own definite size re-bases the chain
                n = ids.get((n.get('slot') or {}).get('parent')); depth += 1
def t2sum(rows):
    ts = sorted({r[0] for r in rows})
    return {'rows': len(rows), 'tests': len(ts), 'android': {t: cells.get((t, 'android')) for t in ts}}
out['T2_parent'] = t2sum(t2_parent); out['T2_parent_rows'] = t2_parent
out['T2_deeperAncestor'] = t2sum(t2_anc); out['T2_deeperAncestor_rows'] = t2_anc
out['T2_own'] = t2sum(t2_own)

# ---- T3: web baked-marker shapes (re-stated from css-counter-styles-3 §6 / §7 names) ----
RANGE = {'armenian', 'upper-armenian', 'lower-armenian', 'georgian', 'hebrew'}
PREDEF = set('''none disc circle square disclosure-open disclosure-closed decimal decimal-leading-zero arabic-indic
armenian upper-armenian lower-armenian bengali cambodian khmer cjk-decimal devanagari georgian gujarati gurmukhi hebrew
kannada lao malayalam mongolian myanmar oriya persian lower-roman upper-roman tamil telugu thai tibetan lower-alpha
lower-latin upper-alpha upper-latin lower-greek hiragana hiragana-iroha katakana katakana-iroha japanese-informal
japanese-formal korean-hangul-formal korean-hanja-informal korean-hanja-formal simp-chinese-informal simp-chinese-formal
trad-chinese-informal trad-chinese-formal cjk-earthly-branch cjk-heavenly-stem ethiopic-numeric'''.split())
def up(c, ids, t):
    n = c
    while n is not None:
        v = prop(n, t)
        if isinstance(v, str): return v.lower().replace('_', '-')
        n = ids.get((n.get('slot') or {}).get('parent'))
    return None
carriers, s1, s2, inline, string, inline_with_marker_pseudo = [], [], [], [], [], []
for k, cs in docs:
    ids = {c['id']: c for c in cs}
    parents = {(c.get('slot') or {}).get('parent') for c in cs}
    for c in cs:
        mt = (c.get('meta') or {}).get('markerText')
        if not isinstance(mt, str) or not mt: continue
        carriers.append(k)
        t = up(c, ids, 'ListStyleType')
        if not t or t.startswith(('"', "'", 'symbols(')): continue
        if t in RANGE: s1.append((k, c['name']))
        elif t not in PREDEF: s2.append((k, c['name'], t))
        else: continue
        pos = up(c, ids, 'ListStylePosition')
        kids = c['id'] in parents
        if pos == 'inside' and not kids:
            inline.append((k, c['name']))
            if (c.get('pseudos') or {}).get('marker') is not None: inline_with_marker_pseudo.append((k, c['name']))
        else: string.append((k, c['name'], pos, kids))
out['T3'] = {'carriers': len(carriers), 'carrierTests': len(set(carriers)),
             'shape1': len(s1), 'shape1Tests': sorted({x[0] for x in s1}), 'shape2_today': len(s2),
             'inline': len(inline), 'string': len(string), 'stringRows': string,
             'inlineWithMarkerPseudo': inline_with_marker_pseudo,
             'cells': {t: cells_of(t) for t in sorted({x[0] for x in s1})}}

# ---- T5: <li> under ol/ul/menu/dir (Compose uaMarkerDefault), marker non-empty, position resolves OUTSIDE ----
outside, outside_nonli_marker = [], []
for k, cs in docs:
    ids = {c['id']: c for c in cs}
    for c in cs:
        par = ids.get((c.get('slot') or {}).get('parent'))
        if par is None or tag(par) not in ('ol', 'ul', 'menu', 'dir'): continue
        pos = up(c, ids, 'ListStylePosition') or 'outside'
        if pos != 'outside': continue
        typ = up(c, ids, 'ListStyleType')
        mt = (c.get('meta') or {}).get('markerText')
        if tag(c) != 'li':
            if isinstance(mt, str) and mt: outside_nonli_marker.append((k, c['name']))
            continue
        if isinstance(mt, str) and mt == '': continue
        if mt is None and typ == 'none': continue
        if str(prop(c, 'Position') or '').upper() in ('ABSOLUTE', 'FIXED'): continue
        outside.append((k, c['name']))
ts = sorted({x[0] for x in outside})
nat = {(t, p): cells.get((t, p)) for t in ts for p in ('ios', 'android') if (t, p) in cells}
out['T5'] = {'items': len(outside), 'tests': len(ts), 'nonLiChildWithBakedMarker': outside_nonli_marker,
             'nativeCells': len(nat), 'nativeP': sum(1 for v in nat.values() if v[0] == 'P'),
             'thinNativeP_lt_0.98': sorted(f'{t} {p} {v[1]}' for (t, p), v in nat.items() if v[0] == 'P' and v[1] < 0.98),
             'tests_list': ts}

# ---- T7: authored sources containing @counter-style ----
t7 = []
for k, _ in docs:
    src = f'{ROOT}/tools/wpt/' + k
    if os.path.exists(src) and re.search(r'@counter-style', open(src, encoding='utf-8', errors='replace').read(), re.I):
        t7.append(k)
out['T7'] = {'tests': len(t7), 'withScript': sum(1 for k in t7 if '<script' in open(f'{ROOT}/tools/wpt/' + k, encoding='utf-8', errors='replace').read().lower()),
             'cssom': sorted(k for k in t7 if '/cssom/' in k)}

# ---- T7 native: empty inside <li> (no text, no children, no pseudos, no declared block size) ----
DBS = {'Height', 'MinHeight', 'BlockSize', 'MinBlockSize'}
empty_now, placeholder = [], []
for k, cs in docs:
    ids = {c['id']: c for c in cs}
    parents = {(c.get('slot') or {}).get('parent') for c in cs}
    for c in cs:
        par = ids.get((c.get('slot') or {}).get('parent'))
        if tag(c) != 'li' or par is None or tag(par) not in ('ol', 'ul', 'menu', 'dir'): continue
        if up(c, ids, 'ListStylePosition') != 'inside': continue
        if c.get('text') or c['id'] in parents or c.get('pseudos'): continue
        if any(p.get('type') in DBS for p in c.get('properties') or []):
            if {p['type'] for p in c['properties']} == {'Width', 'Height'}: placeholder.append((k, c['name']))
            continue
        empty_now.append((k, c['name']))
out['T7native'] = {'emptyNow': len(empty_now), 'emptyNowTests': sorted({x[0] for x in empty_now}),
                   'placeholderPairInside': len(placeholder), 'placeholderTests': len({x[0] for x in placeholder})}
json.dump(out, open(sys.argv[1], 'w'), indent=1, default=str)
for k, v in out.items():
    if k.endswith('_rows') or k == 'T5': continue
    print(k, json.dumps(v, default=str)[:900])
print('T5', {k: v for k, v in out['T5'].items() if k != 'tests_list'})
```

T7 differential (node, HEAD bake extracted with `git show HEAD:tools/titan/counter-style-{bake,table}.mjs` into a scratch dir):

```js
// L6 skeptic: differential of HEAD's counter-style bake vs the tree's, over every extractor fixture.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const S = path.dirname(new URL(import.meta.url).pathname);
const oldB = await import(path.join(S, 'head-bake/counter-style-bake.mjs'));
const newB = await import(path.join(ROOT, 'tools/titan/counter-style-bake.mjs'));
const FX = path.join(ROOT, 'fixtures/wpt');
const stamps = (fx) => { const out = []; const walk = (n, p) => { if (!n || typeof n !== 'object') return; if (typeof n._markerText === 'string') out.push(`${p}=${n._markerText}`); for (const [k, c] of Object.entries(n.children ?? {})) walk(c, p + '/' + k); }; for (const [k, r] of Object.entries(fx.components ?? {})) walk(r, k); return out; };
let n = 0, changed = [], statusChange = [], wptChange = [];
for (const sec of fs.readdirSync(FX)) {
  const dir = path.join(FX, sec); if (!fs.statSync(dir).isDirectory()) continue;
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith('.json') || f.endsWith('__ref.json')) continue;
    const src = path.join(ROOT, 'tools/wpt/css', sec, f.replace(/\.json$/, '').split('__').join('/') + '.html');
    const html = fs.existsSync(src) ? fs.readFileSync(src, 'utf8') : null;
    if (html === null) continue;
    n++;
    const raw = fs.readFileSync(path.join(dir, f), 'utf8');
    const a = JSON.parse(raw), b = JSON.parse(raw);
    const ra = oldB.bakeCounterStyles(a, html), rb = newB.bakeCounterStyles(b, html);
    const sa = stamps(a).join(' | '), sb = stamps(b).join(' | ');
    const key = `${sec}/${f.replace(/\.json$/, '').split('__').join('/')}`;
    if (sa !== sb) changed.push({ key, old: sa, new: sb });
    if (ra.status !== rb.status || ra.reason !== rb.reason) statusChange.push({ key, old: `${ra.status}:${ra.reason ?? ''}`, new: `${rb.status}:${rb.reason ?? ''}` });
    if (JSON.stringify(a._wpt) !== JSON.stringify(b._wpt)) wptChange.push({ key, old: a._wpt?.lossyReasons, new: b._wpt?.lossyReasons, oldBaked: a._wpt?.counterStyleBaked, newBaked: b._wpt?.counterStyleBaked });
  }
}
console.log(JSON.stringify({ fixturesWithSource: n, stampChanged: changed.length, changed, statusChange, wptChange }, null, 1));
```

## Re-verify

Reviewer: L6 re-verify skeptic, 2026-10-05 19:07–19:20 CEST. Tree: campaign/wave52 @ 7d9c22a7 + every lane's uncommitted work.
Note re-read: ends `STATUS: COMPLETE`; §11 records the fix pass. Read-only except (a) mutations of `BorderSideApplier.kt` restored
sha256-byte-exact, (b) one TEMPORARY probe test file added to the shared test tree and removed (absent before and after; its compiled
classes were purged by the next incremental compile — `find runtimes/compose/build -name 'ZzL6SkepticProbeTest*'` → nothing),
(c) seam-3 + seam-1 applied under the per-file lock and restored with `git show HEAD:<path> > <path>`. Own helper (`mut.py`, scratch):
exact-substring replace (must match once), focused Gradle run, only JUnit XML newer than the run start counted, restore, re-sha.

### M1 — CONFIRMED FIXED (executed)

`BorderSideApplier.kt` sha256 `9f806563…` (= the note's §11 sha). `sideGeometry(side, width, box: Size)` and
`doubleGeom(side, inset, box: Size)` are `internal` pure functions; `paintSide`, `drawDouble` (×2 call sites) and
`drawGrooveOrRidge` (×3) pass `DrawScope.size`. `BorderSideZeroTallBandTest` (175 lines) now rasterises the line returned by
`sideGeometry` / `doubleGeom`.

| run | what | result | restore |
|---|---|---|---|
| base (no mutation) | `lists.* borders.* *MulticolFloatStrip*` | 31 classes, **283 / 0** (= note §11) | — |
| **mutation 5** (the M1 repro, re-executed) | `sideGeometry` BOTTOM both offsets → `box.height - width / 2`, helper untouched | **3 red**: 3 px → `[-3,-2,-1]`, 5 px → `[-5..-1]`, 10 px → `[-10..-1]` (8 run, 3 failed) | `9f806563…` byte-exact |
| mutation B (not a lane mutation) | `doubleGeom` END → `box.width - inset` | **1 red**: double pin `expected [0, 2] was [-1, -3]` (the END half of that pin) | `9f806563…` byte-exact |
| post | `borders.sides.*` after the probe removal | 8 / 0 | — |

Mutation 5, which stayed green before the fix pass, is now red. M1 is closed. Limit (stated, not a defect): the step from
`paintSide` to `sideGeometry(…, size)` has no JVM pin, because the JVM has no draw surface. It is a single argument.

### Regression hunt — NEW must-fix R1: `doubleGeom` MIRRORS the inner line instead of TRANSLATING the band

`innerEdgeStrokeCentre(extent, inset) = max(inset, extent − inset)` is correct for the single stroke (inset = w/2:
`max(w/2, extent − w/2) ≡ max(extent, w) − w/2`). But `doubleGeom` feeds it insets up to `w − line/2 = 5w/6` (double) and
`3w/4` (groove/ridge inner half-band). Whenever `extent < 2·inset`, the clamp MIRRORS that line about `inset` instead of leaving
HEAD's `extent − inset`. So boxes 1 stroke tall or taller change, and the code comment's claim "unchanged whenever the box is at least
one stroke tall (every committed baseline …)" is false for the two-line paths. Executed probe (temporary
`borders/sides/ZzL6SkepticProbeTest.kt`, verbatim below. Expected values are HEAD's `size.height − inset` arithmetic, or the spec
band order for the 0-tall groove):

| probe | box · border | HEAD / spec rows | tree rows | |
|---|---|---|---|---|
| p1 | 100×3 · `border-bottom: 3px double` (an empty separator div in composed-WPT mode, where the 30dp floor is OFF) | {0, 2} | **{2}** — one line, no gap | RED |
| p2 | 100×6 · 6px double | {0,1,4,5} | **{4,5}** | RED |
| p3 | 100×9 · 6px double (extent < 5w/3 = 10) | {3,4,7,8} | **{4,5,7,8}** (inner line 1 px low) | RED |
| p4 | 3×40 · `border-right: 3px double` | cols {0, 2} | **{2}** | RED |
| p5 | 100×4 · 4px groove, inner half | {0,1} | **{2,3}** — drawn over the outer half | RED |
| p6 | 100×0 · 4px groove, OUTER half (spec: border edge = y w → outer half [w/2, w)) | {2,3} | **{0,1}** — shades inverted; `BorderSideZeroTallBandTest` "a groove border on a 0-tall box…" PINS this inverted order | RED |
| p7 | 100×2 · 3px double (0 < extent < w) | {0, 2} | **{1, 2}** — no gap | RED |
| c1 | 100×50 · 3px double (control) | {47, 49} | {47, 49} | green |
| c2 | 100×0 · 3px double (the T2 shape) | {0, 2} | {0, 2} | green |

Result: 9 run, 7 failed (p1–p7), both controls green. p1–p5 render correctly at HEAD and wrong in the tree. That is a regression this lane introduced. Exposure,
from my OWN census (`two_line_census.py`, verbatim below; it never reads census.json): wave51-fix has 53 two-line far-edge
carriers in 10 tests (DOUBLE w≥3 / GROOVE / RIDGE w≥2 on bottom/right). **0** have a known extent below 2·inset_max. 5 carry text
(border-color-currentcolor `__3`/`__4` ridge/groove 5 px; border-conflict-resolution `__1__1__2` ridge 5 px), so their extent is at least one line box,
far above the 7.5 px threshold. No parent definite size falls below the threshold. In the non-WPT fixtures: 44 carriers, max width 14 px. The
30dp placeholder floor (on outside composed WPT) keeps every extent above 5·14/3 = 23.3. **The gate is unaffected (0 cells). The product runtime still paints
a wrong picture** for a reachable input: a short or empty element with a one-sided double, groove or ridge bottom/end border, or any `height:0` box with
groove/ridge.

Fix (cheap, gate-neutral): translate the band instead of mirroring it. Use far-edge centre = `max(extent, bandWidth) − inset`, and pass the
side's full `width` into `doubleGeom` from `drawDouble` and `drawGrooveOrRidge`. For the single stroke this is the same function
as today, so every T2 single-stroke pin and both measured cells (multicol-003 / balancing-003) are unchanged. Flip the groove pin to
outer {2,3} / inner {0,1}. Add p1/p3/p4/p5 as pins. Re-run a mutation that restores `max(inset, extent − inset)` in `doubleGeom` and confirm it goes
red. Correct the "unchanged whenever the box is at least one stroke tall" sentence (BorderSideApplier.kt `innerEdgeStrokeCentre` doc) or scope it to the single stroke.

### Other re-checks (executed)

- **T2 predicted picture** (pure-python PNG decode, orange rows in x 235–334): multicol-003 android 158–160, ios 161–163, frozen ref
  (`white-black-ink-font-lh-imgpad-htmlpins`) 161–163. balancing-003 android 166–170, ios 171–175, ref 171–175. Android is exactly w px
  high, so the +w single-stroke clamp lands on the ref rows. Plausible. R1 does not touch these solid cells.
- **Seams**: none of seam-1..4 touches `BorderSideApplier.kt` (grep 0). `git apply --check` in a scratch copy of HEAD: seam-3 ok, seam-4 ok,
  seam-2 on HEAD fails (documented), seam-1 on HEAD+3 ok, seam-2 on HEAD+4 ok. **Under the lock** (`wave52-lock/ComponentRenderer.kt`,
  19:14:52–19:14:56; sha before = HEAD `c4369165…`; restored `c4369165…` equal; lock released): seam-3 + seam-1, run `lists.* borders.*
  core.renderer.*`. `compileDebugKotlin` re-ran; 59 classes / 504 tests / 3 failed = `LineClampUnderPreWave39Test` ×1 +
  `PlaceholderOverflowMarkerTest` ×2. The attribution run with the file at HEAD gave the SAME 3 / 504, so these are L9's in-flight work, not L6's.
- **Other pins**: node counter-style 45/45; web `tests/lists tests/renderer` 13 files 143/143. Swift: the fix pass touched comments only
  (the `ListMarkerRow.swift` diff vs HEAD has no non-comment line except the pre-existing `hangsOutside` body). `xcrun swiftc -parse` ok on
  ListMarkerRow / ListMarkerOutsideHang / ListMarkerEmptyItem. I did not run xcodebuild in this pass.
- **N1**: the "100 `<li>` items in 19 tests" text is present in ListMarkerOutsideHang.kt:47, ListMarkerRow.kt:252 and ListMarkerRow.swift:225. "127" is gone.
- **Ownership**: the changed L6 paths are unchanged in kind since the first review. `inject-wpt-block.mjs` / `.test.mjs` are modified by L12. Their
  diffs carry no armenian / `css3-counter-styles-008` line, so the hunk was delivered and not applied. Both seam files are at HEAD. I found no foreign edit.
- **Rules**: the new test file has 175 lines and a comment on every block. It contains no WPT test-name literal in a code path. `BorderSideApplier.kt` has 565 lines (HEAD 498,
  +67), which deepens N3. S1 (no executable pin for the Compose seam-1/seam-3 wiring) is still open, as the note §11 states.

### Re-verify verdict

M1: FIXED (mutation 5 is now 3 red, executed). New must-fix R1 (doubleGeom mirror) has 0 gate cells and is a cheap fix. S1 is still open (should-fix).

#### Probe test (verbatim; it lived in the tree for about 10 s and was then removed)

```kotlin
package com.styleconverter.runtime.borders.sides

// TEMPORARY re-verify probe (L6 skeptic) — expected values are HEAD's arithmetic
// (`size.height - inset`) for boxes at least one stroke tall, and the spec band order for 0-tall groove.
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.styleconverter.runtime.borders.sides.BorderSideApplier.Side
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

class ZzL6SkepticProbeTest {
    private fun ink(a: Offset, w: Float, h: Boolean): Set<Int> {
        val c = if (h) a.y else a.x
        return (floor(c - w / 2f).toInt()..(ceil(c + w / 2f).toInt() - 1)).toSet()
    }
    private fun dbl(s: Side, w: Float, box: Size): Set<Int> {
        val line = w / 3f; val h = s == Side.TOP || s == Side.BOTTOM
        return listOf(line / 2f, w - line / 2f).flatMap { o -> ink(BorderSideApplier.doubleGeom(s, o, box).first, line, h) }.toSet()
    }
    private fun groove(s: Side, w: Float, box: Size): Pair<Set<Int>, Set<Int>> {
        val half = w / 2f; val h = s == Side.TOP || s == Side.BOTTOM
        return ink(BorderSideApplier.doubleGeom(s, half / 2f, box).first, half, h) to
            ink(BorderSideApplier.doubleGeom(s, half / 2f + half, box).first, half, h)
    }
    @Test fun p1_double3_on_100x3_keeps_HEAD_rows_0_and_2() = assertEquals(setOf(0, 2), dbl(Side.BOTTOM, 3f, Size(100f, 3f)))
    @Test fun p2_double6_on_100x6_keeps_HEAD_rows() = assertEquals(setOf(0, 1, 4, 5), dbl(Side.BOTTOM, 6f, Size(100f, 6f)))
    @Test fun p3_double6_on_100x9_keeps_HEAD_rows() = assertEquals(setOf(3, 4, 7, 8), dbl(Side.BOTTOM, 6f, Size(100f, 9f)))
    @Test fun p4_double3_end_on_3x40_keeps_HEAD_cols() = assertEquals(setOf(0, 2), dbl(Side.END, 3f, Size(3f, 40f)))
    @Test fun p5_groove4_on_100x4_inner_half_HEAD_rows_0_1() = assertEquals(setOf(0, 1), groove(Side.BOTTOM, 4f, Size(100f, 4f)).second)
    @Test fun p6_groove4_on_0tall_outer_half_at_far_edge_rows_2_3() = assertEquals(setOf(2, 3), groove(Side.BOTTOM, 4f, Size(100f, 0f)).first)
    @Test fun p7_double3_on_0tall_partial_extent2_spec_rows_0_and_2() = assertEquals(setOf(0, 2), dbl(Side.BOTTOM, 3f, Size(100f, 2f)))
    @Test fun c1_control_double3_on_100x50_HEAD_rows_47_49() = assertEquals(setOf(47, 49), dbl(Side.BOTTOM, 3f, Size(100f, 50f)))
    @Test fun c2_control_double3_on_100x0_rows_0_2() = assertEquals(setOf(0, 2), dbl(Side.BOTTOM, 3f, Size(100f, 0f)))
}
```

#### two_line_census.py (verbatim)

```python
#!/usr/bin/env python3
# L6 re-verify skeptic — independent census of the TWO-LINE far-edge paths (DOUBLE w>=3, GROOVE/RIDGE w>=2)
# whose doubleGeom inner line is mirrored by max(inset, extent-inset) whenever extent < 2*inset.
# inset_max: DOUBLE w - w/6 (=5w/6) ; GROOVE/RIDGE 3w/4.  Mirror (wrong) iff extent < 2*inset_max.
import json, glob, os, sys, collections
ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
RUN = f'{ROOT}/tools/titan/runs/wave51-fix/sections'
cells = {}
for man in glob.glob(f'{RUN}/*/manifest.json'):
    m = json.load(open(man))
    for key, r in m['wpt']['results'].items():
        for plat, d in (r.get('browserRef') or {}).get('diffs', {}).items():
            if isinstance(d, dict) and isinstance(d.get('ssim'), (int, float)) and not d.get('scoreExcluded'):
                cells[(key, plat.replace('-ref', ''))] = ('P' if d.get('wptPass') is True else 'f', d['ssim'])
def prop(c, t):
    for p in c.get('properties') or []:
        if p.get('type') == t: return p.get('data')
    return None
def px(v):
    if isinstance(v, dict):
        if isinstance(v.get('px'), (int, float)): return v['px']
    if isinstance(v, (int, float)): return v
    return None
def style(c, side):
    s = prop(c, f'Border{side}Style')
    return s.upper() if isinstance(s, str) else ''
def width(c, side):
    st = style(c, side)
    if st in ('', 'NONE', 'HIDDEN'): return 0
    w = px(prop(c, f'Border{side}Width'))
    return 3 if w is None else w
rows = []
for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
    stem = os.path.basename(f)[:-5]
    key = 'css/' + '/'.join(stem[len('wpt__'):].split('__')) + '.html'
    cs = json.load(open(f)).get('components', [])
    ids = {c['id']: c for c in cs}
    parents = {(c.get('slot') or {}).get('parent') for c in cs}
    for c in cs:
        for far, near, P, pa, pb in (('Bottom', 'Top', 'Height', 'PaddingTop', 'PaddingBottom'),
                                     ('Right', 'Left', 'Width', 'PaddingLeft', 'PaddingRight')):
            st = style(c, far); w = width(c, far)
            if st not in ('DOUBLE', 'GROOVE', 'RIDGE') or w <= 0: continue
            if st == 'DOUBLE' and w < 3: continue          # drawDouble's one-stroke fallback
            if st != 'DOUBLE' and w < 2: continue          # groove/ridge one-stroke fallback
            inset_max = (w - w / 6) if st == 'DOUBLE' else 0.75 * w
            thresh = 2 * inset_max
            band = width(c, near) + (px(prop(c, pa)) or 0) + (px(prop(c, pb)) or 0) + w
            bs = str(prop(c, 'BoxSizing') or '').upper()
            h = px(prop(c, P))
            if h is not None:
                ext = h if 'BORDER' in bs else h + band; how = f'{P}={h}' + (' border-box' if 'BORDER' in bs else '')
            elif c.get('text'):
                ext = None; how = 'text'
            elif c['id'] in parents:
                ext = None; how = 'children'
            else:
                ext = band; how = 'empty'
            # parent definite size smaller than the child's own extent (the T2 compression)
            par = ids.get((c.get('slot') or {}).get('parent'))
            ph = px(prop(par, P)) if par is not None else None
            mirrored = ext is not None and ext < thresh
            rows.append(dict(test=key, comp=c['name'], side=far, style=st, w=w, extent=ext, how=how,
                             thresh=round(thresh, 3), mirrored=mirrored, parentDefinite=ph,
                             android=cells.get((key, 'android'))))
mir = [r for r in rows if r['mirrored']]
amb = [r for r in rows if r['extent'] is None]
print('two-line far-edge carriers:', len(rows), 'tests', len({r['test'] for r in rows}))
print('MIRRORED (extent known < 2*inset_max):', len(mir), 'tests', len({r['test'] for r in mir}))
for r in mir: print('  ', r)
print('extent unknown (text/children):', len(amb))
for r in amb: print('  ', r['test'], r['comp'], r['side'], r['style'], r['w'], r['how'], 'parentDef', r['parentDefinite'], r['android'])
print('parent definite < thresh (compression candidates):')
for r in rows:
    if r['parentDefinite'] is not None and r['parentDefinite'] < r['thresh']: print('  ', r)
json.dump(rows, open(sys.argv[1], 'w'), indent=1)
```
