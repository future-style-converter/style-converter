a = "            cfg = read(prop.data)\n"
assert s.count(a) == 1; s = s.replace(a, "            if cfg == nil { cfg = read(prop.data) } // MUTATION S1: first wins\n")
