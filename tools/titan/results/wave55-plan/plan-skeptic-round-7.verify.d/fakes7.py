#!/usr/bin/env python3
# fakes7.py — plan-skeptic round 7 (VERIFY of fix-r6): an independent fake generator. Written without reusing fakes5.py /
# added_ink*.py builders; it reuses only probes-teeth.py's make_run (the symlink-tree plumbing every round used) and the
# installed residual-read.py declarations (to PLACE paint inside / outside the declared zones — never to judge it).
#
# Every fake is built from a key cell's FIXED picture (the standing set's: L1 Rm replays, L2 wave54-probe U1 captures + the
# clip-3 U2 replay, L3 positioned-parent-slot-replay U1U2/U1/U2, L4 ua-heading-margin-replay C and U1 arms, L5 ok-all, L6
# U2 / U1 replays) and judged by the REAL geometry-gate.py on a symlink-tree fake run with --base wave55-open (PLAN's
# registered base; byte-identical to wave54-final on all 35 key cells).
#
# Classes (rounds 1-5): S target ink removed / moved ±2-3 px / segments shortened / count-preserving rearrangements;
# A strictly-additive specks + lines in-zone (class colour, black, greys incl. faint, ramp / palette colours);
# F faint copies (α .5 / .25 / .15 / .08) in-zone and free; G duplicated glyph (full / faint, in-zone / out); W whole faint
# ghost; O background-only paint OUTSIDE the zones in every colour (incl. faint, grey, near-white, class colours) as specks,
# lines and blocks, far / near / on a non-white flat; T targeted (window palette paint, AA-ring own-colour lines, a
# relocated "other" square, child fill over the band, overlap revert + red elsewhere, vacate-class paint).
import os, sys, json, time, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *

WHITE = np.array([255, 255, 255], np.int16)
grow = RR.grow


def dist_map(m, cap=48):
    """8-neighbour distance (px) from mask m, capped."""
    d = np.full(m.shape, cap + 1, np.int16); d[m] = 0; cur = m.copy()
    for g in range(1, cap + 1):
        cur = grow(cur, 1); d[cur & (d > cap)] = g
    return d


def comps8(m):
    lab, n = RR._components(m)
    return lab, n


