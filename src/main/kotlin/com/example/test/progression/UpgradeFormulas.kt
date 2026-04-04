package com.example.test

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

object UpgradeFormulas {
    private const val FORTUNE_MAX_MULTIPLIER = 20.0
    private const val FORTUNE_GROWTH = 3.5
    private const val MULTI_BREAK_MAX_BASE = 7.5
    private const val MULTI_BREAK_GROWTH = 2.0
    private const val ORE_BOOST_MAX_CHANCE = 0.03
    private const val ORE_BOOST_GROWTH = 1.5
    private const val EXCAVATOR_MAX_CHANCE = 0.15
    private const val EXCAVATOR_GROWTH = 2.5
    private const val LIGHTNING_MAX_CHANCE = 0.04
    private const val LIGHTNING_GROWTH = 6.0
    private const val JACKHAMMER_MAX_CHANCE = 0.025
    private const val JACKHAMMER_GROWTH = 8.0
    private const val XP_GAIN_MAX_MULTIPLIER = 40.0
    private const val EXCAVATOR_EFFICIENCY_MAX = 7.5
    private const val EXCAVATOR_EFFICIENCY_GROWTH = 4.0
    private const val ORE_FREQUENCY_MAX_MULTIPLIER = 18.0
    private const val ORE_FREQUENCY_GROWTH = 2.0
    private const val SCROLL_FINDER_MAX_CHANCE = 0.00020
    private const val SCROLL_FINDER_GROWTH = 8.0
    private const val SELL_MULTIPLIER_MAX = 2.75
    private const val SELL_MULTIPLIER_GROWTH = 2.25
    private const val TOKEN_FINDER_MAX_CHANCE = 0.018
    private const val TOKEN_FINDER_GROWTH = 2.0
    private const val TOKEN_FINDER_MAX_AMOUNT = 5
    private const val KEY_FINDER_MAX_CHANCE = 0.0012
    private const val KEY_FINDER_GROWTH = 2.6
    private const val JACKPOT_MAX_CHANCE = 0.0015
    private const val JACKPOT_CHANCE_GROWTH = 3.0
    private const val COMBO_MAX_STREAK = 300
    private const val COMBO_STREAK_GROWTH = 2.0
    private const val COMBO_MAX_BONUS = 0.8
    private const val COMBO_BONUS_GROWTH = 2.3
    private const val PROC_POWER_CHANCE_BONUS_MAX = 0.025
    private const val PROC_POWER_CHANCE_BONUS_GROWTH = 2.0
    private const val AUTO_MINER_LUCK_MAX_MULTIPLIER = 1.5
    private const val AUTO_MINER_OFFLINE_MAX_RATE = 0.25
    private const val AUTO_MINER_EFFICIENCY_GROWTH = 5.0
    private const val AUTO_MINER_OFFLINE_GROWTH = 4.0
    private const val AUTO_MINER_LUCK_GROWTH = 4.0
    private const val AUTO_MINER_PAYOUT_MAX_CHANCE = 0.3
    private const val AUTO_MINER_PAYOUT_GROWTH = 3.2

    private fun scaledMaxValue(baseMaxValue: Double, baseMaxLevel: Int, effectiveMaxLevel: Int): Double {
        val safeBaseMaxLevel = baseMaxLevel.coerceAtLeast(1)
        val safeEffectiveMaxLevel = effectiveMaxLevel.coerceAtLeast(1)
        return baseMaxValue * (safeEffectiveMaxLevel.toDouble() / safeBaseMaxLevel.toDouble())
    }

    private fun scalePreservingBaseCap(
        level: Int,
        baseMaxLevel: Int,
        effectiveMaxLevel: Int,
        minValue: Double,
        baseMaxValue: Double,
        growth: Double
    ): Double {
        val safeBaseMaxLevel = baseMaxLevel.coerceAtLeast(1)
        val safeEffectiveMaxLevel = effectiveMaxLevel.coerceAtLeast(safeBaseMaxLevel)
        val clampedLevel = level.coerceIn(1, safeEffectiveMaxLevel)
        if (safeEffectiveMaxLevel == safeBaseMaxLevel || clampedLevel <= safeBaseMaxLevel) {
            return UpgradeScaling.scale(clampedLevel, safeBaseMaxLevel, minValue, baseMaxValue, growth)
        }

        val scaledMax = scaledMaxValue(baseMaxValue, safeBaseMaxLevel, safeEffectiveMaxLevel)
        val extraLevels = safeEffectiveMaxLevel - safeBaseMaxLevel
        val extraProgressLevel = (clampedLevel - safeBaseMaxLevel) + 1
        return UpgradeScaling.scale(extraProgressLevel, extraLevels + 1, baseMaxValue, scaledMax, growth)
    }

