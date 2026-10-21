import re
m = re.search(r"\n *PropertyTracker\.logOnce\(key: containDecodeBreadcrumb,\n[^\n]*\n", s)
assert m and len(re.findall(r"logOnce\(key: containDecodeBreadcrumb,", s)) == 1
s = s[:m.start()] + "\n            // MUTATION S4: the logOnce call deleted\n" + s[m.end():]