class Ctx:
    def __init__(self, lane, setn, key3, F):
        self.lane, self.setn, self.key3 = lane, setn, key3
        sec, plat, test = key3
        self.cell = cellkey(sec, plat, test)
        self.F, self.B = F.copy(), base_arr(self.cell)
        self.spec = RR.SPECS[self.cell]
        H, W, _ = F.shape; self.H, self.W = H, W
        self.win = np.zeros((H, W), bool)
        for x0, y0, x1, y1 in self.spec.get('windows', []):
            self.win[y0:y1, x0:x1] = True
        self.Zc = RR.zone(F, self.spec['zone'], True) | self.win
        self.Zb = RR.zone(self.B, self.spec['zone'], False)
        self.Zall = self.Zc | self.Zb
        self.white = (F == 255).all(axis=2)
        self.ink = F.min(axis=2) < 250
        self.kind = 'mask' if self.spec['zone'][0][0] == 'mask' else 'region'      # band / rect zones hold white canvas in-zone
        self.K = self._class()
        self.dZ = dist_map(self.Zall)
        self.dInk = dist_map(~self.white)
        ys, xs = np.nonzero(self.K)
        self.kb = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else None
        self.col = self._class_colour()

    def _class(self):
        F, c, L = self.F, self.cell, self.lane
        m = np.zeros(self.F.shape[:2], bool)
        if L == 'L1':
            y0 = PT.L1_DIV0[c.split('character-')[1][:3]]; m[y0:, :140] = self.ink[y0:, :140]
        elif L == 'L2':
            m = RR.PRED['green'](F)
        elif 'dir-style' in c:
            for y0 in (116, 136, 176):
                m[y0:y0 + 20] = self.ink[y0:y0 + 20]
        elif 'bidi' in c:
            m = RR.PRED['blue3'](F)
        elif 'anchor-position' in c:
            m = RR.PRED['green3'](F)
        elif 'dynamic-offset' in c:
            m = RR.PRED['magenta'](F)
        elif L == 'L4':
            m = self.ink & RR.zone(F, self.spec['zone'], True)
        elif L == 'L5':
            m = self.ink & self.Zc
        elif 'clip-rect' in c:
            m = RR.PRED['red6'](F)
        else:
            m = RR.PRED[self.spec['ink'][0]](F)
        return m

    def _class_colour(self):
        px = self.F[self.K & (self.F.min(axis=2) < 200) | (self.K & (self.kind == 'mask'))]
        if not len(px):
            return (0, 0, 0)
        v, n = np.unique(px.reshape(-1, 3), axis=0, return_counts=True)
        return tuple(int(x) for x in v[np.argmax(n)])

    # ── placement ────────────────────────────────────────────────────────────────────────────────────────────────────────
    def foot_ok(self, allowed, x, y, w, h):
        return 0 <= x and 0 <= y and x + w <= self.W and y + h <= self.H and allowed[y:y + h, x:x + w].all()

    def place(self, allowed, w, h, score, step=1, want='max'):
        """Top-left of a w×h footprint inside `allowed`, maximising (or minimising) min(score) over the footprint (vectorised;
        ties: the first in raster order)."""
        H, W = allowed.shape
        if w > W or h > H:
            return None
        ii = np.zeros((H + 1, W + 1), np.int32); ii[1:, 1:] = allowed.astype(np.int32).cumsum(0).cumsum(1)
        cnt = ii[h:, w:] - ii[:-h, w:] - ii[h:, :-w] + ii[:-h, :-w]
        ok = cnt == w * h
        if not ok.any():
            return None
        sc = score.astype(np.int32)
        r = sc[:, 0:W - w + 1].copy()
        for k in range(1, w):
            r = np.minimum(r, sc[:, k:W - w + 1 + k])
        m = r[0:H - h + 1].copy()
        for k in range(1, h):
            m = np.minimum(m, r[k:H - h + 1 + k])
        m = np.where(ok, m if want == 'max' else -m, np.iinfo(np.int32).min)
        y, x = np.unravel_index(int(np.argmax(m)), m.shape)
        return int(x), int(y)

    def pos_out(self, w, h, where):
        free = self.white & ~self.Zall
        if where == 'far':
            return self.place(free & (self.dInk >= 3), w, h, np.minimum(self.dZ, 40), want='max')
        if where == 'near':          # just outside the declared zone: min distance 1 … 3 px, on white canvas
            allowed = free & (self.dZ >= 1)
            sc = np.where(self.dZ <= 3, 100 - self.dZ, 0)
            return self.place(allowed, w, h, sc, want='max')
        if where == 'flat':          # on a non-white flat colour outside the zone (a parent box, the clip's blue field)
            q = self.F.reshape(-1, 3); nonw = ~self.white & ~grow(self.Zall, 2)
            if nonw.sum() < 200:
                return None
            v, n = np.unique(self.F[nonw].reshape(-1, 3), axis=0, return_counts=True)
            col = v[np.argmax(n)]
            if n.max() < 200:
                return None
            flat = (self.F == col).all(axis=2) & ~grow(self.Zall, 2)
            flat = ~grow(~flat, 1)
            return self.place(flat, w, h, np.minimum(self.dZ, 40), want='max')
        return None

    def pos_in(self, w, h, where):
        """In-zone white canvas (band / rect zones), 'open' (≥ 3 px from ink) or 'edge' (touching the class ink)."""
        allowed = self.white & self.Zc & ~self.win
        if not allowed.any():
            return None
        if where == 'open':
            return self.place(allowed & (self.dInk >= 3), w, h, np.minimum(self.dInk, 40), step=1, want='max')
        dK = dist_map(self.K, 4)
        return self.place(allowed, w, h, np.where(dK == 1, 10, 0) - np.minimum(dK, 4), step=1, want='max')


def paint(F, x, y, w, h, col, alpha=1.0):
    o = F.copy()
    if alpha >= 1.0:
        o[y:y + h, x:x + w] = col
    else:
        o[y:y + h, x:x + w] = np.round(o[y:y + h, x:x + w] * (1 - alpha) + np.array(col) * alpha).astype(np.int16)
    return o


def shift_region(F, dx, dy, box, bg):
    return PT.shift(F, dx, dy, box, bg)


