#!/usr/bin/env python3
# Round-5 VERIFIER's own strictly-additive fakes (written without reusing added_ink.py / added_ink4.py / fakes4.py builders).
#
# What it does. For every gating and pictureCorrectTally key of the generated expectations.json, its fixed picture (the
# standing set's `*fixed` cases: the briefs' replays / the wave54-probe device captures, merged when cases share pictures)
# gets ink ADDED inside the key's own READ ZONE — the region the key's probe reads with a non-pixel-identity read (a region
# a pixel-identity read covers cannot accept added ink by construction, so it is not faked):
#   L1  hyphenate-character-00x ios: x0-139 × y DIV0-599 (the probe's line scan and crops)
#   L2  every U1 / U2 capture: the whole capture (strict-green mask + fail ink, whole-picture)
#   L3  dir-style-02a android row6 / row7 / row9: that row's 20-px band; bidi-tab-001 rtl-rows: the four RTL bands;
#       anchor-position-multicol-nested-001 stack and the dynamic-offset keys: the whole capture
#   L4  [M] / [F] 005 / 006 / 014: y96-399 (the band scan); [M] 011: y112-399
#   L5  [H] ios x0-63 × y64-111; [C] ios / android x0-63 × y112-159; [T] ios x136-150 × y208-231 (the rest of row 9 is
#       pixel-identity); [I] 005 ios / android x200-389 × y95-151 (the rest of 005 is pixel-identity)
#   L6  [X] / [PO]: the whole capture
# Ink classes per zone: the zone's SOLID colours (≥ 15 px, not an anti-aliasing blend of two palette colours), the unit's
# own changed colour first, up to 5. Every fake is STRICTLY ADDITIVE: paint is laid on pixels that are flat palette colour
# and not already the class's ink — nothing is erased or moved; a faint copy is a 50 % alpha blend onto such pixels.
# Variants per (zone, class): 1-px / 2×2 / 3×3 specks touching a glyph edge (mid / last), 2×2 / 3×3 specks in a gap of the
# band and in the open zone, thin 1-px lines (strike-through, under, over, vertical in the widest gap, a 30-px line in the
# open), faint 50 % copies (the class ink moved +1 x, +1 y, (+2,+1), (+4,0), (0,+5), (−3,+3); every non-background pixel
# of the zone moved (+3,+2); a 3×3 faint speck in a gap and in the open zone; a faint strike-through and a faint 30-px line
# in the open zone; a faint duplicated glyph), and a
# duplicated glyph at full ink (after the band's end, in its widest gap, below itself).
# Each fake run (a symlink tree of wave54-final, probes-teeth.make_run) is read by the REAL geometry-gate.py on the generated
# expectations.json under the pinned interpreter (probes-teeth.gate). A fake is ACCEPTED by a key when that key — owning the
# faked zone, PASS on the fixed pictures — still PASSes; for a tally cell, when every key of the tally entry PASSes.
# Usage: python3.12 -B fakes5.py --scratch <abs dir> [--groups L1,L4] [--out file] [--json file]
import hashlib, importlib.util, json, os, sys
sys.dont_write_bytecode = True
import numpy as np

P = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))     # round-6 fix: the plan dir this copy sits under (the verifier's hard-coded the shared tree)
def load(n, p):
    s = importlib.util.spec_from_file_location(n, p); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt = load('pt', f'{P}/probes-teeth.py'); r2 = load('r2', f'{P}/probes-teeth-r2.py')
EXP = f'{P}/expectations.json'; PY = ['/opt/homebrew/bin/python3.12', '-B']
a = sys.argv[1:]
SC = a[a.index('--scratch') + 1]; assert os.path.isabs(SC) and not os.path.realpath(SC).startswith(os.path.realpath(os.path.join(P, '..', '..', '..', '..')) + '/')
ONLY = a[a.index('--groups') + 1].split(',') if '--groups' in a else None
OUT = a[a.index('--out') + 1] if '--out' in a else None
JOUT = a[a.index('--json') + 1] if '--json' in a else None
MAXC = 5

