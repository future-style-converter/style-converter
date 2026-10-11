#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-teeth-r3.py — the ROUND-3 cases of the standing rejection set: ADDED INK
# (plan-skeptic-round-3.verify.md M2; fix-r3.done.md). Loaded by probes-teeth.py run_all() after probes-teeth-r2.py (never run
# alone); every case is (lane, sections, fid, {(sec, plat, test): picture}, {(script, key): pre-registered verdict}).
#
# Why: rounds 1-2 built wrong pictures that REMOVE, MOVE, SHORTEN or REARRANGE the target ink. Round 3's verifier painted a row a
# second time a few px lower and the three L3 dir-style-02a row keys accepted it (gate exit 0). The class: the target painted
# twice (vertical ghost, horizontal copy), a glyph doubled, stray ink inside the key's own band. The builders live in
# plan-skeptic-round-3.fakes/added_ink.py (shared with sweep.py, the full matrix over every lane that probes-selfcheck.py §T3
# runs); the cases here pin, per gating / tally key, the added-ink pictures it must reject itself:
#   L3 dir-style-02a rows 6 / 7 / 9: the verifier's row-band ghosts (+3 / +5 / +7 y) and every round-3 row variant, each on its
#      own row only, FAIL on that row's key; the whole-picture ghosts +7 y / −12 y (the verifier's) and copies ±1 / +2 x FAIL on
#      all three rows;
#   L3 bidi-tab-001 rtl-rows: a blob on the yellow box, one touching the '0', one touching the run's box, the '0' doubled;
#   L3 [W] stack: a blob 2 px off a cell, one in the open, one touching a cell;
#   every other gating / tally key (and L3's dynamic-offset ones): its own fixed pictures painted again 2 px to the right and
#      3 px lower (one case each per lane), FAIL on every key of the case.
# Writes nothing itself: probes-teeth.py writes the fake runs under its --scratch dir.
import importlib.util, os

HERE = os.path.dirname(os.path.abspath(__file__))
_s = importlib.util.spec_from_file_location('added_ink', os.path.join(HERE, 'plan-skeptic-round-3.fakes', 'added_ink.py'))
ai = importlib.util.module_from_spec(_s); _s.loader.exec_module(ai)


def _load_r2(pt):
    """probes-teeth-r2.py's L1 cases (the tally's fixed pictures)."""
    import importlib.util as iu
    s2 = iu.spec_from_file_location('probes_teeth_r2', os.path.join(HERE, 'probes-teeth-r2.py')); r2 = iu.module_from_spec(s2); s2.loader.exec_module(r2)
    return r2.cases(pt, 'L1')


