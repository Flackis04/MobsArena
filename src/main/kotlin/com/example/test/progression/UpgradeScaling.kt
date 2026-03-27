package com.example.test

import kotlin.math.pow
import kotlin.math.roundToInt

object UpgradeScaling {
    fun exponentialProgress(level: Int, maxLevel: Int, growth: Double): Double {
        val clampedLevel = level.coerceIn(1, maxLevel.coerceAtLeast(1))
        if (maxLevel <= 1) return 1.0
        val normalized = (clampedLevel - 1).toDouble() / (maxLevel - 1).toDouble()
        return if (growth <= 1.0) {
            normalized
        } else {
            ((growth.pow(normalized) - 1.0) / (growth - 1.0)).coerceIn(0.0, 1.0)
        }
    }

    fun scale(level: Int, maxLevel: Int, minValue: Double, maxValue: Double, growth: Double): Double {
        val progress = exponentialProgress(level, maxLevel, growth)
        return minValue + ((maxValue - minValue) * progress)
    }

    fun scaleInt(level: Int, maxLevel: Int, minValue: Int, maxValue: Int, growth: Double): Int =
        scale(level, maxLevel, minValue.toDouble(), maxValue.toDouble(), growth).roundToInt()
}