# ── colours ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
C_OUT = {'black': (0, 0, 0), 'grey128': (128, 128, 128), 'grey204': (204, 204, 204), 'grey230': (230, 230, 230), 'grey245': (245, 245, 245),
         'grey252': (252, 252, 252), 'white254': (254, 254, 254), 'red': (255, 0, 0), 'lime': (0, 255, 0), 'blueFF': (0, 0, 255),
         'yellowFF': (255, 255, 0), 'magenta': (255, 0, 255), 'cyan': (0, 255, 255), 'green080': (0, 128, 0), 'teal': (0, 128, 128),
         'blue66': (102, 204, 255), 'pink': (255, 127, 255), 'tint-g': (250, 255, 250), 'tint-r': (255, 250, 250)}


def faint(col, a):
    return tuple(int(round(255 * (1 - a) + c * a)) for c in col)


def in_colours(cx):
    """The in-zone paint colours for a cell's declared anti-aliasing."""
    aa, out = cx.spec.get('aa'), {}
    if cx.kind == 'region':
        out.update({'black': (0, 0, 0), 'grey100': (100, 100, 100), 'grey150': (150, 150, 150), 'grey199': (199, 199, 199),
                    'grey200': (200, 200, 200), 'grey215': (215, 215, 215), 'grey230': (230, 230, 230), 'grey245': (245, 245, 245),
                    'red': (255, 0, 0), 'tint-b': (200, 200, 255)})
        if aa == 'ramp':
            for nm, c in (('lime', (0, 255, 0)), ('blue', (0, 0, 255))):
                for a in (1.0, 0.5, 0.25, 0.2, 0.15, 0.08):
                    out[f'{nm}{int(a * 100)}'] = faint(c, a)
    else:
        out['class'] = cx.col
    return out


