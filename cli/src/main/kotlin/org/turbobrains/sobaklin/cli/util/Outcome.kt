package org.turbobrains.sobaklin.cli.util

sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Error(val throwable: Throwable) : Outcome<Nothing>()
}
