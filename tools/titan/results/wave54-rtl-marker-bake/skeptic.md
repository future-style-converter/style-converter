# wave54 L1 · rtl-marker-bake — SKEPTIC report

Base HEAD **db6e8aa0**. Lane note `_note.md` (STATUS: COMPLETE, 19:29:15). Owned bytes examined (unchanged for the
whole review): `tools/titan/bidi-bake.mjs` 98ad650e4219…, `tools/titan/bidi-bake.test.mjs` d85f66366524…,
`tools/titan/bidi-marker-bake.mjs` 4ded3874bba8…. Every repro below was EXECUTED; scripts and outputs are in
`skeptic/` beside this file. The shared tree was never mutated: every mutation ran in a throwaway export
(`git archive HEAD tools/titan tools/visual package.json` + the three owned files overlaid + symlinked
node_modules / tools/wpt / fixtures/wpt / wpt-buckets.json; with the buckets linked the suites match the shared
tree: 134/134, bidi-bake alone 69/69).

## Verdict: CLAIM-HOLDS (no must-fix before the gate; two should-fix, six nits)

The lane's units do what the note says, the radius is exactly the plan's, and every pin I replayed can fail.
Neither should-fix moves a corpus cell today: (1) is latent, with 0 corpus reach; (2) is a hole in the
plan-owned stage-1 instrument, mitigated by the pre-registered PNG look.

## Repros (executed)

