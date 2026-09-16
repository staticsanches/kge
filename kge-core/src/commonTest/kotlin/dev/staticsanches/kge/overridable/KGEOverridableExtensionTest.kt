package dev.staticsanches.kge.overridable

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

expect val translatorDefault: TranslatorService
expect val translatorExpectedDefault: String

/**
 * The extension-contract proof: a service is an interface plus a companion
 * facade delegating per call to the active implementation. The default is
 * per-platform; [TranslatorService.original] exposes it to decorators, and a
 * later override supersedes an earlier one (last-declared-wins). The expected
 * default is a per-target literal declared independently of the implementation.
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

        test("an override is active within its own test") {
            TranslatorService.override(
                object : TranslatorService {
                    override fun translate(message: String): String = "kept:$message"
                },
            )

            TranslatorService.translate("hi") shouldBe "kept:hi"
        }

        test("the module teardown clears an override before the next test") {
            TranslatorService.translate("hi") shouldBe translatorExpectedDefault
        }
    })
