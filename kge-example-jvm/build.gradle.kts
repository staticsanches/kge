plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.logback.classic)

    val name = System.getProperty("os.name")!!
    val arch = System.getProperty("os.arch")!!
    val kgeNatives =
        with(projects.kgeNatives) {
            when {
                arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
                    if (arrayOf("arm", "aarch64").any { arch.startsWith(it) }) {
                        if (arch.contains("64") || arch.startsWith("armv8")) {
                            kgeLinuxArm64
                        } else {
                            kgeLinuxArm32
                        }
                    } else {
                        kgeLinux
                    }

                arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
                    if (arch.startsWith("aarch64")) {
                        kgeMacosArm64
                    } else {
                        kgeMacos
                    }

                arrayOf("Windows").any { name.startsWith(it) } ->
                    if (arch.contains("64")) {
                        if (arch.startsWith("aarch64")) {
                            kgeWindowsArm64
                        } else {
                            kgeWindows
                        }
                    } else {
                        kgeWindowsX86
                    }

                else -> throw Error("Unrecognized or unsupported platform. Please set \"kgeNatives\" manually")
            }
        }

    implementation(kgeNatives)
}
