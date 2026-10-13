a = "  for (const node of hits) {"
assert s.count(a) == 1
s = s.replace(a, "  for (const node of hits.slice(0, 1)) {   // MUTATION T1 (X1)")