    private fun exponentialScalePreservingBaseCap(
        level: Int,
        baseMaxLevel: Int,
        effectiveMaxLevel: Int,
        baseMaxValue: Double
    ): Double {
        val safeBaseMaxLevel = baseMaxLevel.coerceAtLeast(1)
        val safeEffectiveMaxLevel = effectiveMaxLevel.coerceAtLeast(safeBaseMaxLevel)
        val clampedLevel = level.coerceIn(1, safeEffectiveMaxLevel)
        if (safeEffectiveMaxLevel == safeBaseMaxLevel || clampedLevel <= safeBaseMaxLevel) {
            val progress = if (safeBaseMaxLevel <= 1) 1.0 else (clampedLevel - 1).toDouble() / (safeBaseMaxLevel - 1).toDouble()
            return baseMaxValue.pow(progress)
        }

        val scaledMax = scaledMaxValue(baseMaxValue, safeBaseMaxLevel, safeEffectiveMaxLevel)
        val extraLevels = safeEffectiveMaxLevel - safeBaseMaxLevel
        val extraProgress = if (extraLevels <= 0) 1.0 else (clampedLevel - safeBaseMaxLevel).toDouble() / extraLevels.toDouble()
        return baseMaxValue * (scaledMax / baseMaxValue).pow(extraProgress)
    }

    private fun scaleIntPreservingBaseCap(
        level: Int,
        baseMaxLevel: Int,
        effectiveMaxLevel: Int,
        minValue: Int,
        baseMaxValue: Int,
        growth: Double
    ): Int = scalePreservingBaseCap(
        level,
        baseMaxLevel,
        effectiveMaxLevel,
        minValue.toDouble(),
        baseMaxValue.toDouble(),
        growth
    ).roundToInt()

    fun getMultiBreakBlockQuantity(
        level: Int,
        hasBoost: Boolean,
        maxLevel: Int = LevelManager.multiBreakMaxLevelWithScroll,
        scrollBonus: Double = 0.0,
        excavatorActive: Boolean = false,
        excavatorEfficiencyLevel: Int = 1
    ): Double {
        val boost = if (hasBoost) 3.0 else 0.0
        val maxBaseValue = getMultiBreakMaxBlocks(level, maxLevel, scrollBonus) + boost
        if (maxBaseValue <= 0.0 || maxBaseValue.isNaN() || maxBaseValue.isInfinite()) return 0.0

        val baseValue = Random.nextDouble(maxBaseValue)
        if (!excavatorActive) return baseValue

        val maxExcavatedValue = baseValue * excavatorEfficiencyLevel.coerceAtLeast(1)
        if (maxExcavatedValue <= 0.0 || maxExcavatedValue.isNaN() || maxExcavatedValue.isInfinite()) return 0.0

        return Random.nextDouble(0.0, maxExcavatedValue)
    }