S1, S1T = 'hyphen-ios-linebox.geometry.py', 'hyphenate-character.geometry.py'
S2, S2b, S3 = 'compose-wpt-content-box-cb.geometry.py', 'cbb-android.geometry.py', 'positioned-parent-slot.geometry.py'
S4, S5, S6 = 'ua-heading-margin.geometry.py', 'counter-suffix-rows.geometry.py', 'android-paint-order.geometry.py'
WHOLE = [(0, 0, 390, 600)]


def zones_of(sec, plat, test):
    """[(zone name, boxes, [(script, key)] owning it)] of one native capture (see the banner)."""
    name = test.split('/')[-1]
    if test.startswith('hyphens/hyphenate-character-'):
        t = name[-3:]; y0 = {'001': 100, '003': 80, '004': 80}[t]
        return [('lines', [(0, y0, 140, 600)], [(S1, f'hyphen-ios-linebox {t} ios')] + ([(S1T, f'{name} ios')] if t != '004' else []))]
    if sec == 'css-flexbox' and name.startswith('abspos-autopos'):
        return [('whole', WHOLE, [(S2, f'{test} android')])]
    if name.startswith('backdrop-filter-nested-border-radius-clip'):
        return [('whole', WHOLE, [(S2, f'{name} android')] + ([(S2b, f'{name} android')] if name.endswith('-3') else []))]
    if name == 'dir-style-02a':
        return [(f'row{k}', [(0, 16 + 20 * (k - 1), 390, 36 + 20 * (k - 1))], [(S3, f'[S] dir-style-02a android row{k}')]) for k in (6, 7, 9)]
    if name == 'bidi-tab-001':
        return [('rtl-bands', [(0, y, 390, y + 20) for y in (86, 106, 186, 226)], [(S3, '[S] bidi-tab-001 android rtl-rows')])]
    if name == 'anchor-position-multicol-nested-001':
        return [('whole', WHOLE, [(S3, '[W] anchor-position-multicol-nested-001 android stack')])]
    if name.startswith('dynamic-offset'):
        return [('whole', WHOLE, [(S3, f'[{"W" if name.endswith("002") else "S"}] {name} android')])]
    if name.startswith('text-decoration-inset-'):
        y0 = 112 if name.endswith('011') else 96
        ks = [(S4, f'[M] {name} {plat}')] + ([(S4, f'[F] {name} {plat}')] if not name.endswith('011') and (plat == 'ios' or name.endswith('014')) else [])
        return [('heading', [(0, y0, 390, 400)], ks)]
    if name == 'counter-suffix':
        if plat == 'ios':
            return [('H', [(0, 64, 64, 112)], [(S5, '[H] counter-suffix ios')]), ('C', [(0, 112, 64, 160)], [(S5, '[C] counter-suffix ios')]),
                    ('T', [(136, 208, 151, 232)], [(S5, '[T] counter-suffix ios')])]
        return [('C', [(0, 112, 64, 160)], [(S5, '[C] counter-suffix android')])]
    if name == 'css3-counter-styles-005':
        return [('I', [(200, 95, 390, 152)], [(S5, f'[I] cjk-decimal/css3-counter-styles-005 {plat}')])]
    if 'staticpos' in name:
        return [('whole', WHOLE, [(S6, f'[X] {name} android')])]
    if name == 'backdrop-filter-clip-rect':
        return [('whole', WHOLE, [(S6, '[PO] backdrop-filter-clip-rect android')])]
    raise ValueError(test)


L1n = lambda u, v: np.abs(u.astype(np.int32) - np.asarray(v, np.int32)).sum(axis=-1)


def comps8(m):
    """8-connected components of a bool mask: [(ys, xs)]."""
    seen = np.zeros_like(m); out = []; H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if seen[y0, x0]:
            continue
        st = [(y0, x0)]; seen[y0, x0] = True; ys, xs = [], []
        while st:
            y, x = st.pop(); ys.append(y); xs.append(x)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not seen[yy, xx]:
                        seen[yy, xx] = True; st.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out


