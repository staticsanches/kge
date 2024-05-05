@file:Suppress("unused")

object LWJGLUtils {

	val lwjglNatives by lazy {
		val name = System.getProperty("os.name")!!
		val arch = System.getProperty("os.arch")!!
		return@lazy when {
			arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
				if (arrayOf("arm", "aarch64").any { arch.startsWith(it) })
					"natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
				else if (arch.startsWith("ppc"))
					"natives-linux-ppc64le"
				else if (arch.startsWith("riscv"))
					"natives-linux-riscv64"
				else
					"natives-linux"

			arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
				"natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"

			arrayOf("Windows").any { name.startsWith(it) } ->
				if (arch.contains("64"))
					"natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
				else
					"natives-windows-x86"

			else ->
				throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
		}
	}


}
