/** The LWJGL native artifact classifier for the current OS/arch. */
fun lwjglNativesClassifier(): String {
    val osName = System.getProperty("os.name")!!
    val osArch = System.getProperty("os.arch")!!
    return when {
        "FreeBSD" == osName -> {
            "natives-freebsd"
        }

        arrayOf("Linux", "SunOS", "Unit").any { osName.startsWith(it) } -> {
            if (arrayOf("arm", "aarch64").any { osArch.startsWith(it) }) {
                "natives-linux${
                    if (osArch.contains("64") || osArch.startsWith("armv8")) {
                        "-arm64"
                    } else {
                        "-arm32"
                    }
                }"
            } else if (osArch.startsWith("ppc")) {
                "natives-linux-ppc64le"
            } else if (osArch.startsWith("riscv")) {
                "natives-linux-riscv64"
            } else {
                "natives-linux"
            }
        }

        arrayOf("Mac OS X", "Darwin").any { osName.startsWith(it) } -> {
            "natives-macos${if (osArch.startsWith("aarch64")) "-arm64" else ""}"
        }

        arrayOf("Windows").any { osName.startsWith(it) } -> {
            if (osArch.contains("64")) {
                "natives-windows${if (osArch.startsWith("aarch64")) "-arm64" else ""}"
            } else {
                "natives-windows-x86"
            }
        }

        else -> {
            error("unsupported OS/arch for LWJGL natives: $osName/$osArch")
        }
    }
}
