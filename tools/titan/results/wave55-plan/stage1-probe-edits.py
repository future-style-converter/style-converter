#!/usr/bin/env python3
# stage1-probe-edits.py — the wave-55 stage-1 step-8 probe edits (PLAN §10 items 4-6), applied as exact string replacements
# (each must apply exactly once) to <plan-dir>/positioned-parent-slot.geometry.py and counter-suffix-rows.geometry.py.
# Usage: python3 stage1-probe-edits.py <plan-dir>
import sys, os
D = sys.argv[1]

def patch(fn, reps):
    p = os.path.join(D, fn); s = open(p).read()
    for old, new in reps:
        assert s.count(old) == 1, f'{fn}: expected exactly one occurrence of:\n{old[:200]}'
        s = s.replace(old, new)
    open(p, 'w').write(s); print('patched', fn, len(reps), 'edits')

# ── L3 · positioned-parent-slot.geometry.py: the [S] rtl-rows `ink:` component read gains the DECIDED device picture as a second
#    reference (INK_RUN, additive like L5's TWIN_RUN) ──
patch('positioned-parent-slot.geometry.py', [
(
"""run = argv[0] if argv else 'wave54-final'
base = argv[1] if len(argv) > 1 else 'wave54-final'
""",
"""# Stage 1 (PLAN §10 item 10, D39's re-pin for a same-renderer read): the [S] bidi-tab-001 rtl-rows `ink:` component read compares
# each RTL band's blue components with the BASE capture's (round 3: "U2 moves runs, it neither adds, splits nor merges ink").
# On the device, band y106's '0' lands at x115-123 — 1 px right of Chromium's x114-122 — and TOUCHES its square (x124-135): the
# 4-connected read merges box + ring (262 px) and counts 2 where wave55-open's WRONG picture (the '0' stranded at x210-219,
# outside the yellow box) counted 3 — the look is the ref's (the recorded look, ../wave55-gate/_note.md stage 1). The band's
# structure is therefore ALSO accepted when it equals the INK run's same-platform band (the decided device picture; default
# wave55-pre from stage 1 on, `--ink-run <run>` / `--ink-run none` for dry runs): a band passes against the base OR against the
# ink run, under the same rules. Additive: no fixed picture changes verdict (every replay band still reads against the base);
# a stray blob / a doubled '0' / a blob touching the box still fails BOTH (positioned-parent-slot.stage1-mutations.out.txt).
INK_RUN_DEFAULT = 'wave55-pre'
ink_run = INK_RUN_DEFAULT
if '--ink-run' in argv:
    i = argv.index('--ink-run'); ink_run = None if argv[i + 1] == 'none' else argv[i + 1]; del argv[i:i + 2]
run = argv[0] if argv else 'wave54-final'
base = argv[1] if len(argv) > 1 else 'wave54-final'
"""),
(
"""def band_comps(a, y0):
    \"\"\"The blue components of one 20-px RTL band.\"\"\"
    m = np.zeros(a.shape[:2], bool); m[y0:y0 + 20] = A_BLUE(a)[y0:y0 + 20]
    return comps4(m)
""",
"""def band_comps(a, y0):
    \"\"\"The blue components of one 20-px RTL band.\"\"\"
    m = np.zeros(a.shape[:2], bool); m[y0:y0 + 20] = A_BLUE(a)[y0:y0 + 20]
    return comps4(m)


def band_ink_why(c, bc, ref_name, y0):
    \"\"\"The round-3 structure rules of one band against one reference's components (None = the band is that reference's).\"\"\"
    if len(c) != len(bc):
        return f'ink: band y{y0} {len(c)} blue components vs {ref_name} {len(bc)} (a stray blob / a doubled or split glyph)'
    (n0, bb), (bn0, bbb) = c[0], bc[0]
    if not (bb[0] >= bbb[0] and bb[1] >= bbb[1] and bb[2] <= bbb[2] and bb[3] <= bbb[3]) or n0 > bn0 + 2:
        return f'ink: band y{y0} run box {n0} px {bb} vs {ref_name} {bn0} px {bbb} (inside it, ≤ +2 px: no added ink)'
    if len(c) > 1:
        (n1, gb), (bn1, gbb) = c[1], bc[1]
        if abs((gb[2] - gb[0]) - (gbb[2] - gbb[0])) > 1 or abs((gb[3] - gb[1]) - (gbb[3] - gbb[1])) > 1 or n1 > bn1 + 2:
            return (f'ink: band y{y0} glyph {n1} px {gb[2] - gb[0] + 1}×{gb[3] - gb[1] + 1} vs {ref_name} {bn1} px '
                    f'{gbb[2] - gbb[0] + 1}×{gbb[3] - gbb[1] + 1} (size ±1, ≤ +2 px)')
    rest, brest = sorted(n for n, _ in c[2:]), sorted(n for n, _ in bc[2:])
    if any(abs(u - v) > 1 for u, v in zip(rest, brest)):
        return f'ink: band y{y0} small components {rest} px vs {ref_name} {brest} (±1 each)'
    return None
"""),
(
"""    bim, _ = capture('css-text', 'bidi/bidi-tab-001', label, run_id=base)        # round 3: the base capture's components
    ba = arr(bim) if bim is not None else None
    if ba is None:
        why.append(f'ink: no {base} capture to read the bands against')
    for y0 in RTL_BANDS if ba is not None else ():
        c, bc = band_comps(a, y0), band_comps(ba, y0)
        if not c or not bc:
            continue                                                   # an empty band is the extent rule's (below)
        if len(c) != len(bc):
            why.append(f'ink: band y{y0} {len(c)} blue components vs {base} {len(bc)} (a stray blob / a doubled or split glyph)')
            continue
        (n0, bb), (bn0, bbb) = c[0], bc[0]
        if not (bb[0] >= bbb[0] and bb[1] >= bbb[1] and bb[2] <= bbb[2] and bb[3] <= bbb[3]) or n0 > bn0 + 2:
            why.append(f'ink: band y{y0} run box {n0} px {bb} vs {base} {bn0} px {bbb} (inside it, ≤ +2 px: no added ink)')
        if len(c) > 1:
            (n1, gb), (bn1, gbb) = c[1], bc[1]
            if abs((gb[2] - gb[0]) - (gbb[2] - gbb[0])) > 1 or abs((gb[3] - gb[1]) - (gbb[3] - gbb[1])) > 1 or n1 > bn1 + 2:
                why.append(f'ink: band y{y0} glyph {n1} px {gb[2] - gb[0] + 1}×{gb[3] - gb[1] + 1} vs {base} {bn1} px '
                           f'{gbb[2] - gbb[0] + 1}×{gbb[3] - gbb[1] + 1} (size ±1, ≤ +2 px)')
        rest, brest = sorted(n for n, _ in c[2:]), sorted(n for n, _ in bc[2:])
        if any(abs(u - v) > 1 for u, v in zip(rest, brest)):
            why.append(f'ink: band y{y0} small components {rest} px vs {base} {brest} (±1 each)')
""",
"""    bim, _ = capture('css-text', 'bidi/bidi-tab-001', label, run_id=base)        # round 3: the base capture's components
    ba = arr(bim) if bim is not None else None
    if ba is None:
        why.append(f'ink: no {base} capture to read the bands against')
    # stage 1: the ink run's same-platform capture is the second accepted structure (natives only: web reads its own base)
    iim, _ = capture('css-text', 'bidi/bidi-tab-001', label, run_id=ink_run) if ink_run and label in ('ios', 'android') else (None, None)
    ia = arr(iim) if iim is not None else None
    for y0 in RTL_BANDS if ba is not None else ():
        c, bc = band_comps(a, y0), band_comps(ba, y0)
        if not c or not bc:
            continue                                                   # an empty band is the extent rule's (below)
        w = band_ink_why(c, bc, base, y0)
        if w is not None and ia is not None:
            ic = band_comps(ia, y0)
            if ic and band_ink_why(c, ic, ink_run, y0) is None:
                facts.append(f'band y{y0} structure = {ink_run}\\'s ({len(c)} components)')     # the decided device picture's
                w = None
        if w is not None:
            why.append(w)
"""),
])

