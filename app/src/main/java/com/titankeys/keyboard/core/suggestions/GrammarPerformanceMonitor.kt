package com.titankeys.keyboard.core.suggestions

import android.util.Log
import kotlin.math.min

/**
 * Monitors performance metrics for grammar correction operations
 */
class GrammarPerformanceMonitor {
    companion object {
        private const val TAG = "GrammarPerfMonitor"
        private const val MAX_METRICS_HISTORY = 1000
        private const val GRAMMAR_TIMEOUT_MS = 100L
    }

    private val metrics = mutableListOf<GrammarPerformanceMetric>()

    /**
     * Record inference time for a grammar model
     */
    fun recordInferenceTime(modelLanguage: String, timeMs: Long, success: Boolean) {
        val metric = GrammarPerformanceMetric(
            timestamp = System.currentTimeMillis(),
            operation = "inference",
            durationMs = timeMs,
            success = success,
            modelLanguage = modelLanguage
        )

        synchronized(metrics) {
            metrics.add(metric)

            // Keep only recent metrics
            if (metrics.size > MAX_METRICS_HISTORY) {
                metrics.removeAt(0)
            }
        }

        // Log performance issues
        if (!success) {
            Log.w(TAG, "Grammar inference failed for $modelLanguage after ${timeMs}ms")
        } else if (timeMs > GRAMMAR_TIMEOUT_MS) {
            Log.w(TAG, "Grammar inference slow for $modelLanguage: ${timeMs}ms")
        } else {
            Log.d(TAG, "Grammar inference completed for $modelLanguage in ${timeMs}ms")
        }
    }

    /**
     * Record tokenization performance
     */
    fun recordTokenizationTime(timeMs: Long, sentenceLength: Int) {
        val metric = GrammarPerformanceMetric(
            timestamp = System.currentTimeMillis(),
            operation = "tokenization",
            durationMs = timeMs,
            success = true,
            sentenceLength = sentenceLength
        )

        synchronized(metrics) {
            metrics.add(metric)
            if (metrics.size > MAX_METRICS_HISTORY) {
                metrics.removeAt(0)
            }
        }
    }

    /**
     * Get average inference time across all models
     */
    fun getAverageInferenceTime(): Double {
        return synchronized(metrics) {
            val inferenceMetrics = metrics.filter {
                it.operation == "inference" && it.success && it.durationMs > 0
            }

            if (inferenceMetrics.isEmpty()) {
                0.0
            } else {
                inferenceMetrics.map { it.durationMs }.average()
            }
        }
    }

    /**
     * Get average inference time for a specific language
     */
    fun getAverageInferenceTimeForLanguage(language: String): Double {
        return synchronized(metrics) {
            val languageMetrics = metrics.filter {
                it.operation == "inference" && it.success && it.modelLanguage == language && it.durationMs > 0
            }

            if (languageMetrics.isEmpty()) {
                0.0
            } else {
                languageMetrics.map { it.durationMs }.average()
            }
        }
    }

    /**
     * Get success rate for inference operations
     */
    fun getInferenceSuccessRate(): Double {
        return synchronized(metrics) {
            val inferenceMetrics = metrics.filter { it.operation == "inference" }

            if (inferenceMetrics.isEmpty()) {
                0.0
            } else {
                val successful = inferenceMetrics.count { it.success }
                successful.toDouble() / inferenceMetrics.size
            }
        }
    }

    /**
     * Get recent performance metrics
     */
    fun getRecentMetrics(count: Int = 50): List<GrammarPerformanceMetric> {
        return synchronized(metrics) {
            metrics.takeLast(min(count, metrics.size))
        }
    }

    /**
     * Check if performance is degrading
     */
    fun isPerformanceDegrading(): Boolean {
        val recentMetrics = getRecentMetrics(20)
        val olderMetrics = getRecentMetrics(40).dropLast(20)

        if (recentMetrics.size < 10 || olderMetrics.size < 10) {
            return false // Not enough data
        }

        val recentAvg = recentMetrics.filter { it.success }.map { it.durationMs }.average()
        val olderAvg = olderMetrics.filter { it.success }.map { it.durationMs }.average()

        // Consider degrading if recent average is 50% slower
        return recentAvg > olderAvg * 1.5
    }

    /**
     * Clear all metrics (for testing or reset)
     */
    fun clearMetrics() {
        synchronized(metrics) {
            metrics.clear()
        }
    }
}