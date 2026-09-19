package dev.staticsanches.kge.font.roboto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JvmLicenseResourcesTest :
    FunSpec({
        test("each family's license ships verbatim in jvmMain resources") {
            resource("/META-INF/licenses/roboto/OFL.txt") shouldBe Roboto.licenseText
            resource("/META-INF/licenses/roboto-mono/OFL.txt") shouldBe RobotoMono.licenseText
        }
    })

private fun resource(path: String): String =
    checkNotNull(JvmLicenseResourcesTest::class.java.getResourceAsStream(path))
        .use { it.readBytes().decodeToString() }
