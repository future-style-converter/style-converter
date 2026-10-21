a = "val content = data.contentOrNull?.uppercase() ?: return emptySet()"
assert s.count(a) == 1; s = s.replace(a, "if (true) return emptySet() // MUTATION C5: the string branch returns the empty set\n                val content = data.contentOrNull?.uppercase() ?: return emptySet()")
