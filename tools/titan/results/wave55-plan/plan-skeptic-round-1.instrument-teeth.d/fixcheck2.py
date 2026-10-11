# fixcheck2.py — proposed measurements for the families fixcheck.py did not separate (L3 dir-style mirror / shift, L3 stack,
# L1, L4, L5): the fixed picture must stay OK, every fake that passed today must turn WRONG. Read-only.
import os
from lib import *

FK = f'{SCR}/fakes'
def fake(fid, sub, sec, test):
    p = f'{FK}/{fid}/{sub}__{cap_name(sec, test)}'
    return A(Image.open(p)) if os.path.exists(p) else None
def row(tag, name, val, ok):
    print(f'  {tag:22} {name:28} {val:60} → {"OK" if ok else "WRONG"}')

# ── L3 dir-style rows: the row's ink-column PROFILE (ink px per column) cross-correlated with the ref's over shifts -6..6:
#    best shift must be 0 (±1) and the peak normalised correlation >= 0.75 (mirror / gut / a 2-3 px shift fail) ──
ink = lambda a: a.min(axis=2) < 200
def prof(a, k):
    y0 = 16 + 20 * (k - 1); return ink(a[y0:y0 + 20]).sum(axis=0).astype(float)
def xcorr(p, q):
    best = (-9, None)
    for s in range(-6, 7):
        qs = np.roll(q, s); c = float((p * qs).sum()) / max(1e-9, np.sqrt((p * p).sum() * (qs * qs).sum()))
        best = max(best, (c, s))
    return best
print('L3 dir-style-02a rows: column-profile cross-correlation vs the ref: best shift 0±1 and peak >= 0.75')
R = A(ref('selectors', 'dir-style-02a'))
FX = A(Image.open(f'{P55}/positioned-parent-slot-replay/dir-style-02a-android-U1U2.png'))
for k in (6, 7, 9):
    for name, a in [('web wave54-final', A(cap('wave54-final', 'selectors', 'dir-style-02a', 'web'))),
                    ('android wave54-final (today)', A(cap('wave54-final', 'selectors', 'dir-style-02a', 'android'))), ('fixed U1U2', FX)] + \
                   [(f, fake(f'L3-dir-row{k}-{f}', 'android-screenshots', 'selectors', 'dir-style-02a')) for f in ('gut', 'mirror-words', 'text+2x', 'text+3x', 'text-3x')]:
        c, s = xcorr(prof(a, k), prof(R, k)); row(f'row{k}', name, f'peak {c:.3f} at shift {s:+d}', c >= 0.75 and abs(s) <= 1)

# ── L3 stack: the 1-px RING around each 10x10 cell (outside it) matches the ref's ring green count ±4 ──
g2 = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)
CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))
def rings(a):
    out = []
    for x, y in CELLS:
        blk = g2(a[y - 1:y + 11, x - 1:x + 11]).copy(); blk[1:11, 1:11] = False; out.append(int(blk.sum()))
    return out
print('L3 stack: + the 1-px ring outside each cell has the ref\'s green count ±4 (the square ends where the ref\'s does)')
rr = rings(A(ref('css-anchor-position', 'anchor-position-multicol-nested-001')))
for name, a in [('web wave54-final', A(cap('wave54-final', 'css-anchor-position', 'anchor-position-multicol-nested-001', 'web'))),
                ('fixed U1', A(Image.open(f'{P55}/positioned-parent-slot-replay/anchor-position-multicol-nested-001-android-U1.png')))] + \
               [(f, fake(f'L3-stack-{f}', 'android-screenshots', 'css-anchor-position', 'anchor-position-multicol-nested-001')) for f in ('grown30', 'slab', 'dup-stacked')]:
    r = rings(a); row('stack', name, f'rings {r} (ref {rr})', all(abs(u - v) <= 4 for u, v in zip(r, rr)))

