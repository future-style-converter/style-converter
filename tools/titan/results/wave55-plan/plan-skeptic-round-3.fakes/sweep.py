#!/usr/bin/env python3
# tools/titan/results/wave55-plan/plan-skeptic-round-3.fakes/sweep.py — the round-3 ADDED-INK sweep (verify M2): every variant
# of added_ink.py on every fixed picture of the standing set (probes-teeth.py + probes-teeth-r2.py `*fixed` cases: every gating
# key and every pictureCorrectTally key that has one), read through the REAL geometry-gate.py on the GENERATED expectations.json
# under the pinned interpreter, exactly as probes-teeth.py reads its cases.
#
# Cases with the same pictures are one GROUP (the three dir-style-02a row cases share the U1U2 replay). Each group's fixed
# pictures are read first: the BASELINE verdict of every row of its lane's probes on those sections. Each FAKED capture of a
# variant is then
#   caught — a gating / control / tally row that READS that capture (its key names the test and the platform) moves PASS →
#            FAIL (a stray outside one key's band is caught by the row whose band it is, e.g. the [C] unaffected-rows control);
#   HOLE   — no such row moves: the added ink is invisible to every row of that picture (exit 1).
# A gating / tally key that still PASSes a capture a sibling row rejects is listed as `still PASS`. Native captures only are faked (every key under test reads ios / android).
# Writes fake runs + gate JSONs ONLY under --scratch (outside the repository); prints to stdout (and --out FILE).
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/plan-skeptic-round-3.fakes/sweep.py --scratch <dir>
#        [--exp <expectations.json>] [--lanes L1,L3] [--out <file>]
import hashlib, importlib.util, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__)); P = os.path.dirname(HERE)
def load(name, path):
    s = importlib.util.spec_from_file_location(name, path); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt = load('probes_teeth', os.path.join(P, 'probes-teeth.py'))
r2 = load('probes_teeth_r2', os.path.join(P, 'probes-teeth-r2.py'))
ai = load('added_ink', os.path.join(HERE, 'added_ink.py'))


def groups(lanes=None):
    """[(lane, sections, group id, {capture: fixed picture}, [keys that PASS on it])] — identical picture sets merged."""
    out = {}
    for L, b in (('L1', pt.l1_cases), ('L2', pt.l2_cases), ('L3', pt.l3_cases), ('L4', pt.l4_cases), ('L5', pt.l5_cases), ('L6', pt.l6_cases)):
        if lanes and L not in lanes:
            continue
        for lane, secs, fid, repl, want in b() + r2.cases(pt, L):
            if not fid.endswith('fixed'):
                continue
            sig = (lane, secs, tuple(sorted((k, hashlib.sha1(v.tobytes()).hexdigest()) for k, v in repl.items())))
            g = out.setdefault(sig, [lane, secs, [], repl, set()])
            g[2].append(fid); g[4] |= {k for k, w in want.items() if w == 'PASS'}
    return [(lane, secs, '+'.join(fids), repl, sorted(keys)) for lane, secs, fids, repl, keys in out.values()]


def variants(gid, repl):
    """[(variant id, {capture: picture})] for one group: the generic added-ink class plus the L3 in-target ones."""
    out = []
    def each(f):
        r = {}
        for (sec, plat, test), pic in repl.items():
            o = None if plat == 'web' else f(pic, sec, plat, test)
            r[(sec, plat, test)] = pic if o is None else o
        return r
    for v in ai.VARIANTS:
        out.append((v, each(lambda pic, sec, plat, test: ai.apply(v, pic, pt.cap('wave54-final', sec, test, plat)))))
    if gid.startswith('dir-row'):
        for k in (6, 7, 9):
            for v in ai.L3_DIR_VARIANTS:
                out.append((f'{v}-r{k}', each(lambda pic, sec, plat, test: ai.l3_dir(pic, k, v))))
    if gid == 'bidi-fixed':
        out += [(v, each(lambda pic, sec, plat, test: ai.l3_bidi(pic, v))) for v in ai.L3_BIDI_VARIANTS]
    if gid == 'stack-fixed':
        out += [(v, each(lambda pic, sec, plat, test: ai.l3_stack(pic, v))) for v in ai.L3_STACK_VARIANTS]
    return out


# NAMED residuals (pre-registered, each with its reason; a later round may close one, never add one silently): a faked capture
# listed here is reported RESIDUAL, not HOLE. Keyed (lane, group, variant, '<test basename> <platform>').
# Round 4 (fix-r4.done.md): the two round-3 residuals — ('L4', 'C-fixed', 'stray2-mid', 'text-decoration-inset-014 ios' /
# 'android'), a 2×2 blob touching a face-present 014 glyph — are CLOSED by ua-heading-margin.geometry.py's `face xor:` (the
# band's ink = the face reference's, XOR ≤ 2: the blob reads 4+) and pinned FAIL in probes-teeth-r4.py; none is left.
RESIDUALS = {}


