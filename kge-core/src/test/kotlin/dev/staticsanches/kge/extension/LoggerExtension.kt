package dev.staticsanches.kge.extension

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import java.util.Collections
import kotlin.reflect.KClass

/**
 * JUnit extension to allow verifications of logging events.
 */
class LoggerExtension(
    loggerName: String = Logger.ROOT_LOGGER_NAME,
) : BeforeEachCallback,
    AfterEachCallback {
    constructor(kClass: KClass<*>) : this(kClass.java.name)

    private val listAppender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(loggerName) as Logger

    val loggingEvents: List<ILoggingEvent> = Collections.unmodifiableList(listAppender.list)

    override fun beforeEach(context: ExtensionContext?) {
        logger.addAppender(listAppender)
        listAppender.start()
    }

    override fun afterEach(context: ExtensionContext?) {
        listAppender.stop()
        listAppender.list.clear()
        logger.detachAppender(listAppender)
    }
}
