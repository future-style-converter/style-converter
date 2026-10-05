# Independent oracle: dev (5d9ed628) doubleGeom far-edge = extent - inset;
# tree (b35e203a) = max(inset, extent - inset); fix = max(extent, band) - inset.
import math
def ink(c, w): return set(range(math.floor(c - w/2), math.ceil(c + w/2)))
def lines(kind, w):
    if kind == 'double': line = w/3; return [(line/2, line), (w - line/2, line)]
    half = w/2; return [(half/2, half), (half/2 + half, half)]
F = {'dev': lambda e,b,i: e - i, 'tree': lambda e,b,i: max(i, e - i), 'fix': lambda e,b,i: max(e, b) - i}
cases = [('p1 double 3 ext3', 'double', 3, 3), ('p2 double 6 ext6', 'double', 6, 6), ('p3 double 6 ext9', 'double', 6, 9),
         ('p4 double 3 ext3 (END)', 'double', 3, 3), ('p5 groove 4 ext4', 'groove', 4, 4), ('p6 groove 4 ext0', 'groove', 4, 0),
         ('p7 double 3 ext2', 'double', 3, 2), ('c1 double 3 ext50', 'double', 3, 50), ('c2 double 3 ext0', 'double', 3, 0)]
for name, kind, w, e in cases:
    out = []
    for k, f in F.items():
        per = [sorted(ink(f(e, w, i), sw)) for i, sw in lines(kind, w)]
        out.append(f"{k}: outer {per[0]} inner {per[1]}")
    print(name, ' | '.join(out))
