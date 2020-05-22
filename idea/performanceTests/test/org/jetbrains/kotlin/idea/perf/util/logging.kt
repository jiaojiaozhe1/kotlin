/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf.util

import java.io.PrintWriter
import java.io.StringWriter

inline fun gradleMessage(block: () -> String) {
    print("#gradle ${block()}")
}

inline fun logMessage(message: () -> String) {
    println("-- ${message()}")
}

inline fun tcMessage(block: () -> String) {
    println("##teamcity[${block()}]")
}

fun logMessage(t: Throwable, message: () -> String) {
    val writer = StringWriter()
    PrintWriter(writer).use {
        t.printStackTrace(it)
    }
    println("-- ${message()}:\n$writer")
}