package org.turbobrains.sobaklin.cli.util

import org.slf4j.Logger

@Suppress("unused")
class ParagraphLogger(
    private val normalPrefix: String,
    private val delegate: Logger,
) {
    private fun format(message: String?) = "\n" + message?.prependLinesWith(normalPrefix)

    fun trace(message: String?) = delegate.trace(format(message))
    fun debug(message: String?) = delegate.debug(format(message))
    fun info(message: String?) = delegate.info(format(message))
    fun warn(message: String?) = delegate.warn(format(message))

    fun error(message: String?) = delegate.error("\n" + message?.prependLinesWith("|| "))
    fun error(throwable: Throwable) = error(throwable.message ?: "Some error occurred")
}

fun Logger.toParagraph(normalPrefix: String): ParagraphLogger = ParagraphLogger(normalPrefix, this)
