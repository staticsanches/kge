package dev.staticsanches.kge

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult

/**
 * The overridable-service registry is process-wide; an override left behind by
 * one test must never leak into the next. Reset runs after every test on every
 * target, so specs do not repeat the teardown.
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
