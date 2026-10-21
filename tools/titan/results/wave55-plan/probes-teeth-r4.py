#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-teeth-r4.py — the ROUND-4 cases of the standing rejection set: STRICTLY-ADDITIVE ink
# (plan-skeptic-round-4.verify.md M1-M4; fix-r4.done.md). Loaded by probes-teeth.py run_all() after probes-teeth-r3.py (never
# run alone); every case is (lane, sections, fid, {(sec, plat, test): picture}, {(script, key): pre-registered verdict}).
#
# Why: round 3 closed added ink against its own variant set; round 4's verifier built strictly-additive fakes of its own
# (class ink written only where the destination is not already ink: nothing moved or erased) and 17 gating keys accepted
# them inside their own read zones — L4's ten face-present keys (FACE_INK_TOL 6 %; the Compose no-wrap rows never read the
# ink below band 1), L6's five testable [X] keys (`extra` dilated by 1 px), L5 [H] ios (no ink read) and [T] ios (ink inside
# its digit window). The cases below pin EVERY variant the verifier recorded IN-ZONE (plan-skeptic-round-4.verify.d/
# zones4.L4/L5/L6.out.txt: 26 / 9 / 7 variants, 98 / 10 / 28 acceptances), each pre-registered FAIL on exactly the keys that
# accepted it; plus the verifier's zone-restricted L5 fakes (l5-zone-fakes.py: the [H] zone and the [T] window copied 1 px
# down / right, 2×2 / 3×3 blobs), fix-r3's named residual (the 2×2 blob touching a face-present 014 glyph, now closed by
# `face xor:`) and the [I] 4-px case summed over its zones. The builders are the verifier's own (plan-skeptic-round-4.fakes/
# added_ink4.py, copied verbatim from its fakes4.py). A fake adding ≤ 2 px under the key's own mask is the named 2-px floor
# (PLAN §0 / D39) and is not pinned here; probes-selfcheck.txt §T4 lists every one.
# Writes nothing itself: probes-teeth.py writes the fake runs under its --scratch dir.
import importlib.util, os
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
_s = importlib.util.spec_from_file_location('added_ink4', os.path.join(HERE, 'plan-skeptic-round-4.fakes', 'added_ink4.py'))
ai4 = importlib.util.module_from_spec(_s); _s.loader.exec_module(ai4)
_s3 = importlib.util.spec_from_file_location('added_ink', os.path.join(HERE, 'plan-skeptic-round-3.fakes', 'added_ink.py'))
ai3 = importlib.util.module_from_spec(_s3); _s3.loader.exec_module(ai3)