    fun getMultiBreakMaxBlocks(level: Int, maxLevel: Int = LevelManager.multiBreakMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.multiBreakMaxLevel, maxLevel, 0.0, MULTI_BREAK_MAX_BASE, MULTI_BREAK_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getFortuneMultiplier(level: Int, maxLevel: Int = LevelManager.fortuneMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.fortuneMaxLevel, maxLevel, 1.0, FORTUNE_MAX_MULTIPLIER, FORTUNE_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getOreBoostChance(level: Int, maxLevel: Int = LevelManager.oreBoostMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.oreBoostMaxLevel, maxLevel, 0.0, ORE_BOOST_MAX_CHANCE, ORE_BOOST_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getExcavatorChance(level: Int, maxLevel: Int = LevelManager.excavatorMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.excavatorMaxLevel, maxLevel, 0.0, EXCAVATOR_MAX_CHANCE, EXCAVATOR_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getLightningChance(level: Int, maxLevel: Int = LevelManager.lightningMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.lightningMaxLevel, maxLevel, 0.0, LIGHTNING_MAX_CHANCE, LIGHTNING_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getVirtualJackhammerChance(level: Int, maxLevel: Int = LevelManager.virtualJackhammerMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.virtualJackhammerMaxLevel, maxLevel, 0.0, JACKHAMMER_MAX_CHANCE, JACKHAMMER_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getExperienceMultiplier(level: Int, maxLevel: Int = LevelManager.xpGainMaxLevelWithScroll, scrollBonus: Double = 0.0): Double {
        return exponentialScalePreservingBaseCap(level, LevelManager.xpGainMaxLevel, maxLevel, XP_GAIN_MAX_MULTIPLIER) +
            scrollBonus.coerceAtLeast(0.0)
    }

    fun getExcavatorEfficiency(level: Int, maxLevel: Int = LevelManager.excavatorEfficiencyMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.excavatorEfficiencyMaxLevel, maxLevel, 1.0, EXCAVATOR_EFFICIENCY_MAX, EXCAVATOR_EFFICIENCY_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getOreFrequencyMultiplier(level: Int, maxLevel: Int = LevelManager.oreFrequencyMaxLevelWithScroll, scrollBonus: Double = 0.0): Double =
        scalePreservingBaseCap(level, LevelManager.oreFrequencyMaxLevel, maxLevel, 1.0, ORE_FREQUENCY_MAX_MULTIPLIER, ORE_FREQUENCY_GROWTH) +
            scrollBonus.coerceAtLeast(0.0)

    fun getScrollFinderChance(level: Int, maxLevel: Int = LevelManager.scrollFinderMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.scrollFinderMaxLevel, maxLevel, 0.0, SCROLL_FINDER_MAX_CHANCE, SCROLL_FINDER_GROWTH)

    fun getSellMultiplier(level: Int, maxLevel: Int = LevelManager.sellMultiplierMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.sellMultiplierMaxLevel, maxLevel, 1.0, SELL_MULTIPLIER_MAX, SELL_MULTIPLIER_GROWTH)

    fun getTokenFinderChance(level: Int, maxLevel: Int = LevelManager.tokenFinderMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.tokenFinderMaxLevel, maxLevel, 0.0, TOKEN_FINDER_MAX_CHANCE, TOKEN_FINDER_GROWTH)

    fun getTokenFinderAmount(level: Int, maxLevel: Int = LevelManager.tokenFinderMaxLevelWithScroll): Int =
        scaleIntPreservingBaseCap(level, LevelManager.tokenFinderMaxLevel, maxLevel, 1, TOKEN_FINDER_MAX_AMOUNT, 1.85)

    fun getKeyFinderChance(level: Int, maxLevel: Int = LevelManager.keyFinderMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.keyFinderMaxLevel, maxLevel, 0.0, KEY_FINDER_MAX_CHANCE, KEY_FINDER_GROWTH)

    fun getJackpotChance(level: Int, maxLevel: Int = LevelManager.jackpotMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.jackpotMaxLevel, maxLevel, 0.0, JACKPOT_MAX_CHANCE, JACKPOT_CHANCE_GROWTH)

    fun getComboMaxStreak(level: Int, maxLevel: Int = LevelManager.comboMaxLevelWithScroll): Int =
        scaleIntPreservingBaseCap(level, LevelManager.comboMaxLevel, maxLevel, 5, COMBO_MAX_STREAK, COMBO_STREAK_GROWTH)

    fun getComboBonusMultiplier(level: Int, streak: Int, maxLevel: Int = LevelManager.comboMaxLevelWithScroll): Double {
        val maxStreak = getComboMaxStreak(level, maxLevel).coerceAtLeast(1)
        val effectiveStreak = streak.coerceIn(0, maxStreak)
        val maxBonus = scalePreservingBaseCap(level, LevelManager.comboMaxLevel, maxLevel, 0.0, COMBO_MAX_BONUS, COMBO_BONUS_GROWTH)
        return 1.0 + UpgradeScaling.scale(effectiveStreak + 1, maxStreak + 1, 0.0, maxBonus, 1.8)
    }

    fun getProcPowerChanceBonus(level: Int, maxLevel: Int = LevelManager.procPowerMaxLevelWithScroll): Double =
        scalePreservingBaseCap(level, LevelManager.procPowerMaxLevel, maxLevel, 0.0, PROC_POWER_CHANCE_BONUS_MAX, PROC_POWER_CHANCE_BONUS_GROWTH)

    fun getAutoMinerLuckMultiplier(level: Int): Double =
        UpgradeScaling.scale(level, LevelManager.autoMinerLuckMaxLevelWithScroll, 1.0, AUTO_MINER_LUCK_MAX_MULTIPLIER, AUTO_MINER_LUCK_GROWTH)

    fun getAutoMinerOfflineYieldRate(level: Int): Double =
        UpgradeScaling.scale(level, LevelManager.autoMinerEnergyDrinkMaxLevelWithScroll, 0.0, AUTO_MINER_OFFLINE_MAX_RATE, AUTO_MINER_OFFLINE_GROWTH)

    fun getAutoMinerProcessingAttempts(level: Int): Int =
        UpgradeScaling.scaleInt(level, LevelManager.autoMinerEfficiencyMaxLevelWithScroll, 1, LevelManager.autoMinerEfficiencyMaxLevelWithScroll, AUTO_MINER_EFFICIENCY_GROWTH)

    fun getAutoMinerPayoutChance(level: Int): Double =
        if (level <= 1) 0.0 else UpgradeScaling.scale(level, LevelManager.autoMinerPayoutMaxLevelWithScroll, 0.0, AUTO_MINER_PAYOUT_MAX_CHANCE, AUTO_MINER_PAYOUT_GROWTH)
}
