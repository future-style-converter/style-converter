a = "[...new Set([...dirs(pre), ...dirs(post)])].sort()"
assert s.count(a) == 1; s = s.replace(a, "dirs(pre).sort() /* MUTATION M2: sections read from PRE only */")
