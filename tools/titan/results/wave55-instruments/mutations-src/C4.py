a = "        is JsonObject -> true\n"
assert s.count(a) == 1; s = s.replace(a, "        is JsonObject -> false // MUTATION C4\n")
