package com.simarel.vkbot.greetings.test

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class SystemLoggerCapture(loggerName: String) : AutoCloseable {
    private val logger = Logger.getLogger(loggerName)
    private val previousLevel: Level? = logger.level
    private val records = CopyOnWriteArrayList<LogRecord>()
    private val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            if (record.message?.startsWith(BOOTSTRAP_MESSAGE) == true) {
                return
            }
            records += record
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    init {
        logger.level = Level.ALL
        logger.addHandler(handler)
    }

    fun messages(level: Level): List<String> = records
        .filter { it.level.intValue() >= level.intValue() }
        .mapNotNull { it.message }

    override fun close() {
        logger.removeHandler(handler)
        logger.level = previousLevel
    }

    private companion object {
        const val BOOTSTRAP_MESSAGE = "The LogManager accessed before"
    }
}
