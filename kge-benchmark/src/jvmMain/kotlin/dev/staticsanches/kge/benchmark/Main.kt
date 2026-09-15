package dev.staticsanches.kge.benchmark

import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

fun main(): Unit =
    runBlocking {
        val results = mutableListOf<BenchmarkResult>()
        runSweep(
            sizes = benchmarkSizes,
            modes = jvmModes,
            workloads = benchmarkWorkloads,
            warmup = benchmarkWarmup,
            measure = benchmarkMeasure,
        ) { result ->
            results += result
            println(
                "${result.size.x}x${result.size.y} " +
                    "(${result.framebufferSize.x}x${result.framebufferSize.y}) " +
                    "${result.mode} ${result.workload} highDpi=${result.highDpi}: " +
                    "avg ${result.metrics.avgFps.roundToInt()} fps, " +
                    "min ${result.metrics.minFps} fps",
            )
        }
        println()
        println(formatTable(results))
    }