# ── L1: every line's ink crop equals the SAME line of the base run's iOS capture translated by whole rows
#    (x unchanged; normalised correlation >= 0.97) — NB-ios moves line boxes, never glyphs ──
core = lambda a: a.max(axis=2) < 110
def lines(a, y0):
    m = core(a[:, :140]); out, cur, blank = [], None, 0
    for y in range(y0, a.shape[0]):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            cur = [y, y] if cur is None else [cur[0], y]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    return out
def crop(a, ln):
    t, b = ln; return 255.0 - a[t - 3:b + 4, :140].min(axis=2).astype(float)
def same_lines(a, base, y0):
    la, lb = lines(a, y0), lines(base, y0)
    if len(la) != len(lb):
        return 0.0, f'{len(la)} lines vs base {len(lb)}'
    worst = 1.0
    for x, y in zip(la, lb):
        p, q = crop(a, x), crop(base, y); h = min(len(p), len(q)); p, q = p[:h], q[:h]
        c = float((p * q).sum()) / max(1e-9, np.sqrt((p * p).sum() * (q * q).sum())); worst = min(worst, c)
    return worst, f'{len(la)} lines, worst line correlation {worst:.3f}'
print('L1: + every line\'s ink crop = the wave54-final iOS line, translated vertically only (correlation >= 0.97)')
DIV0 = {'001': 100, '003': 80, '004': 80}
for t in DIV0:
    B = A(cap('wave54-final', 'css-text', f'hyphens/hyphenate-character-{t}', 'ios'))
    for name, a in [('fixed Rm', A(Image.open(f'{P55}/hyphen-ios-linebox-replay/{t}-ios-Rm.png')))] + \
                   [(f, fake(f'L1-{f}', 'ios-screenshots', 'css-text', f'hyphens/hyphenate-character-{t}')) for f in ('hyph-removed', 'lines-2-3-swapped', 'text+3x', 'text-2x', 'g2l1+3x')]:
        c, msg = same_lines(a, B, DIV0[t]); row(f'hyphen {t} ios', name, msg, c >= 0.97)

# ── L4 [M]/[F] 005/006/014: every heading band (top, bottom, x0, x1) = the ref's ±2 when the 2em face is present ──
dk = lambda a: a.sum(axis=2) < 300
def bands(a, y0=96, y1=400, gap=2):
    m = dk(a); out, cur, blank = [], None, 0
    for y in range(y0, min(y1, a.shape[0])):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            cur = [y, y, xs.min(), xs.max()] if cur is None else [cur[0], y, min(cur[2], xs.min()), max(cur[3], xs.max())]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    return out
print('L4 [M]/[F] (face present): + the heading\'s bands = the ref\'s bands, each edge ±2, same count')
for t in ('text-decoration-inset-005', 'text-decoration-inset-014'):
    rb = bands(A(ref('css-text-decor', t)))[:2]
    for p in ('ios', 'android'):
        for name, a in [('fixed C', A(Image.open(f'{P55}/ua-heading-margin-replay/{t}-{p}-C.png')))] + \
                       [(f, fake(f'L4-C-{f}', f'{p}-screenshots', 'css-text-decor', t)) for f in ('heading+3y', 'heading-2y', 'heading+12x', 'line2-removed', 'line1-truncated')]:
            b = bands(a)[:2]; ok = len(b) == len(rb) and all(abs(u - v) <= 2 for x, y in zip(b, rb) for u, v in zip(x, y))
            row(f'{t[-3:]} {p}', name, f'{[tuple(int(v) for v in x) for x in b]}'[:60], ok)