# ── families: each a list of (name, fn(cx) -> fake or None) ──────────────────────────────────────────────────────────────
def fam_S():
    """Standing classes, generic: removed / moved ±2-3 / shortened / count-preserving rearrangements of the class ink."""
    def bgfill(cx, m):
        o = cx.F.copy()
        if cx.kind == 'region' or cx.lane in ('L1', 'L4', 'L5'):
            o[m] = 255
        else:                       # a mask class sits on a parent / field: refill with the colour around it
            ring = grow(cx.K, 2) & ~grow(cx.K, 1)
            v, n = np.unique(cx.F[ring].reshape(-1, 3), axis=0, return_counts=True) if ring.any() else (np.array([[255, 255, 255]]), np.array([1]))
            o[m] = v[np.argmax(n)]
        return o
    def box(cx, pad=1):
        x0, y0, x1, y1 = cx.kb; return (max(0, x0 - pad), max(0, y0 - pad), min(cx.W, x1 + 1 + pad), min(cx.H, y1 + 1 + pad))
    def bgcol(cx):
        o = bgfill(cx, np.zeros_like(cx.K) | cx.K)
        return tuple(int(v) for v in o[cx.K][0]) if cx.K.any() else (255, 255, 255)
    def rm_all(cx):
        return bgfill(cx, cx.K) if cx.kb else None
    def rm_half(cx):
        if not cx.kb: return None
        x0, y0, x1, y1 = cx.kb; m = cx.K.copy(); m[:, :(x0 + x1) // 2] = False; return bgfill(cx, m)
    def hole3(cx):
        if not cx.kb: return None
        core = cx.K & ~grow(~cx.K, 1)
        ys, xs = np.nonzero(core)
        if not len(xs): return None
        i = len(xs) // 2; m = np.zeros_like(cx.K); m[ys[i] - 1:ys[i] + 2, xs[i] - 1:xs[i] + 2] = True; return bgfill(cx, m & cx.K)
    def mv(dx, dy):
        def f(cx):
            if not cx.kb: return None
            return shift_region(cx.F, dx, dy, box(cx), bgcol(cx))
        return f
    def cut_r(cx):
        if not cx.kb: return None
        x0, y0, x1, y1 = cx.kb; m = cx.K.copy(); m[:, :x1 - 2] = False; return bgfill(cx, m)
    def cut_b(cx):
        if not cx.kb: return None
        x0, y0, x1, y1 = cx.kb; m = cx.K.copy(); m[:y1 - 2] = False; return bgfill(cx, m)
    def mirror(cx):
        if not cx.kb: return None
        x0, y0, x1, y1 = box(cx, 0); o = cx.F.copy(); o[y0:y1, x0:x1] = cx.F[y0:y1, x0:x1][:, ::-1]; return o
    def swapv(cx):
        if not cx.kb: return None
        x0, y0, x1, y1 = box(cx, 0); h = (y1 - y0) // 2
        if h < 2: return None
        o = cx.F.copy(); a, b = cx.F[y0:y0 + h, x0:x1].copy(), cx.F[y1 - h:y1, x0:x1].copy(); o[y0:y0 + h, x0:x1] = b; o[y1 - h:y1, x0:x1] = a; return o
    return [('S-removed-all', rm_all), ('S-removed-right-half', rm_half), ('S-hole3', hole3), ('S-moved+2x', mv(2, 0)), ('S-moved-3x', mv(-3, 0)),
            ('S-moved+2y', mv(0, 2)), ('S-moved-3y', mv(0, -3)), ('S-cut-right3', cut_r), ('S-cut-bottom3', cut_b), ('S-mirrored', mirror),
            ('S-halves-swapped', swapv)]


IN_NAMES = ['class', 'black', 'grey100', 'grey150', 'grey199', 'grey200', 'grey215', 'grey230', 'grey245', 'red', 'tint-b'] + \
           [f'{c}{a}' for c in ('lime', 'blue') for a in (100, 50, 25, 20, 15, 8)]


def fam_A():
    """Strictly additive in-zone: 2×2 / 3×3 specks (edge / open), 10- and 30-px 1-px lines, per in-zone colour."""
    out = []
    for cn in IN_NAMES:
        for shape, (w, h) in (('speck2', (2, 2)), ('speck3', (3, 3)), ('line10', (10, 1)), ('line30', (30, 1))):
            for where in (('edge', 'open') if shape.startswith('speck') else ('open',)):
                def f(cx, cn=cn, w=w, h=h, where=where):
                    cols = in_colours(cx)
                    if cn not in cols: return None
                    if cx.kind == 'region':
                        p = cx.pos_in(w, h, where)
                    else:               # class colour of a mask zone: anywhere on the canvas (the paint makes its own zone)
                        p = cx.pos_out(w, h, 'far') if where == 'open' else None
                        if where == 'edge' and cx.kb:
                            free = cx.white | ~grow(cx.K, 0)
                            dK = dist_map(cx.K, 4); allowed = ~cx.K & (dK >= 1)
                            p = cx.place(allowed, w, h, np.where(dK == 1, 10, 0) - np.minimum(dK, 4), step=1, want='max')
                    if p is None: return None
                    return paint(cx.F, p[0], p[1], w, h, cols[cn])
                out.append((f'A-{cn}-{shape}-{where}', f))
    return out


def fam_F():
    """Faint copies of the class ink, strictly additive (only onto white canvas), in-zone-restricted and free."""
    out = []
    for a in (0.5, 0.25, 0.15, 0.08):
        for dx, dy in ((1, 0), (2, 1), (0, 5), (-3, 3)):
            for scope in ('inzone', 'free'):
                def f(cx, a=a, dx=dx, dy=dy, scope=scope):
                    if not cx.kb: return None
                    ys, xs = np.nonzero(cx.K); ty, tx = ys + dy, xs + dx
                    ok = (ty >= 0) & (ty < cx.H) & (tx >= 0) & (tx < cx.W)
                    ys, xs, ty, tx = ys[ok], xs[ok], ty[ok], tx[ok]
                    keep = cx.white[ty, tx]
                    if scope == 'inzone':
                        keep &= (cx.Zc | cx.Zb)[ty, tx]
                    if not keep.any(): return None
                    o = cx.F.copy()
                    o[ty[keep], tx[keep]] = np.round(255 * (1 - a) + cx.F[ys[keep], xs[keep]] * a).astype(np.int16)
                    return o
                out.append((f'F-faint{int(a * 100)}-{dx:+d}{dy:+d}-{scope}', f))
    return out


def fam_G():
    """A duplicated glyph / class component (full ink and faint), pasted in-zone (white canvas) and out of zone."""
    out = []
    def pick(cx):
        lab, n = comps8(cx.K)
        if not n: return None
        sizes = np.bincount(lab.ravel())[1:]
        good = [i + 1 for i, s in enumerate(sizes) if 12 <= s <= 400]
        i = good[len(good) // 2] if good else 1 + int(np.argmax(sizes))
        ys, xs = np.nonzero(lab == i)
        x0, y0, x1, y1 = max(0, xs.min() - 1), max(0, ys.min() - 1), min(cx.W, xs.max() + 2), min(cx.H, ys.max() + 2)
        if (x1 - x0) * (y1 - y0) > 3000:
            x1, y1 = min(x1, x0 + 40), min(y1, y0 + 40)
        return x0, y0, x1, y1
    for a in (1.0, 0.25, 0.15):
        for where in ('inzone', 'out'):
            def f(cx, a=a, where=where):
                b = pick(cx)
                if b is None: return None
                x0, y0, x1, y1 = b; w, h = x1 - x0, y1 - y0
                p = (cx.pos_in(w, h, 'open') if cx.kind == 'region' else None) if where == 'inzone' else cx.pos_out(w, h, 'far')
                if p is None: return None
                src = cx.F[y0:y1, x0:x1]; m = ~(src == 255).all(axis=2)
                o = cx.F.copy(); dst = o[p[1]:p[1] + h, p[0]:p[0] + w]
                dst[m] = np.round(255 * (1 - a) + src[m] * a).astype(np.int16)
                return o
            out.append((f'G-dup{int(a * 100)}-{where}', f))
    return out


def fam_W():
    """Whole faint ghost: the whole fixed picture blended with itself shifted."""
    out = []
    for a in (0.5, 0.25, 0.12, 0.06):
        for dx, dy in ((3, 2), (0, 5), (1, 0)):
            def f(cx, a=a, dx=dx, dy=dy):
                S = PT.shift(cx.F, dx, dy, None, (255, 255, 255))
                return np.round(cx.F * (1 - a) + S * a).astype(np.int16)
            out.append((f'W-ghost{int(a * 100)}-{dx:+d}{dy:+d}', f))
    return out


def fam_O():
    """Background-only paint OUTSIDE every declared zone, every colour, specks / lines / blocks, far / near / on a flat."""
    out = []
    for cn, col in list(C_OUT.items()) + [('classcol', None), ('class50', 0.5), ('class15', 0.15)]:
        for shape, (w, h) in (('speck3', (3, 3)), ('line30', (30, 1)), ('block12', (12, 12))):
            for where in ('far', 'near', 'flat'):
                if where == 'near' and shape == 'block12':
                    continue
                def f(cx, cn=cn, col=col, w=w, h=h, where=where):
                    c = col if isinstance(col, tuple) else cx.col if col is None else faint(cx.col, col)
                    p = cx.pos_out(w, h, where)
                    if p is None: return None
                    o = paint(cx.F, p[0], p[1], w, h, c)
                    return o if (o != cx.F).any() else None
                out.append((f'O-{cn}-{shape}-{where}', f))
    return out


def fam_T():
    """Targeted: the read's own allowances."""
    out = []
    # T1 windows: palette / blend paint grown from the window's recorded change (connected to it), on pixels the fixed
    # picture leaves equal to the base and that are not class ink
    pal = {'white': (255, 255, 255), 'green': (0, 128, 0), 'pink': (255, 127, 255), 'blue66': (102, 204, 255), 'grey204': (204, 204, 204),
           'yellowFF': (255, 255, 0), 'black': (0, 0, 0), 'blueFF': (0, 0, 255), 'blend-blue30': faint((0, 0, 255), 0.3),
           'blend-yel50': faint((255, 255, 0), 0.5), 'blend-blue66-50': faint((102, 204, 255), 0.5), 'blend-grey204-50': faint((204, 204, 204), 0.5)}
    for cn, col in pal.items():
        for g in (1, 2, 4):
            for which in ('one', 'all'):
                def f(cx, col=col, g=g, which=which):
                    ws = cx.spec.get('windows', [])
                    if not ws: return None
                    d = (cx.F != cx.B).any(axis=2); o = cx.F.copy(); hit = False
                    for x0, y0, x1, y1 in (ws[:1] if which == 'one' else ws):
                        w = np.zeros_like(d); w[y0:y1, x0:x1] = True
                        tgt = grow(d & w, g) & w & ~d & ~cx.K & ~(cx.F == np.array(col)).all(axis=2)
                        if tgt.any():
                            o[tgt] = col; hit = True
                    return o if hit else None
                out.append((f'T-window-{cn}-grow{g}-{which}', f))
    # T2 L2: own-colour green on the 1-px ring outside the box (the probe's AA band), along the bottom edge
    for L in (4, 10, 20, 30, 40):
        def f(cx, L=L):
            if cx.lane != 'L2' or not cx.kb: return None
            x0, y0, x1, y1 = cx.kb; o = cx.F.copy(); cols = [x for x in range(x0, x1 + 1) if cx.K[y1, x] and not cx.K[y1 + 1, x]]
            mid = len(cols) // 2; sel = cols[max(0, mid - L // 2):max(0, mid - L // 2) + L]
            if not sel: return None
            for x in sel:
                o[y1 + 1, x] = (0, 128, 0)
            return o
        out.append((f'T-L2-ring-bottom-{L}', f))
    # T3 L3 stack: count-preserving — one "other" green square relocated 24 px right onto white canvas
    for dx, dy in ((24, 0), (0, 30), (-40, 10)):
        def f(cx, dx=dx, dy=dy):
            if 'anchor-position' not in cx.cell: return None
            keep = np.zeros_like(cx.K)
            for x, y in PT.L3_CELLS:
                keep[y - 2:y + 12, x - 2:x + 12] = True
            lab, n = comps8(cx.K & ~keep)
            for i in range(1, n + 1):
                ys, xs = np.nonzero(lab == i)
                ty, tx = ys + dy, xs + dx
                if ty.min() < 0 or tx.min() < 0 or ty.max() >= cx.H or tx.max() >= cx.W: continue
                if not cx.white[ty.min() - 2:ty.max() + 3, tx.min() - 2:tx.max() + 3].all(): continue
                o = cx.F.copy(); col = cx.F[ys, xs].copy(); o[ys, xs] = 255; o[ty, tx] = col; return o
            return None
        out.append((f'T-L3-stack-other-square-moved{dx:+d}{dy:+d}', f))
    # T4 L6 [X]: child fill painted over the parent band where no child is (inside the fill bbox), 3 / 6 / 9 % of the band
    for frac in (0.03, 0.06, 0.09):
        def f(cx, frac=frac):
            if cx.lane != 'L6' or 'clip-rect' in cx.cell or not cx.kb: return None
            band = cx.F.sum(axis=2) < 150
            x0, y0, x1, y1 = cx.kb; inb = np.zeros_like(band); inb[y0:y1 + 1, x0:x1 + 1] = True
            cand = band & inb & ~grow(cx.K, 2)
            ys, xs = np.nonzero(cand)
            k = int(frac * band.sum())
            if k < 3 or len(xs) < k: return None
            o = cx.F.copy(); o[ys[:k], xs[:k]] = cx.col; return o
        out.append((f'T-L6-X-fill-over-band-{int(frac * 100)}pct', f))
    # T5 L6 [PO]: k overlap px reverted to the base's blue + k red px added inside the red bbox on white (count-preserving)
    for k in (4, 6, 8):
        def f(cx, k=k):
            if 'clip-rect' not in cx.cell: return None
            o = cx.F.copy(); n = 0
            for y in (166, 167):
                for x in range(200, 267):
                    if n < k and RR.PRED['red6'](cx.F[y:y + 1, x:x + 1])[0, 0] and not RR.PRED['red6'](cx.B[y:y + 1, x:x + 1])[0, 0]:
                        o[y, x] = cx.B[y, x]; n += 1
            x0, y0, x1, y1 = cx.kb; m = 0
            for y in range(y0 + 40, y1):
                for x in range(x0 + 20, x1 - 20):
                    if m < n and cx.white[y, x] and cx.white[y - 1:y + 2, x - 1:x + 2].all():
                        o[y, x] = (255, 0, 0); m += 1
                if m >= n: break
            return o if n else None
        out.append((f'T-L6-PO-overlap-revert-plus-red-{k}', f))
    # T6 bidi: vacate-class yellow where the base's blue run left (white in the fixed picture), not beside persistent blue
    def f(cx):
        if 'bidi' not in cx.cell: return None
        pers = grow(RR._persistent(cx.F, cx.B, cx.spec['zone']), 1)
        tgt = cx.Zb & ~cx.Zc & cx.white & ~pers
        if not tgt.any(): return None
        o = cx.F.copy(); o[tgt] = (255, 255, 0); return o
    out.append(('T-bidi-vacated-yellow', f))
    # T7 L1 / L4 / L5 / dir rows: a faint copy of the WHOLE class ink at min ≥ 200, moved ±2-3 px, painted everywhere (not
    # only on white) — faint ink over glyph edges
    for a in (0.2, 0.15, 0.1):
        for dx, dy in ((2, 0), (-3, 0), (0, 2)):
            def f(cx, a=a, dx=dx, dy=dy):
                if cx.kind != 'region' or not cx.kb: return None
                ys, xs = np.nonzero(cx.K & (cx.F.min(axis=2) < 128)); ty, tx = ys + dy, xs + dx
                ok = (ty >= 0) & (ty < cx.H) & (tx >= 0) & (tx < cx.W) & cx.Zc[np.clip(ty, 0, cx.H - 1), np.clip(tx, 0, cx.W - 1)]
                o = cx.F.copy(); src = cx.F[ys[ok], xs[ok]]
                tgt = o[ty[ok], tx[ok]]
                o[ty[ok], tx[ok]] = np.minimum(tgt, np.round(255 * (1 - a) + src * a)).astype(np.int16)
                return o
            out.append((f'T-faintdark{int(a * 100)}-{dx:+d}{dy:+d}-overpaint', f))
    return out


FAMILIES = fam_S() + fam_A() + fam_F() + fam_G() + fam_W() + fam_O() + fam_T()


def contexts():
    """[(lane, setname, sections, repl, {key3: Ctx})] for every fixed set."""
    out = []
    for lane, setn, secs, repl in fixed_sets():
        cxs = {k3: Ctx(lane, setn, k3, F) for k3, F in repl.items() if cellkey(*k3) in RR.SPECS}
        out.append((lane, setn, secs, repl, cxs))
    return out


if __name__ == '__main__':
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--scratch', required=True); ap.add_argument('--out', required=True)
    ap.add_argument('--only', default=None); ap.add_argument('--shard', default='0/1'); ap.add_argument('--list', action='store_true')
    a = ap.parse_args()
    fams = [x for x in FAMILIES if not a.only or any(x[0].startswith(p) for p in a.only.split(','))]
    if a.list:
        print(len(FAMILIES), 'families;', len(fams), 'selected'); sys.exit(0)
    si, sn = map(int, a.shard.split('/'))
    fams = fams[si::sn]
    CX = contexts()
    os.makedirs(a.scratch, exist_ok=True)
    rec = open(a.out, 'a')
    for fname, fn in fams:
        t0 = time.time()
        # group the fixed sets into runs: L4 arms C and U1 share cells — U1 goes in a run of its own
        groups = [[s for s in CX if not (s[0] == 'L4' and s[1] == 'U1')], [s for s in CX if s[0] == 'L4' and s[1] == 'U1']]
        for gi, sets in enumerate(groups):
            repl, faked = {}, {}
            for lane, setn, secs, rp, cxs in sets:
                repl.update(rp)
                for k3, cx in cxs.items():
                    try:
                        o = fn(cx)
                    except Exception as e:      # a builder that cannot apply to this cell (recorded, not a fake)
                        o = None
                    if o is None:
                        continue
                    o = np.clip(o, 0, 255).astype(np.uint8).astype(np.int16)
                    n = int((o != cx.F).any(axis=2).sum())
                    if n == 0:
                        continue
                    ro, rv, rn, _ = RR.read_arrays(o, cx.B, cx.spec)
                    repl[k3] = o; faked[k3] = (cx, n, (ro, rv, rn))
            if not faked:
                continue
            rid = make_run(a.scratch, f'{fname}-g{gi}', repl)
            secs = ','.join(sorted({k3[0] for k3 in faked}))
            lanes = ','.join(sorted({cx.lane for cx, _, _ in faked.values()}))
            rc, rows, so = gate(rid, lanes, secs, os.path.join(a.scratch, f'gate-{fname}-g{gi}.json'))
            for k3, (cx, n, res) in faked.items():
                ks = KEYS.get(cx.cell, [])
                kv = {f'{s} {k}': (rows.get((s, k), {}).get('verdict', 'NO ROW'), (rows.get((s, k), {}).get('residual') or {}).get('state'),
                                   rows.get((s, k), {}).get('line', '')[-160:]) for _, s, k, _ in ks}
                rec.write(json.dumps({'fake': fname, 'group': gi, 'set': f'{cx.lane}-{cx.setn}', 'cell': cx.cell, 'px': n, 'res': res,
                                      'keys': kv, 'gateExit': rc}, ensure_ascii=False) + '\n')
            rec.flush()
            shutil.rmtree(os.path.join(a.scratch, 'fakeruns', f'{fname}-g{gi}'), ignore_errors=True)
            try:
                os.remove(os.path.join(a.scratch, f'gate-{fname}-g{gi}.json'))
            except OSError:
                pass
        print(f'{fname} {time.time() - t0:.1f}s', flush=True)
