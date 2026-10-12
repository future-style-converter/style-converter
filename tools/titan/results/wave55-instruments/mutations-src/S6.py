import re
m = re.search(r"\n *PropertyTracker\.logOnce\(key: positionedEstablisherBreadcrumb,\n[^\n]*\n", s)
assert m and len(re.findall(r"logOnce\(key: positionedEstablisherBreadcrumb,", s)) == 1
s = s[:m.start()] + "\n            // MUTATION S6: the logOnce call deleted\n" + s[m.end():]