def dil(m, r=1, square=True):
    """Binary dilation by r px (square = 8-neighbour, else 4-neighbour)."""
    o = m.copy()
    for _ in range(r):
        p = o.copy()
        p[1:] |= o[:-1]; p[:-1] |= o[1:]; p[:, 1:] |= o[:, :-1]; p[:, :-1] |= o[:, 1:]
        if square:
            p[1:, 1:] |= o[:-1, :-1]; p[1:, :-1] |= o[:-1, 1:]; p[:-1, 1:] |= o[1:, :-1]; p[:-1, :-1] |= o[1:, 1:]
        o = p
    return o


def block_sum(m, n):
    """S[y, x] = number of True in m[y:y+n, x:x+n] (top-left anchored), shape (H-n+1, W-n+1)."""
    c = np.pad(m.astype(np.int32), ((1, 0), (1, 0))).cumsum(0).cumsum(1)
    return c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]


def blend(F, ys, xs, col, alpha):
    o = F.copy(); o[ys, xs] = np.round(alpha * np.asarray(col) + (1 - alpha) * F[ys, xs]).astype(F.dtype); return o


class Zone:
    """One faked zone of one capture: its palette, background and solid ink classes."""
    def nonbg_any(s):
        return bool(s.nonbg.any())

    def __init__(s, F, B, boxes):
        s.F = F; H, W, _ = F.shape; s.H, s.W = H, W
        s.Z = np.zeros((H, W), bool)
        for x0, y0, x1, y1 in boxes:
            s.Z[y0:y1, x0:x1] = True
        vals, cnt = np.unique(F[s.Z].reshape(-1, 3), axis=0, return_counts=True)
        order = np.argsort(-cnt); s.bg = vals[order[0]].astype(np.int32)
        pal = [s.bg]
        def is_blend(c):
            for i, p in enumerate(pal):
                for q in pal[i + 1:] + ([s.bg] if i else []):
                    d = (q - p).astype(float); nn = (d * d).sum()
                    if nn == 0:
                        continue
                    t = ((c - p) * d).sum() / nn
                    if 0.0 < t < 1.0 and np.abs(c - (p + t * d)).sum() <= 24:
                        return True
            return False
        sol = []
        for i in order[1:]:
            c = vals[i].astype(np.int32)
            if cnt[i] < 15 or L1n(c, s.bg) < 90 or any(L1n(c, p) <= 60 for p in pal) or is_blend(c):
                continue
            pal.append(c); sol.append(c)
        s.pal = pal
        s.flat = s.Z & (np.min(np.stack([L1n(F, p) for p in pal]), axis=0) <= 8)
        s.isbg = s.Z & (L1n(F, s.bg) <= 8)
        s.nonbg = s.Z & (L1n(F, s.bg) > 8)
        # the unit's own changed colour first (the diff's most frequent solid class), then the rest by count
        d = (F != B).any(axis=2) & s.Z if B.shape == F.shape else s.Z
        if d.any():
            cc = sorted(sol, key=lambda c: -int((L1n(F[d], c) <= 60).sum()))
            first = [c for c in cc if int((L1n(F[d], c) <= 60).sum()) >= 3][:1]
            sol = first + [c for c in sol if not any((c == f).all() for f in first)]
        s.classes = [(c, None) for c in sol[:MAXC]]
        if not s.classes and s.nonbg_any():
            # no solid colour (small anti-aliased glyphs, e.g. the CJK marker): the zone's farthest colour with >= 3 px is the
            # class, its ink every pixel at least half as far from the background (the glyph cores)
            dist = L1n(vals, s.bg); okc = cnt >= 3
            c = vals[okc][np.argmax(dist[okc])].astype(np.int32)
            s.classes = [(c, 0.5 * L1n(c, s.bg))]