S4, S5, S6 = 'ua-heading-margin.geometry.py', 'counter-suffix-rows.geometry.py', 'android-paint-order.geometry.py'
I5 = '[I] cjk-decimal/css3-counter-styles-005'
# (lane, the group's fixed case, variant) → the gating keys that accepted it IN-ZONE (the verifier's zones4 records, verbatim)
IN_ZONE = [
    ('L4', 'C-fixed', 'c0:dg-first-adj', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android', '[F] 006 ios', '[M] 006 ios', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:dg-last-adj', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:dg-mid-smear', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android', '[F] 006 ios', '[M] 006 ios', '[M] 006 android',
                                          '[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c0:dg-first-smear', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android', '[F] 006 ios', '[M] 006 ios', '[M] 006 android',
                                            '[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c0:st-dash', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:st-vbar', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android']),
    ('L4', 'C-fixed', 'c0:dg-band0-last', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:st2-band0-end3', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:hd-band1@+2', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:hd-band1@+5', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:dg-band1-last', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c0:st2-band1-end3', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:vd-tgt+1', ['[F] 005 ios', '[M] 005 ios', '[M] 005 android', '[F] 006 ios', '[M] 006 ios', '[M] 006 android',
                                      '[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:vd-tgt-1', ['[M] 005 android', '[M] 006 android', '[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:vd-tgt+2', ['[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:hd-tgt+1', ['[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:hd-tgt-1', ['[M] 005 android', '[M] 006 android', '[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:dg-last-adj', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:dg-mid-smear', ['[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:dg-first-smear', ['[F] 014 ios', '[M] 014 ios', '[F] 014 android', '[M] 014 android']),
    ('L4', 'C-fixed', 'c1:st2-band1-end3', ['[M] 006 android']),
    ('L4', 'C-fixed', 'c1:vd-band2@-3', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:hd-band2@+2', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:hd-band2@+5', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:dg-band2-last', ['[M] 005 android', '[M] 006 android']),
    ('L4', 'C-fixed', 'c1:st2-band2-end3', ['[M] 005 android', '[M] 006 android']),
    ('L5', 'fixed', 'c0:hd-tgt+1', [f'{I5} ios', '[H] counter-suffix ios']),
    ('L5', 'fixed', 'c0:dg-first-adj', ['[H] counter-suffix ios']),
    ('L5', 'fixed', 'c0:hd-band0@+2', [f'{I5} android']),
    ('L5', 'fixed', 'c0:st2-band6-end3', ['[T] counter-suffix ios']),
    ('L5', 'fixed', 'vd-whole-min+1', ['[H] counter-suffix ios']),
    ('L5', 'fixed', 'vd-whole-min-1', ['[H] counter-suffix ios']),
    ('L5', 'fixed', 'c0:vd-tgt+1', ['[H] counter-suffix ios']),
    ('L5', 'fixed', 'c0:vd-tgt-1', ['[H] counter-suffix ios']),
    ('L5', 'fixed', 'c0:hd-tgt-1', ['[H] counter-suffix ios']),
    ('L6', 'X-fixed', 'c0:vd-tgt+1', ['flex-abspos-staticpos-align-self-safe-001', 'grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001',
                                      'grid-abspos-staticpos-align-self-vertWM-001', 'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:vd-tgt-1', ['grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001', 'grid-abspos-staticpos-align-self-vertWM-001',
                                      'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:hd-tgt+1', ['grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001', 'grid-abspos-staticpos-align-self-vertWM-001',
                                      'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:hd-tgt-1', ['grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001', 'grid-abspos-staticpos-align-self-vertWM-001',
                                      'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:dg-mid-smear', ['grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001', 'grid-abspos-staticpos-align-self-vertWM-001',
                                          'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:dg-first-smear', ['flex-abspos-staticpos-align-self-safe-001', 'grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-justify-self-001',
                                            'grid-abspos-staticpos-align-self-vertWM-001', 'grid-abspos-staticpos-align-self-rtl-001']),
    ('L6', 'X-fixed', 'c0:st-vbar', ['grid-abspos-staticpos-align-self-001', 'grid-abspos-staticpos-align-self-rtl-001']),
]


def key_of(lane, k):
    """(script, full key) of a short IN_ZONE key."""
    if lane == 'L4':
        tag, n, plat = k.split()
        return (S4, f'{tag} text-decoration-inset-{n} {plat}')
    if lane == 'L5':
        return (S5, k)
    return (S6, f'[X] {k} android')


_classes = {}


def fake(F, B, name):
    """added_ink4.build with the per-capture colour classes computed once (fakes4.py computes them once per group too)."""
    if not name.startswith('c'):
        return ai4.whole(F, name)
    sig = (F.tobytes().__hash__(), B.tobytes().__hash__())
    if sig not in _classes:
        _classes[sig] = ai4.classes(F, B)
    ci, n = name.split(':', 1); ci = int(ci[1:]); ks = _classes[sig]
    return ai4.variant(ks[ci], n) if ci < len(ks) else None


def cases(pt, lane):
    """The round-4 (strictly-additive) cases of one lane; pt is the loaded probes-teeth module."""
    ai4.pt = pt
    out = []
    src = {'L4': pt.l4_cases, 'L5': pt.l5_cases, 'L6': pt.l6_cases}.get(lane)
    if src is None:
        return out
    fixed = {fid: (secs, repl) for ln, secs, fid, repl, want in src()}
    for ln, gid, var, keys in IN_ZONE:
        if ln != lane:
            continue
        secs, repl = fixed[gid]
        r = {}
        for (sec, plat, test), F in repl.items():
            o = None if plat == 'web' else fake(F, pt.cap('wave54-final', sec, test, plat), var)
            r[(sec, plat, test)] = F if o is None else o
        out.append((lane, secs, f'r4-{gid}-{var.replace(":", "_")}', r, {key_of(lane, k): 'FAIL' for k in keys}))
    if lane == 'L4':
        # fix-r3's named residual, closed by round 4's `face xor:`: a 2×2 blob touching a glyph of a face-present 014 band
        secs, repl = fixed['C-fixed']
        r = {x: (F if x[1] == 'web' or not x[2].endswith('014') else ai3.apply('stray2-mid', F, pt.cap('wave54-final', x[0], x[2], x[1])))
             for x, F in repl.items()}
        out.append((lane, secs, 'r4-C-fixed-r3-residual-stray2-mid-014', r,
                    {(S4, f'{t} text-decoration-inset-014 {p}'): 'FAIL' for t in ('[M]', '[F]') for p in ('ios', 'android')}))
    if lane == 'L5':
        # the verifier's zone-restricted fakes (plan-skeptic-round-4.verify.d/l5-zone-fakes.py, ported): ink only inside ONE key's
        # own zone of counter-suffix ios — [H] rows 3-4 x0-63 × y64-111, [T] the digit window x136-150 × y208-231
        secs, repl = fixed['fixed']; X = ('css-counter-styles', 'ios', 'counter-suffix'); F = repl[X]
        ink = np.abs(F).sum(axis=2) <= 90; anyink = F.min(axis=2) < 200
        def copy(zone, dx, dy):
            x0, y0, x1, y1 = zone; o = F.copy(); ys, xs = np.nonzero(ink[y0:y1, x0:x1]); ys += y0; xs += x0
            ty, tx = ys + dy, xs + dx; ok = ~ink[ty, tx]; o[ty[ok], tx[ok]] = F[ys[ok], xs[ok]]; return o
        def blob(zone, n):
            x0, y0, x1, y1 = zone; ys, xs = np.nonzero(ink[y0:y1, x0:x1]); xr, ym = xs.max() + x0, int(np.median(ys)) + y0
            for dx in range(2, 8):
                if xr + dx + n <= x1 and not anyink[ym:ym + n, xr + dx - 1:xr + dx + n + 1].any():
                    o = F.copy(); o[ym:ym + n, xr + dx:xr + dx + n] = 0; return o
        H, T = (0, 64, 64, 112), (136, 208, 151, 232)
        for name, o, k in (('H-zone+1y', copy(H, 0, 1), '[H]'), ('H-zone+1x', copy(H, 1, 0), '[H]'), ('H-blob2', blob(H, 2), '[H]'),
                           ('H-blob3', blob(H, 3), '[H]'), ('T-window+1y', copy(T, 0, 1), '[T]'), ('T-blob2', blob(T, 2), '[T]'),
                           ('T-blob3', blob(T, 3), '[T]')):
            r = dict(repl); r[X] = o
            out.append((lane, secs, f'r4-L5-zone-{name}', r, {(S5, f'{k} counter-suffix ios'): 'FAIL'}))
    return out
