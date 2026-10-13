import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
ROW = pt.L5_ROW; OK = 'counter-suffix-rows-replay/ok-all'
def letter(a, k):
    L = [b for b in pt.l5_blobs(a, 30, 64, *ROW[k]) if b[1] - b[0] >= 4][0]; y0, y1 = ROW[k]
    return (255 - a[y0:y1, L[0]:L[1] + 1].min(axis=2)).astype(float)
def ident(p, q):
    """best normalised correlation of crop p against crop q over vertical shifts −3..+3 (same width: pad to the wider)."""
    w = max(p.shape[1], q.shape[1]); P = np.zeros((p.shape[0], w)); Q = np.zeros((q.shape[0], w)); P[:, :p.shape[1]] = p; Q[:, :q.shape[1]] = q
    best = 0
    for s in range(-3, 4):
        Qs = np.roll(Q, s, 0); d = np.sqrt((P * P).sum() * (Qs * Qs).sum()); best = max(best, (P * Qs).sum() / d if d else 0)
    return round(float(best), 3)
base = pt.cap('wave54-final', 'css-counter-styles', 'counter-suffix', 'ios')
for l, a in [('ok-ios', pt.png(f'{OK}/counter-suffix.ios.png')), ('H-letters-swapped-3-4', fk('L5-H-letters-swapped-3-4', 'ios', 'counter-suffix')), ('r1:H-letter-block', pt.A(Image.open(f'{SCR}/teeth-rerun/fakeruns/L5-H-letter-block/sections/css-counter-styles/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')))]:
    say(f'  [H] {l:24} letter identity vs wave54-final ios: row3 {ident(letter(a, 3), letter(base, 3))} row4 {ident(letter(a, 4), letter(base, 4))}')
