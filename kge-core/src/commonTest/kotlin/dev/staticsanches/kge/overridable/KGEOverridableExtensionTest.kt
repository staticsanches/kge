package dev.staticsanches.kge.overridable

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal expect val translatorDefault: TranslatorService
internal expect val translatorExpectedDefault: String

/**
 * The extension-contract proof: a test-defined service that follows the
 * facade-contract shape — the service is an interface (`TranslatorService`)
 * and its companion object is the stateless facade delegating per call to the
 * current implementation. The default is per-platform ([translatorDefault]
 * actuals); [TranslatorService.original] exposes the engine default to
 * decorators; [TranslatorService.override] replaces the active implementation
 * (last-declared-wins). The expected default output is asserted against
 * [translatorExpectedDefault] — a literal per target declared independently of
 * the implementation, so a wrong or copied literal fails that target's test.
 */
interface TranslatorService : KGEOverridable {
    fun translate(message: String): String

    companion object :
        KGEOverridable.Proxy<TranslatorService>(TranslatorService::class, translatorDefault),
        TranslatorService {
        override fun translate(message: String): String = delegate.translate(message)
    }
}

class KGEOverridableExtensionTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        test("the facade resolves the platform default") {
            TranslatorService.translate("hi") shouldBe translatorExpectedDefault
        }

        test("override provably changes resolved behavior") {
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "overridden:$message"
                },
            )

            TranslatorService.translate("hi") shouldBe "overridden:hi"
        }

        test("a decorator delegating to original wraps the engine default") {
            val original = TranslatorService.original
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "decorated(${original.translate(message)})"
                },
            )

            TranslatorService.translate("hi") shouldBe "decorated($translatorExpectedDefault)"
        }

        test("a later override supersedes the previous one") {
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "first:$message"
                },
            )
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "second:$message"
                },
            )

            TranslatorService.translate("hi") shouldBe "second:hi"
        }

        test("a service cannot be registered twice") {
            shouldThrow<IllegalArgumentException> {
                object : KGEOverridable.Proxy<TranslatorService>(
                    TranslatorService::class,
                    translatorDefault,
                ) {}
            }
        }

        test("resetAll restores the engine defaults") {
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "kept:$message"
                },
            )

            KGEOverridable.Proxy.resetAll()

            TranslatorService.translate("hi") shouldBe translatorExpectedDefault
        }
    })