def reads(key, sec, plat, test):
    """Does probe row `key` read the capture (sec, plat, test)? Its platform token and the test's name (or, for the L1 linebox
    probe, its 3-digit number) must both be in the key."""
    t = key.split(); base = test.split('/')[-1]
    if plat not in t:
        return False
    if t[0] == 'hyphen-ios-linebox':
        return base.endswith('-' + t[1])
    return any(x == test or x == base or x.endswith('/' + base) for x in t)


residual = []          # the named residuals met by the last run()


def run(scratch, exp, lanes=None, say=print):
    """Every group × variant through the real gate, classified PER CAPTURE: a faked capture is CAUGHT when a gating / control /
    tally row that reads it moves PASS → FAIL; else it is a HOLE. Returns (holes, in-band acceptances, reads)."""
    e = json.load(open(exp)); py = e['interpreter'].split()
    script = lambda pr: os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
    gating = {(script(pr), k) for l in e['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
    tally = {tuple(k.split(' ', 1)) for part in ('full', 'part', 'paintOrderOnly') for x in e['pictureCorrectTally'].get(part, []) for k in x['keys']}
    holes, accepted, reads_n = [], [], 0
    residual.clear()
    for lane, secs, gid, repl, keys in groups(lanes):
        keys = [k for k in keys if k in gating or k in tally]
        rid = pt.make_run(scratch, f'r3sweep-{lane}-{gid}-FIXED', repl)
        _, base_rows = pt.gate(rid, lane, secs, exp, py, os.path.join(scratch, f'gate-r3sweep-{lane}-{gid}-FIXED.json'))
        ok_rows = {k for k, r in base_rows.items() if r['verdict'] == 'PASS' and (r['class'] in ('gating', 'control') or k in tally)}
        say(f'{lane} {gid}: fixed pictures read first — {len(ok_rows)} gating / control / tally rows PASS; keys under test: ' + ', '.join(k[1] for k in keys))
        for v, r in variants(gid, repl):
            changed = [x for x in repl if x[1] != 'web' and not ((r[x] is repl[x]) or (r[x] == repl[x]).all())]
            if not changed:
                say(f'  {v:24} (no-op on every capture: not run)'); continue
            rid = pt.make_run(scratch, f'r3sweep-{lane}-{gid}-{v}', r)
            rc, rows = pt.gate(rid, lane, secs, exp, py, os.path.join(scratch, f'gate-r3sweep-{lane}-{gid}-{v}.json'))
            out = []
            for (sec, plat, test) in changed:
                mine = [k for k in ok_rows if reads(k[1], sec, plat, test)]
                caught = sorted(k[1] for k in mine if rows.get(k, {}).get('verdict') != 'PASS')
                acc = sorted(k[1] for k in mine if k in keys and rows.get(k, {}).get('verdict') == 'PASS')
                reads_n += len([k for k in mine if k in keys])
                accepted += [(lane, gid, v, a) for a in acc]
                name = f'{test.split("/")[-1]} {plat}'
                if not mine:
                    out.append(f'{name}: no row reads it'); continue
                if not caught and (lane, gid, v, name) in RESIDUALS:
                    residual.append((lane, gid, v, name)); out.append(f'{name}: RESIDUAL (named: {RESIDUALS[(lane, gid, v, name)][:90]}…)')
                elif not caught:
                    holes.append((lane, gid, v, name)); out.append(f'{name}: HOLE (accepted by {", ".join(k[1] for k in mine)})')
                else:
                    out.append(f'{name}: caught by {"; ".join(caught[:3])}{" …" if len(caught) > 3 else ""}' + (f' (still PASS: {", ".join(acc)})' if acc else ''))
            say(f'  {v:24} ' + ' | '.join(out))
    return holes, accepted, reads_n


if __name__ == '__main__':
    a = sys.argv[1:]
    SC = a[a.index('--scratch') + 1]; assert os.path.isabs(SC), '--scratch must be absolute'
    EXP = a[a.index('--exp') + 1] if '--exp' in a else os.path.join(P, 'expectations.json')
    LANES = a[a.index('--lanes') + 1].split(',') if '--lanes' in a else None
    OUT = a[a.index('--out') + 1] if '--out' in a else None
    lines = []
    def say(s):
        lines.append(s); print(s, flush=True)
    say(f'# round-3 added-ink sweep on expectations.json sha256 {hashlib.sha256(open(EXP, "rb").read()).hexdigest()[:16]}…, added_ink.py '
        f'{hashlib.sha256(open(os.path.join(HERE, "added_ink.py"), "rb").read()).hexdigest()[:16]}…')
    holes, acc, n = run(SC, EXP, LANES, say)
    say(f'\n{n} key reads on faked captures; HOLES (a faked capture no gating / control / tally row of it rejects): {len(holes)}; '
        f'key reads that PASS a fake a sibling row of the same capture rejects: {len(acc)}')
    for h in holes:
        say(f'  HOLE {h[0]} {h[1]} {h[2]} — {h[3]}')
    for r in residual:
        say(f'  RESIDUAL (named, pre-registered) {r[0]} {r[1]} {r[2]} — {r[3]}: {RESIDUALS[r]}')
    if OUT:
        open(OUT, 'w').write('\n'.join(lines) + '\n')
    sys.exit(1 if holes else 0)
