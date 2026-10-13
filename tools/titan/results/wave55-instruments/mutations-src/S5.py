import re
m = re.search(r"\n *PropertyTracker\.logOnce\(key: impliedContainmentBreadcrumb,\n[^\n]*\n", s)
assert m and len(re.findall(r"logOnce\(key: impliedContainmentBreadcrumb,", s)) == 1
s = s[:m.start()] + "\n            // MUTATION S5: the logOnce call deleted\n" + s[m.end():]
