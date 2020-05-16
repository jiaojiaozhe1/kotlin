/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import org.jetbrains.kotlin.idea.perf.Stats.Companion.printStatValue
import org.jetbrains.kotlin.idea.perf.Stats.Companion.tcSuite
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import org.jetbrains.kotlin.idea.testFramework.logMessage
import java.io.File

class HighlightWholeProjectPerformanceTest : AbstractPerformanceProjectsTest() {

    companion object {

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @JvmStatic
        val warmUp = WarmUpProject(hwStats)

        init {
            // there is no @AfterClass for junit3.8
            Runtime.getRuntime().addShutdownHook(Thread { hwStats.close() })
        }

    }

    override fun setUp() {
        super.setUp()
        warmUp.warmUp(this)

        val allowedErrorDescription = setOf(
            "Unknown artifact type: war",
            "Unknown artifact type: exploded-war"
        )

        ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(
            { errorDescription ->
                val description = errorDescription.description
                if (description !in allowedErrorDescription) {
                    throw RuntimeException(description)
                } else {
                    logMessage { "project loading error: '$description' at '${errorDescription.elementName}'" }
                }
            }, testRootDisposable
        )
    }

    fun testHighlightAllKtFilesInProject() {
        val emptyProfile = System.getProperty("emptyProfile", "false").toBoolean()
        val projectSpecs = projectSpecs()
        for (projectSpec in projectSpecs) {
            val projectName = projectSpec.name
            val projectPath = projectSpec.path

            val suiteName = "$projectName project"
            try {
                tcSuite(suiteName) {
                    val stats = Stats(suiteName)
                    stats.use { stat ->
                        perfGradleBasedProject(projectName, projectPath, stat)
                        //perfJpsBasedProject(projectName, stat)

                        try {
                            val projectDir = File(projectPath)
                            val ktFiles = projectDir.allFilesWithExtension("kt").toList()
                            printStatValue("$suiteName: number of kt files", ktFiles.size)
                            val topMidLastFiles =
                                limitedFiles(ktFiles, 10)
                                    .map {
                                        val path = it.path
                                        it to path.substring(path.indexOf(projectPath) + projectPath.length + 1)
                                    }
                            printStatValue("$suiteName: limited number of kt files", topMidLastFiles.size)

                            topMidLastFiles.forEach {
                                logMessage { "${it.second} fileSize: ${it.first.length()}" }
                            }

                            topMidLastFiles.forEachIndexed { idx, file ->
                                logMessage { "$idx / ${topMidLastFiles.size} : ${file.second} fileSize: ${file.first.length()}" }
                                try {
                                    // 1x3 it not good enough for statistics, but at least it gives some overview
                                    perfHighlightFile(
                                        project(),
                                        fileName = file.second,
                                        stats = stat,
                                        warmUpIterations = 1,
                                        iterations = 3,
                                        filenameSimplifier = { it },
                                        tools = if (emptyProfile) emptyArray() else null,
                                        checkStability = false
                                    )
                                } catch (e: Exception) {
                                    // nothing as it is already caught by perfTest
                                }
                            }
                        } finally {
                            closeProject()
                        }
                    }
                }
            } catch (e: Exception) {
                // don't fail entire test on a single failure
            }
        }
    }

    private fun limitedFiles(ktFiles: List<File>, partPercent: Int): Collection<File> {
        val sortedBySize = ktFiles
            .filter { it.length() > 0 }
            .map { it to it.length() }.sortedByDescending { it.second }
        val percentOfFiles = (sortedBySize.size * partPercent) / 100

        val topFiles = sortedBySize.take(percentOfFiles).map { it.first }
        val midFiles =
            sortedBySize.take(sortedBySize.size / 2 + percentOfFiles / 2).takeLast(percentOfFiles).map { it.first }
        val lastFiles = sortedBySize.takeLast(percentOfFiles).map { it.first }

        return LinkedHashSet(topFiles + midFiles + lastFiles)
    }

    private fun perfGradleBasedProject(name: String, path: String, stats: Stats) {
        myProject = perfOpenProject(
            name = name,
            stats = stats,
            note = "",
            path = path,
            openAction = ProjectOpenAction.GRADLE_PROJECT,
            fast = true
        )
    }

    private fun projectSpecs(): List<ProjectSpec> {
        val projects = System.getProperty("performanceProjects") ?: return emptyList()
        return projects.split(",").map {
            val idx = it.indexOf("=")
            if (idx <= 0) ProjectSpec(it, "../$it") else ProjectSpec(it.substring(0, idx), it.substring(idx + 1))
        }.filter {
            val path = File(it.path)
            path.exists() && path.isDirectory
        }
    }

    private data class ProjectSpec(val name: String, val path: String)
}