package dev.staticsanches.kge.benchmark

import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Runs the JVM sweep; `--key=value` arguments (`sizes`, `modes`, `workloads`,
 * `highDpi`, `warmup`, `measure`) narrow it, so one comparison costs seconds.
 */
fun main(args: Array<String>): Unit =
    runBlocking {
        val options = parseBenchmarkOptions(args.toList(), jvmModes)
        println(
            "sweep sizes=${options.sizes.joinToString { "${it.x}x${it.y}" }} " +
                "modes=${options.modes.joinToString { "${it.label}/highDpi=${it.highDpi}" }} " +
                "workloads=${options.workloads.joinToString { it.label }} " +
                "warmup=${options.warmup} measure=${options.measure}",
        )
        val results = mutableListOf<BenchmarkResult>()
        runSweep(
            sizes = options.sizes,
            modes = options.modes,
            workloads = options.workloads,
            warmup = options.warmup,
            measure = options.measure,
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
