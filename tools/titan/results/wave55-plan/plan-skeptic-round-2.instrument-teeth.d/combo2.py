# One wrong picture per gating key, every gating key of a lane at once (round-2 accepted fakes), through the real gate.
import os, sys, json, importlib.util
sys.dont_write_bytecode = True
sys.argv = [sys.argv[0], 'NONE']          # import r2fakes without running its main
spec = importlib.util.spec_from_file_location('r2', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r2fakes.py')); r2 = importlib.util.module_from_spec(spec); spec.loader.exec_module(r2)
pt, np, shift = r2.pt, r2.np, r2.shift
EXP, PY, SCR = r2.EXP, r2.PY, r2.SCR
combos = {}
# L2: the six U1 boxes with the fail-ink sliver; clip-3 with the off-scanline pink sliver
combos['L2'] = ('css-flexbox,filter-effects', {**{(sec, 'android', t): r2.l2_redsliver(pt.cap('wave54-probe', sec, t, 'android'), sec) for sec, t in r2.U1},
                                              ('filter-effects', 'android', 'backdrop-filter-nested-border-radius-clip-3'): r2.c3_pink(r2.B3)})
# L3: dir rows 6 (+2y), 7 (vflip), 9 (-2y) in ONE picture; bidi run order mirrored; stack with red; both dynamic-offset parents erased
d = r2.D
d = shift(d, 0, 2, (21, 116, d.shape[1], 134)); d = r2.l3_vflip(d, 7); d = shift(d, 0, -2, (21, 178, d.shape[1], 196))
combos['L3'] = ('selectors,css-text,css-anchor-position,css-writing-modes', {('selectors', 'android', 'dir-style-02a'): d, ('css-text', 'android', 'bidi/bidi-tab-001'): r2.l3_bidi_order(r2.B),
               ('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): r2.l3_red(r2.ST),
               ('css-writing-modes', 'android', 'dynamic-offset-rtl-001'): r2.l3_parent(pt.l3_fixed('dynamic-offset-rtl-001'), 'erase'),
               ('css-writing-modes', 'android', 'dynamic-offset-rtl-002'): r2.l3_parent(pt.l3_fixed('dynamic-offset-rtl-002'), 'erase')})
# L4: the C arm with line 1 clipped 10 px on 005/006/014 natives; 011 on its fixed C picture
repl = {('css-text-decor', p, t): r2.l4_clip(pt.png(f'ua-heading-margin-replay/{t}-{p}-C.png'), t) for t in r2.L4T for p in ('ios', 'android')}
repl.update({('css-text-decor', p, 'text-decoration-inset-011'): pt.png(f'ua-heading-margin-replay/text-decoration-inset-011-{p}-C.png') for p in ('ios', 'android')})
combos['L4'] = ('css-text-decor', repl)
# L5: letters swapped + ideographs swapped + row 9 text erased in ONE counter-suffix picture per native; 005 marker +3y
repl = {}
for p in ('web', 'ios', 'android'):
    cs, c5 = pt.png(f'{r2.OK}/counter-suffix.{p}.png'), pt.png(f'{r2.OK}/cjk-decimal__css3-counter-styles-005.{p}.png')
    if p != 'web': cs = r2.l5_row9_text(r2.l5_ideo_swap(r2.l5_letter_swap(cs)), 'erase'); c5 = r2.l5_i_y(c5, 3)
    repl[('css-counter-styles', p, 'counter-suffix')] = cs; repl[('css-counter-styles', p, 'cjk-decimal/css3-counter-styles-005')] = c5
combos['L5'] = ('css-counter-styles', repl)
for lane, (secs, repl) in combos.items():
    rid = pt.make_run(SCR, f'combo-{lane}', repl)
    for (sec, plat, test), arr in repl.items():
        os.makedirs(f'{SCR}/pngs/combo-{lane}', exist_ok=True); r2.I(arr).save(f'{SCR}/pngs/combo-{lane}/{plat}__{test.replace("/", "__")}.png')
    rc, rows = pt.gate(rid, lane, secs, EXP, PY, os.path.join(SCR, f'gate-combo-{lane}.json'))
    g = [r for r in rows.values() if r['class'] == 'gating']
    print(f'{lane}: geometry-gate exit {rc}; gating keys {len(g)}: PASS {sum(r["verdict"] == "PASS" for r in g)} FAIL {sum(r["verdict"] == "FAIL" for r in g)} UNMEASURED {sum(r["verdict"] == "UNMEASURED" for r in g)}'
          + ''.join(f'\n    {r["verdict"]:5} {r["key"]}' for r in g))
