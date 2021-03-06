/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.AsyncStackTraceProvider
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutinePreflightStackFrame
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CoroutineFrameBuilder
import org.jetbrains.kotlin.idea.debugger.coroutine.util.threadAndContextSupportsEvaluation
import org.jetbrains.kotlin.idea.debugger.hopelessAware

class CoroutineAsyncStackTraceProvider : AsyncStackTraceProvider {

    override fun getAsyncStackTrace(stackFrame: JavaStackFrame, suspendContext: SuspendContextImpl): List<StackFrameItem>? {
        val stackFrameList = hopelessAware {
            if (stackFrame is CoroutinePreflightStackFrame)
                processPreflight(stackFrame, suspendContext)
            else
                null
        }
        return if (stackFrameList == null || stackFrameList.isEmpty())
            null
        else stackFrameList
    }

    private fun processPreflight(
        preflightFrame: CoroutinePreflightStackFrame,
        suspendContext: SuspendContextImpl
    ): List<CoroutineStackFrameItem>? {
        val resumeWithFrame = preflightFrame.threadPreCoroutineFrames.firstOrNull()

        if (threadAndContextSupportsEvaluation(
                suspendContext,
                resumeWithFrame
            )
        ) {
            val doubleFrameList = CoroutineFrameBuilder.build(preflightFrame, suspendContext)
            val resultList = doubleFrameList.stackTrace + doubleFrameList.creationStackTrace
            return PreflightProvider(preflightFrame, resultList)
        }
        return null
    }
}

class PreflightProvider(private val preflight: CoroutinePreflightStackFrame, stackFrames: List<CoroutineStackFrameItem>) :
    List<CoroutineStackFrameItem> by stackFrames {
    fun getPreflight() =
        preflight
}