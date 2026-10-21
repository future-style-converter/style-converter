# fixcheck.py — the PROPOSED extra measurement of each must-fix, executed on the fixed picture (must stay OK) and on
# every fake that passed the current key (must turn WRONG), plus the ref (self-check) — so each fix in the report is shown
# to separate before it is written into a probe. Read-only: reads refs, runs, the replay folders and the fakes/ dir.
import glob, os
from lib import *

FK = f'{SCR}/fakes'
def fake(fid, sub, sec, test):
    p = f'{FK}/{fid}/{sub}__{cap_name(sec, test)}'
    return A(Image.open(p)) if os.path.exists(p) else None
def row(tag, name, val, ok):
    print(f'  {tag:34} {name:30} {val:52} → {"OK" if ok else "WRONG"}')

# ── F1 compose-wpt-content-box-cb (7 gating keys): strict-green MASS within max(40, 0.5 %) of the ref ──
green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
print('F1 compose-wpt-content-box-cb: + |greenPx - ref| <= max(40, 0.005 * ref)')
for sec, t in [('css-flexbox', 'abspos/abspos-autopos-htb-ltr'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip'),
               ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip-3')]:
    r = int(green(A(ref(sec, t))).sum()); tol = max(40, 0.005 * r)
    pics = [('web wave54-final', A(cap('wave54-final', sec, t, 'web'))), ('ios wave54-final', A(cap('wave54-final', sec, t, 'ios')))]
    if t.endswith('-3'):
        pics.append(('fixed (cbb replay)', A(Image.open(f'{P55}/cbb-android-replay/android-clip-3.png'))))
        pics += [(f, fake(f'L2-U2-{f}', 'android-screenshots', sec, t)) for f in ('gut-cross', 'square-3-corners')]
    else:
        pics.append(('fixed (wave54-probe android)', A(cap('wave54-probe', sec, t, 'android'))))
        pics += [(f, fake(f'L2-U1-{f}', 'android-screenshots', sec, t)) for f in ('hollow', 'corners2', 'stripe30')]
    for name, a in pics:
        n = int(green(a).sum()); row(t.split('/')[-1], name, f'greenPx {n} (ref {r}, tol {tol:.0f})', abs(n - r) <= tol)

# ── F2 cbb-android clip-3: the corner count on ALL FOUR 20x20 corners of #inner (x36-55 / x216-235, y36-55 / y116-135) ──
print('F2 cbb-android clip-3: + all four 20x20 corner squares green count within +-8 of the ref')
def corners(a):
    return [int(green(a[y:y + 20, x:x + 20]).sum()) for x, y in ((36, 36), (216, 36), (36, 116), (216, 116))]
rc = corners(A(ref('filter-effects', 'backdrop-filter-nested-border-radius-clip-3')))
for name, a in [('fixed (cbb replay)', A(Image.open(f'{P55}/cbb-android-replay/android-clip-3.png'))),
                ('square-3-corners', fake('L2-U2-square-3-corners', 'android-screenshots', 'filter-effects', 'backdrop-filter-nested-border-radius-clip-3')),
                ('gut-cross', fake('L2-U2-gut-cross', 'android-screenshots', 'filter-effects', 'backdrop-filter-nested-border-radius-clip-3'))]:
    c = corners(a); row('clip-3', name, f'corners {c} (ref {rc})', all(abs(u - v) <= 8 for u, v in zip(c, rc)))

# ── F3 L3: per-row INK MASS + COLUMN PROFILE against the ref (dir-style rows), the cell set (stack), the full box (dyn) ──
ink = lambda a: a.min(axis=2) < 200
print('F3a dir-style-02a rows 6/7/9: + ink px within +-20 % of the ref row, and the row\'s ink-column set overlaps the ref\'s (Jaccard >= 0.75, x tolerance 1 px)')
def cols(a, k):
    y0 = 16 + 20 * (k - 1); m = ink(a[y0:y0 + 20]); c = m.any(axis=0)
    c1 = c | np.roll(c, 1) | np.roll(c, -1)          # 1-px tolerance
    return int(m.sum()), c, c1
R = A(ref('selectors', 'dir-style-02a'))
FX = A(Image.open(f'{P55}/positioned-parent-slot-replay/dir-style-02a-android-U1U2.png'))
for k in (6, 7, 9):
    rn, rcset, rc1 = cols(R, k)
    for name, a in [('web wave54-final', A(cap('wave54-final', 'selectors', 'dir-style-02a', 'web'))), ('fixed U1U2', FX)] + \
                   [(f, fake(f'L3-dir-row{k}-{f}', 'android-screenshots', 'selectors', 'dir-style-02a')) for f in ('gut', 'mirror-words', 'text+2x', 'text+3x', 'text-3x')]:
        n, c, c1 = cols(a, k); jac = float((c & rc1).sum() + (rcset & c1).sum()) / max(1, c.sum() + rcset.sum())
        row(f'row{k}', name, f'ink {n} (ref {rn}) col-overlap {jac:.2f}', abs(n - rn) <= 0.2 * rn and jac >= 0.75)

blue = lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
print('F3b bidi-tab-001 rtl-rows: + blue px per band within +-20 % of the ref band\'s, left edge within +-1')
R = A(ref('css-text', 'bidi/bidi-tab-001'))
for name, a in [('fixed U1U2', A(Image.open(f'{P55}/positioned-parent-slot-replay/bidi-tab-001-android-U1U2.png')))] + \
               [(f, fake(f'L3-bidi-{f}', 'android-screenshots', 'css-text', 'bidi/bidi-tab-001')) for f in ('gut', 'rtl+2x')]:
    ok, vals = True, []
    for y0 in (86, 106, 186, 226):
        rb, b = blue(R[y0:y0 + 20]), blue(a[y0:y0 + 20])
        rx, bx = np.nonzero(rb.any(axis=0))[0], np.nonzero(b.any(axis=0))[0]
        vals.append(f'{int(b.sum())}/{int(rb.sum())}@x{bx.min() if len(bx) else None}/{rx.min()}')
        ok &= abs(int(b.sum()) - int(rb.sum())) <= 0.2 * rb.sum() and len(bx) > 0 and abs(bx.min() - rx.min()) <= 1
    row('bidi-tab', name, ' '.join(vals)[:52], ok)

g2 = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)
print('F3c anchor-position-multicol-nested-001 stack: + green px in x150-420 y60-220 = 400 +- 8 (nothing but the four 10x10 squares)')
for name, a in [('ref', A(ref('css-anchor-position', 'anchor-position-multicol-nested-001'))),
                ('web wave54-final', A(cap('wave54-final', 'css-anchor-position', 'anchor-position-multicol-nested-001', 'web'))),
                ('fixed U1', A(Image.open(f'{P55}/positioned-parent-slot-replay/anchor-position-multicol-nested-001-android-U1.png')))] + \
               [(f, fake(f'L3-stack-{f}', 'android-screenshots', 'css-anchor-position', 'anchor-position-multicol-nested-001')) for f in ('grown30', 'slab', 'dup-stacked')]:
    n = int(g2(a[60:220, 150:420]).sum()); row('stack', name, f'green {n}', abs(n - 400) <= 8)

mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
print('F3d/e dynamic-offset-rtl-001 / -002: + magenta px = 100 +- 5 and (002) the FULL bbox = web\'s +-1')
for t, fl in (('dynamic-offset-rtl-001', ('hollow',)), ('dynamic-offset-rtl-002', ('shift+3y', 'shift-3y', 'wider+5', 'hollow'))):
    W = A(cap('wave54-final', 'css-writing-modes', t, 'web')); wy, wx = np.nonzero(mag(W)); wb = (wx.min(), wy.min(), wx.max(), wy.max())
    for name, a in [('web wave54-final', W), ('fixed U2', A(Image.open(f'{P55}/positioned-parent-slot-replay/{t}-android-U2.png')))] + \
                   [(f, fake(f'L3-dyn{t[-1]}-{f}', 'android-screenshots', 'css-writing-modes', t)) for f in fl]:
        ys, xs = np.nonzero(mag(a)); bb = (xs.min(), ys.min(), xs.max(), ys.max()); n = len(xs)
        ok = abs(n - 100) <= 5 and (t.endswith('001') or all(abs(u - v) <= 1 for u, v in zip(bb, wb)))
        row(t[-3:], name, f'magenta n {n} bbox {tuple(int(v) for v in bb)} (web {tuple(int(v) for v in wb)})', ok)

# ── F4 L6 [X]: + the parent border present (dark px >= 0.9 x the ref's) and the child fill bbox = the ref's +-2 ──
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
dark = lambda a: a.sum(axis=2) < 150
print('F4 [X]: + dark (border) px >= 0.9 x ref, child-fill bbox within +-2 of the ref\'s, child fill >= 0.95 x ref')
for sec, t, fill in [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', yellow), ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', teal),
                     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', teal)]:
    R = A(ref(sec, t)); rd = int(dark(R).sum()); ry, rx = np.nonzero(fill(R)); rbb = (rx.min(), ry.min(), rx.max(), ry.max()); rf = len(rx)
    for name, a in [('fixed U2 replay', A(Image.open(f'{P55}/android-paint-order-replay/{t.replace("/", "__")}-android-U2.png')))] + \
                   [(f, fake(f'L6-X-{f}', 'android-screenshots', sec, t)) for f in ('border-erased', 'child+3x', 'child-3y', 'child-shrunk8%', 'child-scrambled')]:
        d = int(dark(a).sum()); y, x = np.nonzero(fill(a)); bb = (x.min(), y.min(), x.max(), y.max())
        ok = d >= 0.9 * rd and all(abs(u - v) <= 2 for u, v in zip(bb, rbb)) and len(x) >= 0.95 * rf
        row(t.split('-staticpos-')[-1], name, f'dark {d}/{rd} fill {len(x)}/{rf} bbox Δ{max(abs(u - v) for u, v in zip(bb, rbb))}', ok)
bl = lambda a: (a[..., 2] >= 200) & (a[..., 0] <= 60) & (a[..., 1] <= 60)
red = lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
print('F4b [PO]: + navbar band blue + overlap red >= 0.95 x the ref\'s (blue + red over the band rows)')
R = A(ref('filter-effects', 'backdrop-filter-clip-rect')); rv = int(bl(R).sum())
for name, a in [('fixed U1 replay', A(Image.open(f'{P55}/android-paint-order-replay/backdrop-filter-clip-rect-android-U1.png'))),
                ('navbar-sides-erased', fake('L6-PO-navbar-sides-erased', 'android-screenshots', 'filter-effects', 'backdrop-filter-clip-rect'))]:
    v = int(bl(a).sum()) + int(red(a[[166, 167, 218, 219], 163:267]).sum()) - int(red(R[[166, 167, 218, 219], 163:267]).sum())
    row('clip-rect', name, f'navbar blue(+overlap red Δ) {v} (ref blue {rv})', v >= 0.95 * rv)
