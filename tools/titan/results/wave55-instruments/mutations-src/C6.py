a = "        PropertyTracker.markUnhandled(CONTAIN_DECODE_BREADCRUMB)\n"
assert s.count(a) == 1; s = s.replace(a, "        // MUTATION C6: the decode breadcrumb deleted\n")
