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
        when {
            arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
                if (arrayOf("arm", "aarch64").any { arch.startsWith(it) }) {
                    if (arch.contains("64") || arch.startsWith("armv8")) {
                        projects.kgeLinuxArm64
                    } else {
                        projects.kgeLinuxArm32
                    }
                } else {
                    projects.kgeLinux
                }

            arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
                if (arch.startsWith("aarch64")) {
                    projects.kgeMacosArm64
                } else {
                    projects.kgeMacos
                }

            arrayOf("Windows").any { name.startsWith(it) } ->
                if (arch.contains("64")) {
                    if (arch.startsWith("aarch64")) {
                        projects.kgeWindowsArm64
                    } else {
                        projects.kgeWindows
                    }
                } else {
                    projects.kgeWindowsX86
                }

            else -> throw Error("Unrecognized or unsupported platform. Please set \"kgeNatives\" manually")
        }

    implementation(kgeNatives)
}
