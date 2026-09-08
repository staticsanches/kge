package dev.staticsanches.kge

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult

/**
 * Module-wide kotest project config: the overridable-service registry is
 * process-wide, so an override left behind by one test must never leak into
 * the next. [KGEOverridable.Proxy.resetAll] runs after every test on every
 * target; specs do not repeat the teardown themselves.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(ServiceResetExtension)
}

private object ServiceResetExtension : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult =
        try {
            execute(testCase)
        } finally {
            KGEOverridable.Proxy.resetAll()
        }
}