| # | what | result | file |
|---|---|---|---|
| R1 | the lane's 7 P mutations on the **P-state** export (HEAD + `unit-P.patch`), my replayer | 7/7 red → byte-exact restore → green, **same sha256 triples** as the lane's log (f266b46bb9ec → … → f266b46bb9ec) | `skeptic/replay.P-state.out.txt` |
| R2 | the lane's 23 final-state mutations (P-1..7, M-1..15, M-4b) on the **final** export | 23/23 OK (expected pins red, byte-exact restore, 69/69 green after each) | `skeptic/replay.final-state.out.txt` |
| R3 | my own GAP mutations: G-1 `'padding' in props` guard dropped; G-2 owner = item's PARENT; G-3 marker runs unshifted before text runs; G-4 hidden-element skip dropped in the marker loop | G-1 CAUGHT (V3b), G-3 CAUGHT (V1/V6/V4), **G-2 GAP (69/69 green)**, G-4 GAP but inert (the runs loop's `hidden.has(key)` skips a hidden item's runs anyway) | `skeptic/replay.gap.out.txt` |
| R4 | unit replay: `unit-P.patch` on HEAD = `state-P/*.txt` sha256; no marker module; `unit-Mprime.patch` on it = the shared-tree bytes (cmp) | holds; P state stands ALONE: bidi/counter-style/counter-bake 121/121, extract-fixture 483/483, view-transition-bake 140/140 | (commands in this note) |
| R5 | my own IR census (`skeptic/census-ir.py`, wire signature of `rootProperties`, never the lane's census) over all 1435 wave53-final per-test IR docs | 17 baked docs = the 17 manifest `bidiBaked`; **P: 6 roots in 4 docs**; UA-padded roots without authored padding 0; list-item roots with padding 0; **M′: 4 items, all counter-suffix, all direct children, 0 carrying an escaped paint effect**; 0 non-RELATIVE root holds a list item | `skeptic/census-ir.wave53-final.out.txt` |
| R6 | must-not-move re-derived (`cells.mjs` over the 17 docs + the 15 cssom docs, minus the 5 scored carrier cells) vs `watchlist.txt` L1 block | 40 baked cells (31 P) − 5 = 35, + 45 cssom = **80 = the watchlist's 80, none only-mine / only-plan**; carriers = `expectations.json` captureCarriers / wireCarriers / revertUnits exactly | (inline) |
| R7 | my wire differ (`skeptic/wire-diff.py`) on the window outputs that ran after the note (`runs/wave54-l1-w2-*`) vs wave54-open | css-counter-styles: identical 32 · renumbered 15 (the cssom ones) · content-changed 1; counter-suffix 23 → 29: roots Padding L/R → `{px:0}`, 4 items `+ListStyleType none −markerText`, 6 runs `slot.parent` = the roots at 116.3/120.59 · 2/26, Dir RTL on the Hebrew runs; **stack-shape parents [] → []**; selectors 48 identical; css-anchor-position 47 + anchor-center-safe-rtl Padding* only | `skeptic/wire-diff.w2-*.out.txt` |
| R8 | wave53-open → wave53-probe (the same P bytes) IR and pixels | IR: only bidi-lines-001/-002 and anchor-center-safe-rtl Padding*; css-backgrounds / css-writing-modes / selectors identical. Pixels: those 3 docs web+iOS **decoded-pixel identical**, Android moved; counter-suffix rows 0-207 identical ×3; the 15 cssom docs ×3 = 45 captures identical under the same +6 shadow. wave53-final = wave54-open in IR (6 sections) and pixels (12 captures) | `skeptic/wire-diff.wave53-*.out.txt`, `skeptic/pixel-identity.*.out.txt` |
| R9 | `cells.mjs 'counter-suffix|bidi-lines-00|anchor-center-safe-rtl' wave53-open wave53-probe wave53-final wave54-open` | every "from" value in the note / PLAN matches (0.9547 / 0.9802 / 0.9818; 0.8934 → 0.9629; 0.9534 → 0.9818; probe 0.9793 / 0.9873 / 1); anchor-center-safe-rtl unscored | (inline) |
| R10 | `geometry-gate.py wave53-final --base wave53-open --lanes L1 --self-test` | **HOLDS, exit 0**: gating 7 FAIL · control 7 PASS · report 1 FAIL — identical to the lane's record | `skeptic/geometry-gate.wave53-final.selftest.out.txt` |
| R11 | LOOKED at ref / wave53-final / wave53-probe counter-suffix ×3 and bidi-lines-001/-002 (ref, web F, android F, android probe) | as described: F web `1.  foo` (left), F iOS no RTL marker and `.א` rows 3-4, F android text at x152 no marker; probe android `foo` / `bar ·` / `foo ·` / `bar ·א` / lone `ב.`; bidi-lines-002 orange `!` LEFT on web and android in every run (DEGENERATE, labelled); bidi-lines-001 probe android line starts on the ref's x, Arabic face wider (font residual, labelled) | `skeptic/look-*.png` |
| R12 | **escape repro**: verbatim frozen counter-suffix + `opacity:0; visibility:hidden; text-shadow` on the first RTL `<li>`, the M′ plan applied | the item's text run inherits all three; its root-owned `.` / `1` runs inherit **none**; no `marker-*` stamp | `skeptic/escape-repro.out.txt` |
| R13 | **geometry fakes**: wave53-probe counter-suffix with the `.` of `.1`/`.2` whitened (web/iOS), and with the digits whitened, run through BOTH unmodified gating probes | no-dot: `rtl-marker-bake.geometry.py` web **GEOMETRY OK**, `lists-bakes.geometry.py` web **OK** and ios **OK**; no-digit: WRONG everywhere (the rule bites only on a dropped digit) | `skeptic/geom-fakes.out.txt` |
| R14 | hygiene: seam files vs HEAD sha256; `runs/wave54-lock/`; probe/console/scratch grep over the owned diff and the lane dir; the other suites that read bidi-bake (`wpt-white-canvas` 21/21, `extract-fixture` 483/483, `section-runner` 19/19, `view-transition-bake` 140/140 on the shared tree); `hunk-for-orchestrator-1.patch` `git apply --check` | the 4 seam files are byte-identical to HEAD; no lock left; only domain uses of "probe"; no scratch pointer; all green; the hunk applies clean, base sha256 b50d9bd1… matches | (inline) |

## Defects (ranked)

1. **should-fix · `tools/titan/bidi-bake.mjs` planBidiBake marker loop + `bidi-marker-bake.mjs` planMarker.**
   Root ownership is a silent fallthrough by construction. Re-parenting the marker runs to the root drops every
   non-inherited paint effect of the item and of any box between it and the root (opacity, transform, filter,
   clip-path, mask, overflow clip, z-index). It also drops every inherited property `runProperties` does not
   restate (visibility, text-shadow, font-feature-settings, …). Neither is declined or stamped.
   - Under HEAD, Blink would not paint such a marker. Under M′ the runs paint it (R12).
   - Corpus reach is **0**: R5 found the 4 items to be direct children with none of these properties.
   - Fix:
     - read the item's `::marker` `visibility` in `inPageMarkerProbe` (it already holds `cs` / `ms` / the element);
     - walk the element up to the root for the non-inherited effects;
     - decline `marker-not-baked` on any hit.
     
     The new logic goes in a new ≤200-line file, because `bidi-marker-bake.mjs` is at 199 lines. Pin it with an
     executed mutation. At minimum, add a TODO plus the stamp and queue it.
2. **should-fix · plan instrument (`wave54-plan/rtl-marker-bake.geometry.py` m_check and
   `wave53-plan/lists-bakes.geometry.py`), a hand-off to the orchestrator.**
   - Under M′ each decimal RTL marker is TWO independent runs (`.` and `1`). Both gating probes print
     GEOMETRY OK when the `.` run is missing (R13), because they check only marker width ≥ 5 px and the right edge.
   - A tabular digit alone passes, and Android has no M′ SSIM floor. So "RTL rows picture-correct ONLY if `[M]`
     prints OK" rests on the pre-registered PNG look, where the `.` is a 2-px detail.
   - Fix before stage 1 reads M′: also require the row's marker-ink LEFT edge at the ref's x133 ±2. Then re-run
     `--self-test` and these fakes: no-dot must turn WRONG, and the ref and wave53-probe web/iOS must stay OK.
3. **nit · `tools/titan/bidi-bake.test.mjs` V6 / V7.**
   - Every pinned item is a DIRECT child of its root, so "owner = enclosing root" is indistinguishable from
     "owner = parent".
   - G-2 (R3) passes 69/69.
   - Fix: add one nested case (root > abspos box > li). Under owner = parent it recreates the stack shape, and V7
     goes red.
4. **nit · `_note.md`.** It says "29/29 OK", but 30 mutations were executed and logged: P×7 at the P state, P×7 at
   final, M-1..15 and M-4b. All 30 are OK.
5. **nit · `_note.md` / lane dir.** `mutations.log` matches `.gitignore:21 *.log` and will not land with the lane
   dir, yet the note cites it as the "full log". The `mutations-*.result.json` files carry the same sha256s, so cite
   those, or rename the log to `.txt`.
6. **nit · `tools/titan/bidi-marker-bake.mjs`.** 72 code lines have no comment on or directly above them (house
   rule "every line commented"). The code is the wave-53 restoration, byte-identical, at the 200-line cap.
7. **nit · `_note.md`.** The window results that ran after the note are not recorded:
   - [W1] `marker-probe.w54.out.txt`: ALL PASS;
   - [W2] `w2.counter-suffix.out.txt`: ALL PASS;
   - [W2b] counter-styles / selectors / anchor-position: ALL PASS.
   
   css-text [W2b] was never run on this tree (P's css-text wire rests on wave53-probe, R8).
8. **nit · PLAN §2 L1 / brief §4 wording.** The sentence "P has made the root's padding box equal to its border
   box" is wrong:
   - `paddingBoxOrigin` = border box + borders, whatever the padding is;
   - what P actually does is make the content box equal the padding box, and Android anchors at the content box.
   
   The code comments are correct.

## Honesty check

- **counter-suffix web P 1 "faithful":** earned. At wave53-probe the web capture equals the ref on every row (R11),
  and root vs li ownership gives Blink the same used x (W1: frames x132.30-146.97).
- **iOS / Android "RTL rows picture-correct, cell stays DEGENERATE on rows 3-6 / 5-6":** honest, with the
  geometry caveat of defect 2.
- **bidi-lines-002 android "stays DEGENERATE":** honest. The orange `!` is on the left on web too.
- **bidi-lines-001 android f→P:** earned by line-start geometry. The Arabic face residual is disclosed.

## Could NOT check

- No device, emulator, simulator or Chromium was run by me. The root-owned runs have never been rendered natively;
  stage 1 is the only evidence for Compose `RenderAbsoluteChild` / the SwiftUI overlay on them.
- I did not re-run [W1] / [W2] myself. I read their outputs and re-derived the W2 wire with my own differ.
- css-text, css-backgrounds and css-writing-modes were not re-extracted on this tree. Their P wire rests on
  wave53-probe (same P bytes, same tests.list, R8).
- Interactions with L6 RS (separators between the `<ol>` root's abspos children) and with other lanes' edits on the
  integrated stage-1 tree are unmeasured.