def cases(pt, lane):
    """The round-3 (added-ink) cases of one lane; pt is the loaded probes-teeth module."""
    out = []
    def case(secs, fid, repl, keys, verdict='FAIL'):
        out.append((lane, secs, f'r3-{fid}', repl, {k: verdict for k in keys}))
    def whole(fixed_fid, builders):
        """Every fixed case `fixed_fid` of this lane (probes-teeth + r2) with each whole-picture variant on its native captures."""
        import importlib.util as iu
        s2 = iu.spec_from_file_location('probes_teeth_r2', os.path.join(HERE, 'probes-teeth-r2.py')); r2 = iu.module_from_spec(s2); s2.loader.exec_module(r2)
        src = {'L1': pt.l1_cases, 'L2': pt.l2_cases, 'L3': pt.l3_cases, 'L4': pt.l4_cases, 'L5': pt.l5_cases, 'L6': pt.l6_cases}[lane]
        for ln, secs, fid, repl, want in src() + r2.cases(pt, lane):
            if fid != fixed_fid:
                continue
            for name, f in builders:
                case(secs, f'{fid}-{name}', {k: (v if k[1] == 'web' else f(v)) for k, v in repl.items()}, [k for k, w in want.items() if w == 'PASS'])
    WHOLE = [('hdup+2x', lambda a: ai.ghost(a, 2, 0)), ('ghost+3y', lambda a: ai.ghost(a, 0, 3))]

    if lane == 'L1':
        whole('fixed', WHOLE)
        # the tally's hyphenate-character-001 / -003 controls are the wave-54 probe (outside this plan's edits): they reject a
        # ghost, NOT a horizontal copy (sweep §T3: their sibling linebox keys reject that capture, so the tally cell — which
        # needs every key — is not counted picture-correct). Named here so a later round sees the blind spot pinned.
        whole('r2-L1-tally-fixed', WHOLE[1:])
        src_r2 = _load_r2(pt)
        for ln, secs, fid, repl, want in src_r2:
            if fid == 'r2-L1-tally-fixed':
                out.append((lane, secs, f'r3-{fid}-hdup+2x-named-blind-spot', {k: (v if k[1] == 'web' else ai.ghost(v, 2, 0)) for k, v in repl.items()},
                            {**{k: 'PASS' for k, w in want.items() if w == 'PASS'},
                             **{('hyphen-ios-linebox.geometry.py', f'hyphen-ios-linebox {t} ios'): 'FAIL' for t in ('001', '003')}}))
    if lane == 'L2':
        whole('U1-fixed', WHOLE); whole('U2-fixed', WHOLE)
        # the `core:` read's own cases (round 3): green ADDED in a rounded corner's cut-out (inside the bbox, outside the arc, so
        # the bbox read cannot see it) and a 3×3 hole in the box — both under the 40-px XOR bound, rejected only by `core:`
        S2 = 'compose-wpt-content-box-cb.geometry.py'
        def corner_blob(a):
            x0, y0, x1, y1 = pt.bbox_of(pt._green(a)); o = a.copy(); o[y0 + 2:y0 + 5, x0 + 2:x0 + 5] = (0, 128, 0); return o
        def hole3(a):
            x0, y0, x1, y1 = pt.bbox_of(pt._green(a)); cx, cy = (x0 + x1) // 2, (y0 + y1) // 2; o = a.copy(); o[cy:cy + 3, cx:cx + 3] = 255; return o
        FE = [u for u in pt.L2_U1 if u[0] == 'filter-effects']
        k1 = lambda sec, t: (S2, f'{t.split("/")[-1] if sec == "filter-effects" else t} android')
        case('filter-effects', 'L2-U1-core-corner-blob3', {(sec, 'android', t): corner_blob(pt.cap('wave54-probe', sec, t, 'android')) for sec, t in FE},
             [k1(sec, t) for sec, t in FE])
        case('css-flexbox,filter-effects', 'L2-U1-core-hole3', {(sec, 'android', t): hole3(pt.cap('wave54-probe', sec, t, 'android')) for sec, t in pt.L2_U1},
             [k1(sec, t) for sec, t in pt.L2_U1])
        C3 = 'backdrop-filter-nested-border-radius-clip-3'; B3 = pt.png('cbb-android-replay/android-clip-3.png')
        K3 = [(S2, f'{C3} android'), ('cbb-android.geometry.py', f'{C3} android')]
        case('filter-effects', 'L2-U2-core-corner-blob3', {('filter-effects', 'android', C3): corner_blob(B3)}, K3)
        case('filter-effects', 'L2-U2-core-hole3', {('filter-effects', 'android', C3): hole3(B3)}, K3)
    if lane == 'L3':
        S3 = 'positioned-parent-slot.geometry.py'
        D = pt.l3_fixed('dir-style-02a'); cap = ('selectors', 'android', 'dir-style-02a')
        rowkey = lambda k: (S3, f'[S] dir-style-02a android row{k}')
        for k in (6, 7, 9):
            for v in ai.L3_DIR_VARIANTS:
                case('selectors', f'L3-dir-row{k}-{v}', {cap: ai.l3_dir(D, k, v)}, [rowkey(k)])
        for dy in (3, 5, 7):                     # the verifier's own fake (verify.d/newfakes3.py): rows 6, 7 and 9 ghosted AT ONCE
            o = D
            for k in (6, 7, 9):
                o = ai.l3_dir(o, k, f'band-ghost+{dy}y')
            case('selectors', f'L3-dir-rows679-band-ghost+{dy}y', {cap: o}, [rowkey(k) for k in (6, 7, 9)])
        for name, f in (('ghost+7y', lambda a: ai.ghost(a, 0, 7)), ('ghost-12y', lambda a: ai.ghost(a, 0, -12)),
                        ('hdup+1x', lambda a: ai.ghost(a, 1, 0)), ('hdup+2x', lambda a: ai.ghost(a, 2, 0)), ('hdup-2x', lambda a: ai.ghost(a, -2, 0))):
            case('selectors', f'L3-dir-whole-{name}', {cap: f(D)}, [rowkey(k) for k in (6, 7, 9)])
        B = pt.l3_fixed('bidi-tab-001')
        for v in ai.L3_BIDI_VARIANTS:
            case('css-text', f'L3-bidi-{v}', {('css-text', 'android', 'bidi/bidi-tab-001'): ai.l3_bidi(B, v)}, [(S3, '[S] bidi-tab-001 android rtl-rows')])
        ST = pt.l3_fixed('anchor-position-multicol-nested-001')
        for v in ai.L3_STACK_VARIANTS:
            case('css-anchor-position', f'L3-stack-{v}', {('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): ai.l3_stack(ST, v)},
                 [(S3, '[W] anchor-position-multicol-nested-001 android stack')])
        for fid in ('001-fixed', '002-fixed', 'r2-L3-vrl-rtl-001-fixed'):
            whole(fid, WHOLE)
    if lane == 'L4':
        whole('C-fixed', WHOLE); whole('U1-fixed', WHOLE)
    if lane == 'L5':
        whole('fixed', WHOLE)
    if lane == 'L6':
        whole('X-fixed', WHOLE); whole('PO-fixed', WHOLE)
    return out
