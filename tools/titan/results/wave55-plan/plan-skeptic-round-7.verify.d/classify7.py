# classify7.py — the round-7 MUST-FIX acceptances (adj7.out.txt) grouped by mechanism (plan-skeptic-round-7.verify.md §3)
import sys, collections
REGION = ('hyphenate-character', 'text-decoration-inset', 'counter-suffix', 'css3-counter-styles-005', 'dir-style-02a')
def mf(fake, cell):
    if fake.startswith('S-moved') and 'text-decoration-inset' in cell: return 'MF4 L4 face-present heading moved ±2-3 px in x'
    if fake.startswith('T-window'): return 'MF3 palette paint grown from a recorded window'
    if 'bidi' in cell: return 'MF2 bidi: the class colour outside the RTL bands'
    if any(r in cell for r in REGION): return 'MF1 faint (min >= 200) in-zone ink on a neutral / ramp zone'
    if fake.startswith('T-L6-X') or (fake.startswith('A-class') and 'staticpos' in cell): return 'MF5 L6 [X]: child fill painted over the parent band'
    if fake.startswith('T-L6-PO'): return 'MF6 L6 [PO]: overlap px reverted + red elsewhere'
    if fake.startswith('T-L3-stack'): return 'MF7 L3 stack: an other square relocated'
    return '?? ' + fake
rows = [l.strip().split(' | ') for l in open(sys.argv[1]) if l.startswith('   ') and ' | ' in l]
acc = []; mode = None
for l in open(sys.argv[1]):
    if l.startswith('MUST-FIX acceptances (fake'): mode = 'm'
    elif l.startswith('floor acceptances'): mode = 'f'
    elif l.startswith('   ') and ' | ' in l and mode == 'm' and not l.startswith('   sibling'):
        acc.append(l.strip().split(' | '))
G = collections.defaultdict(lambda: {'g': 0, 't': 0, 'fakes': set(), 'keys': set(), 'tcells': set(), 'px': []})
for a in acc:
    g = G[mf(a[0], a[2])]
    if a[3] == 'gating': g['g'] += 1; g['keys'].add(a[4].split('.py ')[1])
    else: g['t'] += 1; g['tcells'].add(a[2])
    g['fakes'].add((a[0], a[1], a[2])); g['px'].append(int(a[5]))
tot_k = set(); tot_t = set()
for k in sorted(G):
    g = G[k]; tot_k |= g['keys']; tot_t |= g['tcells']
    print(f'{k}: {g["g"]} gating-key reads + {g["t"]} tally entries; {len(g["fakes"])} distinct fakes; {len(g["keys"])} gating keys; {len(g["tcells"])} tally cells; px {min(g["px"])} … {max(g["px"])}')
    print('    keys: ' + '; '.join(sorted(g['keys'])))
print(f'ALL: {len(acc)} acceptances; {len(tot_k)} gating keys; {len(tot_t)} tally cells')