class K:
    """One ink class c of one zone: its ink, free pixels, main band and glyphs."""
    def __init__(s, z, cls):
        c, thr = cls
        s.z = z; s.c = c; F = z.F
        s.ink = z.Z & ((L1n(F, c) <= 60) if thr is None else (L1n(F, z.bg) >= thr))
        s.free = z.flat & ~s.ink
        ys, xs = np.nonzero(s.ink)
        s.ok = len(ys) >= 3
        if not s.ok:
            return
        s.cy, s.cx = float(ys.mean()), float(xs.mean())
        # main band: the row run (blank gaps > 2 split) holding the most class ink
        rows = s.ink.any(axis=1); runs = []; cur = None; blank = 0
        for y in range(z.H):
            if rows[y]:
                cur = [y, y] if cur is None else [cur[0], y]; blank = 0
            elif cur is not None:
                blank += 1
                if blank > 2:
                    runs.append(cur); cur = None; blank = 0
        if cur:
            runs.append(cur)
        s.bands = []
        for y0, y1 in runs:
            cx = np.nonzero(s.ink[y0:y1 + 1].any(axis=0))[0]
            s.bands.append((y0, y1, int(cx.min()), int(cx.max()), int(s.ink[y0:y1 + 1].sum())))
        s.main = max(s.bands, key=lambda b: b[4])
        y0, y1, x0, x1, _ = s.main
        bm = np.zeros_like(s.ink); bm[y0:y1 + 1] = s.ink[y0:y1 + 1]
        s.glyphs = sorted([g for g in comps8(bm) if len(g[0]) >= 4], key=lambda g: g[1].min())

    def put(s, ys, xs, alpha=1.0, col=None):
        """Lay colour (default c) on the given pixels where they are free (flat palette colour, not class ink) and in the zone."""
        ys, xs = np.asarray(ys), np.asarray(xs)
        ok = (ys >= 0) & (ys < s.z.H) & (xs >= 0) & (xs < s.z.W)
        ys, xs = ys[ok], xs[ok]
        if col is None:
            keep = s.free[ys, xs]; ys, xs = ys[keep], xs[keep]
            return blend(s.z.F, ys, xs, s.c, alpha) if len(ys) else None
        keep = s.z.isbg[ys, xs]; ys, xs = ys[keep], xs[keep]
        if not len(ys):
            return None
        o = s.z.F.copy(); o[ys, xs] = np.round(alpha * col[keep] + (1 - alpha) * s.z.F[ys, xs]).astype(s.z.F.dtype); return o

    def block_pos(s, n, mode, region=None):
        """Top-left (y, x) of an n×n block wholly on free pixels: mode 'touch-mid' / 'touch-last' (adjacent to the class ink),
        'gap' (inside the main band's bbox, not touching any ink), 'open' (outside every band's bbox ±3, not touching)."""
        fr = block_sum(s.free, n) == n * n
        if mode.startswith('touch'):
            cand = fr & (block_sum(dil(s.ink, 1, square=False), n) > 0)
        else:
            cand = fr & (block_sum(dil(s.z.nonbg, 1), n) == 0)
            R = np.zeros_like(s.ink)
            if mode == 'gap':
                y0, y1, x0, x1, _ = s.main; R[y0:y1 + 1, x0:x1 + 1] = True
            else:
                R[:] = s.z.Z
                for y0, y1, x0, x1, _ in s.bands:
                    R[max(0, y0 - 3):y1 + 4, max(0, x0 - 3):x1 + 4] = False
            cand &= block_sum(R, n) == n * n
        ys, xs = np.nonzero(cand)
        if not len(ys):
            return None
        if mode == 'touch-last':
            i = np.lexsort((-ys, xs))[-1]                    # the rightmost free block touching the ink
        else:
            i = np.argmin((ys + n / 2 - s.cy) ** 2 + (xs + n / 2 - s.cx) ** 2)
        return int(ys[i]), int(xs[i])

    def speck(s, n, mode, alpha=1.0):
        p = s.block_pos(n, mode)
        if p is None:
            return None
        y, x = p; yy, xx = np.mgrid[y:y + n, x:x + n]
        return s.put(yy.ravel(), xx.ravel(), alpha)

    def widest_gap(s):
        y0, y1, x0, x1, _ = s.main
        cols = s.z.nonbg[y0:y1 + 1, x0:x1 + 1].any(axis=0); best, run = (None, 0), 0
        for k, col in enumerate(cols):
            run = 0 if col else run + 1
            if run > best[1]:
                best = (x0 + k - run // 2, run)
        return best

    def line(s, kind, alpha=1.0):
        y0, y1, x0, x1, _ = s.main
        if kind == 'strike':
            y = (y0 + y1) // 2; xs = np.arange(x0, x1 + 1); return s.put(np.full(len(xs), y), xs, alpha)
        if kind == 'under':
            xs = np.arange(x0, x1 + 1); return s.put(np.full(len(xs), y1 + 2), xs, alpha)
        if kind == 'over':
            xs = np.arange(x0, x1 + 1); return s.put(np.full(len(xs), y0 - 2), xs, alpha)
        if kind == 'vgap':
            xg, wd = s.widest_gap()
            if xg is None or wd < 3:
                return None
            ys = np.arange(y0, y1 + 1); return s.put(ys, np.full(len(ys), xg), alpha)
        if kind == 'open':
            # the zone's longest blank row run (no non-background pixel over the band's columns), its middle row, 30 px from x0
            zr = np.nonzero(s.z.Z.any(axis=1))[0]
            blank = [y for y in zr if not s.z.nonbg[y, max(0, x0 - 2):x1 + 3].any()]
            if not blank:
                return None
            runs = [];
            for y in blank:
                if runs and y == runs[-1][1] + 1:
                    runs[-1][1] = y
                else:
                    runs.append([y, y])
            r = max(runs, key=lambda r: r[1] - r[0]); y = (r[0] + r[1]) // 2
            xs = np.arange(x0, min(s.z.W, x0 + 30)); return s.put(np.full(len(xs), y), xs, alpha)
        raise ValueError(kind)

    def faint(s, dx, dy):
        ys, xs = np.nonzero(s.ink); return s.put(ys + dy, xs + dx, 0.5)

    def faint_whole(s, dx, dy):
        ys, xs = np.nonzero(s.z.nonbg); col = s.z.F[ys, xs].astype(np.int32)
        ty, tx = ys + dy, xs + dx; ok = (ty >= 0) & (ty < s.z.H) & (tx >= 0) & (tx < s.z.W)
        return s.put(ty[ok], tx[ok], 0.5, col[ok])

    def dup(s, where, alpha=1.0):
        G = s.glyphs
        if not G:
            return None
        y0, y1, x0, x1, _ = s.main
        if where == 'end':
            g = G[-1]; dx = x1 + 3 - int(g[1].min()); dy = 0
        elif where == 'below':
            g = G[len(G) // 2]; dx = 0; dy = int(g[0].max() - g[0].min()) + 2
        elif where == 'gap':
            xg, wd = s.widest_gap(); g = min(G, key=lambda g: g[1].max() - g[1].min())
            gw = int(g[1].max() - g[1].min()) + 1
            if xg is None or wd < gw + 2:
                return None
            dx = xg - gw // 2 - int(g[1].min()); dy = 0
        else:
            raise ValueError(where)
        o = s.put(g[0] + dy, g[1] + dx, alpha)
        if o is None or int((o != s.z.F).any(axis=2).sum()) < 0.5 * len(g[0]):
            return None                                          # not a glyph's copy: most of it would not land
        return o


VARIANTS = (['sp1-touch-mid', 'sp2-touch-mid', 'sp2-touch-last', 'sp3-touch-mid', 'sp3-touch-last', 'sp2-gap', 'sp3-gap', 'sp2-open', 'sp3-open',
             'ln-strike', 'ln-under', 'ln-over', 'ln-vgap', 'ln-open',
             'f50-ink+1x', 'f50-ink+1y', 'f50-ink+2x+1y', 'f50-ink+4x', 'f50-ink+5y', 'f50-ink-3x+3y', 'f50-all+3x+2y', 'f50-sp3-gap',
             'f50-ln-strike', 'f50-sp3-open', 'f50-ln-open', 'f50-dup-end', 'dup-end', 'dup-gap', 'dup-below'])


def build(k, v):
    if not k.ok:
        return None
    if v.startswith('sp'):
        n = int(v[2]); mode = v[4:]; return k.speck(n, mode)
    if v.startswith('ln-'):
        return k.line(v[3:])
    if v == 'f50-all+3x+2y':
        return k.faint_whole(3, 2)
    if v == 'f50-sp3-gap':
        return k.speck(3, 'gap', 0.5)
    if v == 'f50-ln-strike':
        return k.line('strike', 0.5)
    if v == 'f50-sp3-open':
        return k.speck(3, 'open', 0.5)
    if v == 'f50-ln-open':
        return k.line('open', 0.5)
    if v == 'f50-dup-end':
        return k.dup('end', 0.5)
    if v.startswith('f50-ink'):
        t = v[7:]; dx = dy = 0
        import re
        for num, ax in re.findall(r'([+-]\d+)([xy])', t):
            if ax == 'x':
                dx = int(num)
            else:
                dy = int(num)
        return k.faint(dx, dy)
    if v.startswith('dup-'):
        return k.dup(v[4:])
    raise ValueError(v)


def groups():
    out = {}
    for L, b in (('L1', pt.l1_cases), ('L2', pt.l2_cases), ('L3', pt.l3_cases), ('L4', pt.l4_cases), ('L5', pt.l5_cases), ('L6', pt.l6_cases)):
        for lane, secs, fid, repl, want in b() + r2.cases(pt, L):
            if not fid.endswith('fixed'):
                continue
            sig = (lane, secs, tuple(sorted((k, hashlib.sha1(v.tobytes()).hexdigest()) for k, v in repl.items())))
            g = out.setdefault(sig, [lane, secs, [], repl])
            g[2].append(fid)
    return [(lane, secs, '+'.join(f), repl) for lane, secs, f, repl in out.values()]


lines = []
def say(s):
    lines.append(s); print(s, flush=True)


e = json.load(open(EXP))
script = lambda pr: os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
GATING = {(script(pr), k) for l in e['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
TALLY = {}
for part in ('full', 'part', 'paintOrderOnly'):
    for ent in e['pictureCorrectTally'].get(part, []):
        ks = [tuple(k.split(' ', 1)) for k in ent['keys']]
        for k in ks:
            TALLY.setdefault(k, []).append((ent['cell'], ks))
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()[:16]
say(f'# round-5 verifier strictly-additive fakes (fakes5.py {sha(__file__)}…) on expectations.json {sha(EXP)}…, geometry-gate.py {sha(P + "/geometry-gate.py")}…; '
    'probes: ' + ' · '.join(f'{s} {sha(P + "/" + s)[:8]}' for s in (S1, S2, S2b, S3, S4, S5, S6)))
acc, nruns, nreads, untested, rec = [], 0, 0, [], []
for lane, secs, gid, repl in groups():
    if ONLY and lane not in ONLY:
        continue
    gtag = gid.replace('/', '_')[:60]
    rid = pt.make_run(SC, f'r5-{lane}-{gtag}-FIXED', repl)
    _, base_rows = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r5-{lane}-{gtag}-FIXED.json')
    caps = {}
    for x, F in repl.items():
        if x[1] == 'web':
            continue
        B = pt.cap('wave54-final', x[0], x[2], x[1])
        zs = []
        for zname, boxes, keys in zones_of(*x):
            own = [k for k in keys if (k in GATING or k in TALLY)]
            ok = [k for k in own if base_rows.get(k, {}).get('verdict') == 'PASS']
            for k in own:
                if k not in ok:
                    untested.append((lane, gid, k[1], base_rows.get(k, {}).get('verdict')))
            z = Zone(F, B, boxes)
            zs.append((zname, z, ok, [K(z, c) for c in z.classes]))
        caps[x] = (F, zs)
        say(f'\n## {lane} {gid} — {x[2].split("/")[-1]} {x[1]}: ' + '; '.join(
            f'zone {zn}: keys {[k[1] for k in ok]} classes {[tuple(int(v) for v in kk.c) for kk in ks]} bg {tuple(int(v) for v in z.bg)}' for zn, z, ok, ks in zs))
    nz = max(len(v[1]) for v in caps.values())
    for j in range(nz):
        for ci in range(MAXC):
            for v in VARIANTS:
                r = dict(repl); changed = {}
                for x, (F, zs) in caps.items():
                    if j >= len(zs) or ci >= len(zs[j][3]) or not zs[j][2]:
                        continue
                    o = build(zs[j][3][ci], v)
                    if o is None or (o == F).all():
                        continue
                    dd = (o != F).any(axis=2); ys, xs = np.nonzero(dd)
                    ni = int((((o.min(axis=2) < 200) & ~(F.min(axis=2) < 200)) | ((o.sum(axis=2) < 300) & ~(F.sum(axis=2) < 300))).sum())
                    # where the paint went: onto the zone's background, or over a pixel of another colour (an overpaint)
                    zz = caps[x][1][j][1]; onbg = int((dd & zz.isbg).sum())
                    r[x] = o; changed[x] = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()), int(dd.sum()), ni, onbg)
                if not changed:
                    continue
                nruns += 1
                tag = f'z{j}c{ci}-{v}'
                rid = pt.make_run(SC, f'r5-{lane}-{gtag}-{tag}', r)
                rc, rows = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r5-{lane}-{gtag}-{tag}.json')
                parts = []
                for x, bb in changed.items():
                    zname, z, ok, ks = caps[x][1][j]
                    col = tuple(int(t) for t in ks[ci].c)
                    nreads += len(ok)
                    passed = [k for k in ok if rows.get(k, {}).get('verdict') == 'PASS']
                    gat = [k for k in passed if k in GATING]
                    tal = []
                    for k in ok:
                        for cell, ks_ in TALLY.get(k, []):
                            if all(rows.get(kk, {}).get('verdict') == 'PASS' for kk in ks_) and cell not in tal:
                                tal.append(cell)
                    moved = sorted(k[1] for k, rr in rows.items() if rr['verdict'] != base_rows.get(k, {}).get('verdict'))
                    name = f'{x[2].split("/")[-1]} {x[1]} {zname}'
                    rec.append({'lane': lane, 'group': gid, 'variant': v, 'zone': j, 'class': col, 'capture': name, 'bbox': bb[:4], 'added': bb[4],
                                'newInk': bb[5], 'own': [k[1] for k in ok], 'gatingPass': [k[1] for k in gat], 'tallyPass': tal, 'moved': moved,
                                'ownQ': [f'{k[0]} {k[1]}' for k in ok], 'gatingPassQ': [f'{k[0]} {k[1]}' for k in gat],
                                'onBackground': bb[6], 'overpaint': bb[4] - bb[6], 'run': rid, 'sec': x[0], 'test': x[2], 'plat': x[1],
                                'lines': {k[1]: rows[k]['line'][-200:] for k in ok if k in rows}})
                    if gat or tal:
                        acc.append(rec[-1])
                        parts.append(f'{name}: ACCEPTED by {[k[1] for k in gat]}{" tally " + str(tal) if tal else ""} +{bb[4]} px (new ink {bb[5]}) bbox{bb[:4]}')
                    else:
                        parts.append(f'{name}: rejected (+{bb[4]})')
                say(f'  {tag:30} class {tuple(int(t) for t in caps[next(iter(changed))][1][j][3][ci].c)} rc {rc} | ' + ' | '.join(parts))
floor = lambda r: r['added'] <= 2
say(f'\n{nruns} fake runs; {nreads} reads of a faked zone by a key that owns it; ACCEPTANCES {len(acc)} ({sum(not floor(r) for r in acc)} adding > 2 px, '
    f'{sum(floor(r) for r in acc)} on the 2-px floor)')
for r in acc:
    say(f'  ACCEPT{"(<=2)" if floor(r) else ""} {r["lane"]} {r["group"]} z{r["zone"]} {r["variant"]} class {r["class"]} — {r["capture"]}: gating PASS {r["gatingPass"]}'
        + (f' tally {r["tallyPass"]}' if r['tallyPass'] else '') + f'; +{r["added"]} px (new ink {r["newInk"]}) bbox {r["bbox"]}')
for u in untested:
    say(f'  NO PASSING FIXED PICTURE: {u[0]} {u[1]} {u[2]} ({u[3]})')
if OUT:
    open(OUT, 'w').write('\n'.join(lines) + '\n')
if JOUT:
    json.dump(rec, open(JOUT, 'w'), indent=0)
sys.exit(1 if any(not floor(r) for r in acc) else 0)
