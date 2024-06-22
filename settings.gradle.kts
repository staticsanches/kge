rootProject.name = "kge"

include("kge-core")

include(
    "kge-linux",
    "kge-linux-arm32",
    "kge-linux-arm64",
)
include(
    "kge-macos",
    "kge-macos-arm64",
)
include(
    "kge-windows",
    "kge-windows-arm64",
    "kge-windows-x86",
)
