# combo.py — one fake run per lane in which EVERY gating key of the lane reads a WRONG picture (each built in fam_*.py
# from the lane's own fixed picture), read by the real geometry-gate.py: exit 0 means R6 / revert rule 4 would call these
# wrong pictures picture-correct. Reuses the fake PNGs fam_*.py wrote under fakes/.
import os, subprocess
from lib import *

FK = f'{SCR}/fakes'
def F(fid, plat, sec, test):
    return Image.open(f'{FK}/{fid}/{DIRS[plat]}__{cap_name(sec, test)}').convert('RGB')

def combo_dir_style():
    """dir-style-02a with rows 6, 7 and 9 ALL gutted in one picture (the per-row fakes each gut one row)."""
    ns = {}; exec(open(f'{SCR}/fam_L3.py').read().split('cases = []')[0], ns); dir_gut, fx = ns['dir_gut'], ns['fx']
    a = fx('dir-style-02a')
    for k in (6, 7, 9):
        a = dir_gut(a, k)
    return I(a)

LANES = {
    'L1': ('css-text', {('css-text', 'ios', 'hyphens/hyphenate-character-001'): F('L1-text+3x', 'ios', 'css-text', 'hyphens/hyphenate-character-001'),
                        ('css-text', 'ios', 'hyphens/hyphenate-character-003'): F('L1-hyph-removed', 'ios', 'css-text', 'hyphens/hyphenate-character-003'),
                        ('css-text', 'ios', 'hyphens/hyphenate-character-004'): F('L1-hyph-removed', 'ios', 'css-text', 'hyphens/hyphenate-character-004')}),
    'L2': ('css-flexbox,filter-effects', {**{(s, 'android', t): F('L2-U1-hollow', 'android', s, t) for s, t in
                        [('css-flexbox', 'abspos/abspos-autopos-htb-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vlr-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vrl-ltr'),
                         ('filter-effects', 'backdrop-filter-nested-border-radius-clip'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip-2'),
                         ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4')]},
                        ('filter-effects', 'android', 'backdrop-filter-nested-border-radius-clip-3'): F('L2-U2-gut-cross', 'android', 'filter-effects', 'backdrop-filter-nested-border-radius-clip-3')}),
    'L3': ('selectors,css-text,css-anchor-position,css-writing-modes', {
                        ('css-text', 'android', 'bidi/bidi-tab-001'): F('L3-bidi-gut', 'android', 'css-text', 'bidi/bidi-tab-001'),
                        ('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): F('L3-stack-slab', 'android', 'css-anchor-position', 'anchor-position-multicol-nested-001'),
                        ('css-writing-modes', 'android', 'dynamic-offset-rtl-001'): F('L3-dyn1-hollow', 'android', 'css-writing-modes', 'dynamic-offset-rtl-001'),
                        ('css-writing-modes', 'android', 'dynamic-offset-rtl-002'): F('L3-dyn2-shift+3y', 'android', 'css-writing-modes', 'dynamic-offset-rtl-002')}),
    'L4': ('css-text-decor', {**{('css-text-decor', p, f'text-decoration-inset-{t}'): F(fid, p, 'css-text-decor', f'text-decoration-inset-{t}') for t, p, fid in
                        [('005', 'ios', 'L4-C-heading+3y'), ('006', 'ios', 'L4-C-heading+3y'), ('005', 'android', 'L4-C-line2-removed'), ('006', 'android', 'L4-C-heading+12x'),
                         ('014', 'ios', 'L4-C-line1-truncated'), ('014', 'android', 'L4-C-line1-truncated'), ('011', 'ios', 'L4-C-fixed'), ('011', 'android', 'L4-C-fixed')]}}),
    'L5': ('css-counter-styles', {('css-counter-styles', 'ios', 'counter-suffix'): None, ('css-counter-styles', 'android', 'counter-suffix'): F('L5-C-ideograph-gutted', 'android', 'css-counter-styles', 'counter-suffix'),
                        ('css-counter-styles', 'web', 'counter-suffix'): Image.open(f'{P55}/counter-suffix-rows-replay/ok-all/counter-suffix.web.png').convert('RGB'),
                        ('css-counter-styles', 'ios', 'cjk-decimal/css3-counter-styles-005'): F('L5-I-comma-removed', 'ios', 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005'),
                        ('css-counter-styles', 'android', 'cjk-decimal/css3-counter-styles-005'): F('L5-I-comma-removed', 'android', 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005'),
                        ('css-counter-styles', 'web', 'cjk-decimal/css3-counter-styles-005'): Image.open(f'{P55}/counter-suffix-rows-replay/ok-all/cjk-decimal__css3-counter-styles-005.web.png').convert('RGB')}),
    'L6': ('css-flexbox,css-grid,filter-effects', {**{(s, 'android', t): F('L6-X-border-erased', 'android', s, t) for s, t in
                        [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001'), ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002'),
                         ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001'), ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001'),
                         ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001'), ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001')]},
                        ('filter-effects', 'android', 'backdrop-filter-clip-rect'): F('L6-PO-navbar-sides-erased', 'android', 'filter-effects', 'backdrop-filter-clip-rect')}),
}
LANES['L3'][1][('selectors', 'android', 'dir-style-02a')] = combo_dir_style()
# L5 ios counter-suffix: three fakes stacked in one picture ([C] ideograph gutted, [T] '1' a bar, [H] letters blocks)
ns5 = {}; exec(open(f'{SCR}/fam_L5.py').read().split('cases = {')[0], ns5)
c_ideograph_gutted, t_bar, h_letter_block = ns5['c_ideograph_gutted'], ns5['t_bar'], ns5['h_letter_block']
LANES['L5'][1][('css-counter-styles', 'ios', 'counter-suffix')] = I(h_letter_block(t_bar(c_ideograph_gutted(A(Image.open(f'{P55}/counter-suffix-rows-replay/ok-all/counter-suffix.ios.png'))))))

for lane, (secs, repl) in LANES.items():
    rid = make_run(f'combo-{lane}', repl)
    rc, rows = gate(rid, lane, secs, f'combo-{lane}')
    g = [r for r in rows.values() if r['class'] == 'gating']
    txt = open(f'{SCR}/out/combo-{lane}.txt').read().splitlines()
    summ = [l for l in txt if l.startswith(('geometry-gate', '  by class'))]
    print(f'{lane}: geometry-gate exit {rc}; gating keys {len(g)}: PASS {sum(r["verdict"] == "PASS" for r in g)} FAIL {sum(r["verdict"] == "FAIL" for r in g)}')
    for l in summ:
        print('   ', l.replace(os.path.relpath(f'{SCR}/fakeruns', RUNS), '<scratch>/fakeruns')[:200])
