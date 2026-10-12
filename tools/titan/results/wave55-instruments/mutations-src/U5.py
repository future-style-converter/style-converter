a = "for (const [k, init] of Object.entries(MARKER_PAINT_INITIAL)) {"
assert s.count(a) == 1
s = s.replace(a, "for (const [k, init] of Object.entries(MARKER_PAINT_INITIAL).filter(([k]) => k !== 'maskImage' && k !== 'mixBlendMode')) {   // MUTATION U5")