# ── L5 · counter-suffix-rows.geometry.py: TWIN_RUN_DEFAULT (pre-registered), the [H] letter-profile twin reference, the [K] zone ──
patch('counter-suffix-rows.geometry.py', [
(
"""TWIN_RUN_DEFAULT = None    # round 4 (D39): the stage-1 step-8 edit sets 'wave55-pre' (the decided device pictures) as an extra twin
""",
"""TWIN_RUN_DEFAULT = 'wave55-pre'    # round 4 (D39): the stage-1 step-8 edit — the decided device pictures are an extra twin (PLAN §10 item 11)
"""),
(
"""def h_check(rows, ref):
    for (k, letters, dots), (_, rl, rd) in zip(rows, ref):
""",
"""def twin_letters(label):
    \"\"\"Stage 1 (PLAN §10 item 11): {3: profile, 4: profile} of TWIN_RUN's same-platform capture — the decided device glyphs, the
    letter-profile reference of the SAME rasterizer. Why: the round-2 `letter profile` read correlates a device letter's 14-column
    profile with Chromium's ref letter; across fonts it misjudges — the device's own alef reads 0.80 vs the ref's alef and 0.88
    vs the ref's bet (the look: alef then its period, bet then its period, the ref's order; ../wave55-gate/_note.md stage 1) —
    while against the device's own glyphs the margin is 1.00 vs 0.83. None for the ref / web rows or before stage 1.\"\"\"
    if not TWIN_RUN or label in ('ref', 'web'):
        return None
    tim, tpx = load(dict(paths(TWIN_RUN, 'css-counter-styles', 'counter-suffix'))[label])
    if tim is None:
        return None
    keep = px_w[0]; px_w[0] = tim.size[0]
    out = {}
    for k, letters, _ in h_rows(tpx):
        if len(letters) == 1:
            out[k] = letter_profile(tpx, k, letters[0])
    px_w[0] = keep
    return out if set(out) == {3, 4} else None


DUP_TOL = 0.95   # stage 1 (PLAN §10 item 11): rows 3 and 4 of ONE capture must show different letters (same font: a duplicated
                 # counter value correlates ≈ 1.0; the pictures of record read 0.83 ios / lower elsewhere — never widened)


def h_check(rows, ref, lref=None):
    profs = {}
    for (k, letters, dots), (_, rl, rd) in zip(rows, ref):
"""),
(
"""        prof = letter_profile(px_h[0], k, L)
        if not ref_letter.get('done'):
            ref_letter[k] = prof
        elif ref_letter:
            # round 2: WHICH letter — a row whose letter is nearer the OTHER row's ref letter carries the wrong counter value
            c_same, c_other = corr(prof, ref_letter[k]), corr(prof, ref_letter[7 - k])
            if c_same <= c_other:
                return f'row {k} letter profile {c_same:.2f} vs its own ref letter, {c_other:.2f} vs row {7 - k}\\'s (the counter value of another row)'
""",
"""        prof = letter_profile(px_h[0], k, L); profs[k] = prof
        if not ref_letter.get('done'):
            ref_letter[k] = prof
        elif ref_letter:
            # round 2: WHICH letter — a row whose letter is nearer the OTHER row's ref letter carries the wrong counter value.
            # Stage 1 (PLAN §10 item 11): a native row is ALSO accepted when it is nearer its own row's letter in TWIN_RUN's
            # same-platform capture (twin_letters: the same rasterizer); a swapped pair fails both (stage1-mutations.out.txt).
            c_same, c_other = corr(prof, ref_letter[k]), corr(prof, ref_letter[7 - k])
            t_same, t_other = (corr(prof, lref[k]), corr(prof, lref[7 - k])) if lref else (0.0, 1.0)
            if c_same <= c_other and t_same <= t_other:
                return (f'row {k} letter profile {c_same:.2f} vs its own ref letter, {c_other:.2f} vs row {7 - k}\\'s (the counter value of another row)'
                        + (f'; {t_same:.2f} vs its own {TWIN_RUN} letter, {t_other:.2f} vs row {7 - k}\\'s' if lref else ''))
"""),
(
"""            if not (near(L[2], rl[0][2], 1) and near(L[3], rl[0][3], 1)):
                return f'row {k} letter ink y{L[2]}-{L[3]} vs ref y{rl[0][2]}-{rl[0][3]}±1 (marker baseline moved)'
    return None
""",
"""            if not (near(L[2], rl[0][2], 1) and near(L[3], rl[0][3], 1)):
                return f'row {k} letter ink y{L[2]}-{L[3]} vs ref y{rl[0][2]}-{rl[0][3]}±1 (marker baseline moved)'
    if ref_letter.get('done') and 3 in profs and 4 in profs and corr(profs[3], profs[4]) >= DUP_TOL:
        # stage 1 (PLAN §10 item 11): the same-font read the twin reference needs — one counter value on both rows
        return f'rows 3 and 4 letter profiles correlate {corr(profs[3], profs[4]):.2f} ≥ {DUP_TOL} (the same counter value on both rows)'
    return None
"""),
(
"""    h_why = h_check(h, refs['h'])
    h_x = ''
""",
"""    h_why = h_check(h, refs['h'], twin_letters(label))
    h_x = ''
"""),
(
"""                [(0, 232, 130, 256), (151, 232, None, 256), (0, 256, 130, 304), (136, 256, None, 304), (0, 304, None, 600)]}
""",
"""                [(0, 232, 130, 256), (151, 232, None, 256), (0, 256, 130, 304), (137, 256, None, 304), (0, 304, None, 600)]}
# Stage 1 (PLAN §10 item 12): rows 11-12's base-identity zone starts at x137, not x136 — on the device U3-ios's run repaint reaches
# x136 (the periods' right anti-aliasing fringe: (136,272) 150→100, (136,273) 137→84, (136,296) 255→240, (136,297) 255→236,
# wave55-pre vs wave55-open), 1 px right of the Catalyst-measured period boxes x130-135 (fix-r1.u3-boxes.measure.out.txt);
# the column is read by the residual window recorded from the device capture (fix-r6.zones.py --also), not left unread.
"""),
])