# ── L5: [I] the marker's 、 blob present and ending at the ref's x ±2; [T] the digit's ink bbox ±1 and ink px ±25 %;
#        [C] the first marker blob (the ideograph) width = ref ±2; [H] the letter's ink px = ref ±30 % ──
OK5 = f'{P55}/counter-suffix-rows-replay/ok-all'
d5 = lambda a: a.sum(axis=2) < 300
def blobs(a, x0, x1, y0, y1):
    m = d5(a[y0:y1, x0:x1]); cols = np.nonzero(m.any(axis=0))[0] + x0; out = []
    for x in cols:
        if out and x == out[-1][1] + 1:
            out[-1][1] = x
        else:
            out.append([x, x])
    return out
ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}
def i_feat(a):
    res = []
    for y0, y1 in ((95, 121), (121, 152)):
        bl = blobs(a, 200, 330, y0, y1); mk = [b for b in bl if b[0] < 255]
        res.append((len(mk), mk[-1][1] if mk else None))
    return res
def t_feat(a):
    y0, y1 = ROW[9]; m = d5(a[y0:y1, 136:151]); ys, xs = np.nonzero(m)
    return (int(xs.min()) + 136, int(ys.min()) + y0, int(xs.max()) + 136, int(ys.max()) + y0), int(m.sum())
def c_feat(a):
    return [(lambda bl: (bl[0][1] - bl[0][0]) if bl else None)(blobs(a, 20, 60, *ROW[k])) for k in (5, 6)]
def h_feat(a):
    out = []
    for k in (3, 4):
        bl = [b for b in blobs(a, 30, 64, *ROW[k]) if b[1] - b[0] >= 4]
        out.append(int(d5(a[ROW[k][0]:ROW[k][1], bl[0][0]:bl[0][1] + 1]).sum()) if bl else 0)
    return out
print('L5: the proposed extra reads, ref vs fixed (ok-all) vs the fakes that passed')
for p in ('ios', 'android'):
    R5, Rc = A(ref('css-counter-styles', 'cjk-decimal/css3-counter-styles-005')), A(ref('css-counter-styles', 'counter-suffix'))
    F5, Fc = A(Image.open(f'{OK5}/cjk-decimal__css3-counter-styles-005.{p}.png')), A(Image.open(f'{OK5}/counter-suffix.{p}.png'))
    ri = i_feat(R5)
    for name, a in [('fixed', F5), ('I-comma-removed', fake('L5-I-comma-removed', f'{p}-screenshots', 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005'))]:
        f = i_feat(a); ok = all(n >= 2 and r[0] >= 2 and e is not None and abs(e - r[1]) <= 2 for (n, e), r in zip(f, ri))
        row(f'[I] 005 {p}', name, f'marker blobs/right {f} (ref {ri})', ok)
    if p == 'ios':
        (rb, rn) = t_feat(Rc)
        for name, a in [('fixed', Fc), ('T-1-bar7', fake('L5-T-1-bar7', 'ios-screenshots', 'css-counter-styles', 'counter-suffix')),
                        ('T-1-down3', fake('L5-T-1-down3', 'ios-screenshots', 'css-counter-styles', 'counter-suffix'))]:
            b, n = t_feat(a); ok = all(abs(u - v) <= 1 for u, v in zip(b, rb)) and abs(n - rn) <= 0.25 * rn
            row('[T] ios', name, f'digit bbox {b} ink {n} (ref {rb} {rn})', ok)
        rh = h_feat(Rc)
        for name, a in [('fixed', Fc), ('H-letter-block', fake('L5-H-letter-block', 'ios-screenshots', 'css-counter-styles', 'counter-suffix'))]:
            h = h_feat(a); row('[H] ios', name, f'letter ink {h} (ref {rh})', all(abs(u - v) <= 0.3 * v for u, v in zip(h, rh)))
    rcw = c_feat(Rc)
    for name, a in [('fixed', Fc), ('C-ideograph-gutted', fake('L5-C-ideograph-gutted', f'{p}-screenshots', 'css-counter-styles', 'counter-suffix'))]:
        c = c_feat(a); row(f'[C] {p}', name, f'ideograph width {c} (ref {rcw})', all(u is not None and abs(u - v) <= 2 for u, v in zip(c, rcw)))
