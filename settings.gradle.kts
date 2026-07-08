rootProject.name = "style-converter"

// Multi-project spine (R1): the converter CLI is the first subproject.
// Nothing builds at the root — the wrapper + gradle.properties live here,
// each product lives in its own subproject.
include(":converter")


