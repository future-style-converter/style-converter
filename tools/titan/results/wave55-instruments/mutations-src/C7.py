a = "if (properties.any { it.type in IMPLIED_TYPES }) PropertyTracker.markUnhandled(IMPLIED_CONTAINMENT_BREADCRUMB)"
assert s.count(a) == 1; s = s.replace(a, "// MUTATION C7: the implied-containment breadcrumb deleted")
