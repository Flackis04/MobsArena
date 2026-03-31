package com.example.test

import kotlin.math.pow
import kotlin.math.round

object LevelManager {
    const val MAX_REBIRTH_LEVEL = 26
    private const val BASE_REBIRTH_REQUIRED_LEVEL = 250
    private const val REBIRTH_REQUIRED_LEVEL_STEP = 25

    const val lightningMaxAddedLevels = 125
    const val virtualJackhammerMaxAddedLevels = 125
    const val xpGainMaxAddedLevels = 125
    const val oreFrequencyMaxAddedLevels = 125

    var rankMaxLevel = 39
    var multiBreakMaxLevel = 75
    var multiBreakMaxLevelWithScroll = multiBreakMaxLevel
    var fortuneMaxLevel = 50
    var fortuneMaxLevelWithScroll = fortuneMaxLevel
    var oreBoostMaxLevel = 75
    var oreBoostMaxLevelWithScroll = oreBoostMaxLevel
    var excavatorMaxLevel = 100
    var excavatorMaxLevelWithScroll = excavatorMaxLevel
    var lightningMaxLevel = 75
    var lightningMaxLevelWithScroll = lightningMaxLevel + lightningMaxAddedLevels
    var virtualJackhammerMaxLevel = 250
    var virtualJackhammerMaxLevelWithScroll = virtualJackhammerMaxLevel + virtualJackhammerMaxAddedLevels
    var excavatorEfficiencyMaxLevel = 25
    var excavatorEfficiencyMaxLevelWithScroll = excavatorEfficiencyMaxLevel
    var xpGainMaxLevel = 125
    var xpGainMaxLevelWithScroll = xpGainMaxLevel + xpGainMaxAddedLevels
    var oreFrequencyMaxLevel = 50
    var oreFrequencyMaxLevelWithScroll = oreFrequencyMaxLevel + oreFrequencyMaxAddedLevels
    var scrollFinderMaxLevel = 100
    var scrollFinderMaxLevelWithScroll = scrollFinderMaxLevel
    var backpackMaxLevel = 250
    var backpackMaxLevelWithScroll = backpackMaxLevel

    var autoMinerFortuneMaxLevel = 75
    var autoMinerFortuneMaxLevelWithScroll = autoMinerFortuneMaxLevel
    var autoMinerEfficiencyMaxLevel = 100
    var autoMinerEfficiencyMaxLevelWithScroll = autoMinerEfficiencyMaxLevel
    var autoMinerEnergyDrinkMaxLevel = 75
    var autoMinerEnergyDrinkMaxLevelWithScroll = autoMinerEnergyDrinkMaxLevel
    var autoMinerBackpackMaxLevel = 500
    var autoMinerBackpackMaxLevelWithScroll = autoMinerBackpackMaxLevel
    var autoMinerLuckMaxLevel = 100
    var autoMinerLuckMaxLevelWithScroll = autoMinerLuckMaxLevel

    val upgradeRankCosts = mutableMapOf<Int, Long>()
    val upgradeMultiBreakCosts = mutableMapOf<Int, Long>()
    val upgradeFortuneCosts = mutableMapOf<Int, Long>()
    val upgradeOreBoostCosts = mutableMapOf<Int, Long>()
    val upgradeExcavatorCosts = mutableMapOf<Int, Long>()
    val upgradeLightningCosts = mutableMapOf<Int, Long>()
    val upgradeVirtualJackhammerCosts = mutableMapOf<Int, Long>()
    val upgradeExcavatorEfficiencyCosts = mutableMapOf<Int, Long>()
    val upgradeXpGainCosts = mutableMapOf<Int, Long>()
    val upgradeOreFrequencyCosts = mutableMapOf<Int, Long>()
    val upgradeScrollFinderCosts = mutableMapOf<Int, Long>()
    val upgradeBackpackCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerFortuneCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerEfficiencyCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerEnergyDrinkCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerBackpackCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerLuckCosts = mutableMapOf<Int, Long>()


    fun init() {
        upgradeRankCosts.clear()
        upgradeMultiBreakCosts.clear()
        upgradeFortuneCosts.clear()
        upgradeOreBoostCosts.clear()
        upgradeExcavatorCosts.clear()
        upgradeLightningCosts.clear()
        upgradeVirtualJackhammerCosts.clear()
        upgradeExcavatorEfficiencyCosts.clear()
        upgradeXpGainCosts.clear()
        upgradeOreFrequencyCosts.clear()
        upgradeScrollFinderCosts.clear()
        upgradeBackpackCosts.clear()
        upgradeAutoMinerFortuneCosts.clear()
        upgradeAutoMinerEfficiencyCosts.clear()
        upgradeAutoMinerEnergyDrinkCosts.clear()
        upgradeAutoMinerBackpackCosts.clear()
        upgradeAutoMinerLuckCosts.clear()


        generateCostTable(upgradeRankCosts, 500, 520.0, 1.68, rankMaxLevel + MAX_REBIRTH_LEVEL)
        generateCostTable(upgradeMultiBreakCosts, 10, 40.0, 1.8, multiBreakMaxLevelWithScroll)
        generateCostTable(upgradeOreBoostCosts, 25, 65.0, 1.8, oreBoostMaxLevelWithScroll)
        generateCostTable(upgradeFortuneCosts, 180, 800.0, 2.5, fortuneMaxLevelWithScroll + MAX_REBIRTH_LEVEL)
        generateCostTable(upgradeExcavatorCosts, 80, 70.0, 2.05, excavatorMaxLevelWithScroll)
        generateCostTable(upgradeLightningCosts, 2_500, 8_500.0, 2.15, lightningMaxLevelWithScroll)
        generateCostTable(upgradeVirtualJackhammerCosts, 10_000, 25_000.0, 2.35, virtualJackhammerMaxLevelWithScroll)
        generateCostTable(upgradeExcavatorEfficiencyCosts, 1000, 60000.0, 2.35, excavatorEfficiencyMaxLevelWithScroll)
        generateCostTable(upgradeXpGainCosts, 20, 35.0, 1.4, xpGainMaxLevelWithScroll)
        generateCostTable(upgradeOreFrequencyCosts, 2_500, 6_000.0, 2.1, oreFrequencyMaxLevelWithScroll)
        generateCostTable(upgradeScrollFinderCosts, 5_000, 12_500.0, 2.25, scrollFinderMaxLevelWithScroll)
        generateCostTable(upgradeBackpackCosts, 750, 2_500.0, 1.85, backpackMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerFortuneCosts, 2_500, 7_500.0, 2.5, autoMinerFortuneMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerEfficiencyCosts, 3_500, 6_000.0, 2.25, autoMinerEfficiencyMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerEnergyDrinkCosts, 5_000, 9_000.0, 2.4, autoMinerEnergyDrinkMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerBackpackCosts, 1_500, 4_500.0, 1.72, autoMinerBackpackMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerLuckCosts, 2_000, 5_500.0, 1.9, autoMinerLuckMaxLevelWithScroll)
    }

    private fun generateCostTable(
        map: MutableMap<Int, Long>,
        baseCost: Long,
        multiplier: Double = 1.0,
        exponent: Double,
        maxLevel: Int
    ) {
        for (level in 2..maxLevel) {
            val step = (level - 2).toDouble()
            val cost = round(baseCost + (multiplier * step.pow(exponent))).toLong().coerceAtLeast(baseCost)
            map[level] = cost
        }
    }

    fun checkLevels(data: PlayerData) {
        if (data.rank <= 0) data.rank = 1
        if (data.multiBreakMaxLevel <= 0) data.multiBreakMaxLevel = multiBreakMaxLevel
        if (data.fortuneMaxLevel <= 0) data.fortuneMaxLevel = fortuneMaxLevel
        if (data.oreBoostMaxLevel <= 0) data.oreBoostMaxLevel = oreBoostMaxLevel
        if (data.excavatorMaxLevel <= 0) data.excavatorMaxLevel = excavatorMaxLevel
        if (data.lightningMaxLevel <= 0) data.lightningMaxLevel = lightningMaxLevel
        if (data.virtualJackhammerMaxLevel <= 0) data.virtualJackhammerMaxLevel = virtualJackhammerMaxLevel
        if (data.excavatorEfficiencyMaxLevel <= 0) data.excavatorEfficiencyMaxLevel = excavatorEfficiencyMaxLevel
        if (data.xpGainMaxLevel <= 0) data.xpGainMaxLevel = xpGainMaxLevel
        if (data.oreFrequencyMaxLevel <= 0) data.oreFrequencyMaxLevel = oreFrequencyMaxLevel
        if (data.scrollFinderMaxLevel <= 0) data.scrollFinderMaxLevel = scrollFinderMaxLevel
        if (data.backpackMaxLevel <= 0) data.backpackMaxLevel = backpackMaxLevel
        if (data.autoMinerFortuneMaxLevel <= 0) data.autoMinerFortuneMaxLevel = autoMinerFortuneMaxLevel
        if (data.autoMinerEfficiencyMaxLevel <= 0) data.autoMinerEfficiencyMaxLevel = autoMinerEfficiencyMaxLevel
        if (data.autoMinerEnergyDrinkMaxLevel <= 0) data.autoMinerEnergyDrinkMaxLevel = autoMinerEnergyDrinkMaxLevel
        if (data.autoMinerBackpackMaxLevel <= 0) data.autoMinerBackpackMaxLevel = autoMinerBackpackMaxLevel
        if (data.autoMinerLuckMaxLevel <= 0) data.autoMinerLuckMaxLevel = autoMinerLuckMaxLevel

        data.multiBreakMaxLevel = data.multiBreakMaxLevel.coerceIn(multiBreakMaxLevel, multiBreakMaxLevelWithScroll)
        data.fortuneMaxLevel = data.fortuneMaxLevel.coerceIn(fortuneMaxLevel, fortuneMaxLevelWithScroll)
        data.oreBoostMaxLevel = data.oreBoostMaxLevel.coerceIn(oreBoostMaxLevel, oreBoostMaxLevelWithScroll)
        data.excavatorMaxLevel = data.excavatorMaxLevel.coerceIn(excavatorMaxLevel, excavatorMaxLevelWithScroll)
        data.lightningMaxLevel = data.lightningMaxLevel.coerceIn(lightningMaxLevel, lightningMaxLevelWithScroll)
        data.virtualJackhammerMaxLevel = data.virtualJackhammerMaxLevel.coerceIn(virtualJackhammerMaxLevel, virtualJackhammerMaxLevelWithScroll)
        data.excavatorEfficiencyMaxLevel = data.excavatorEfficiencyMaxLevel.coerceIn(excavatorEfficiencyMaxLevel, excavatorEfficiencyMaxLevelWithScroll)
        data.xpGainMaxLevel = data.xpGainMaxLevel.coerceIn(xpGainMaxLevel, xpGainMaxLevelWithScroll)
        data.oreFrequencyMaxLevel = data.oreFrequencyMaxLevel.coerceIn(oreFrequencyMaxLevel, oreFrequencyMaxLevelWithScroll)
        data.scrollFinderMaxLevel = data.scrollFinderMaxLevel.coerceIn(scrollFinderMaxLevel, scrollFinderMaxLevelWithScroll)
        data.backpackMaxLevel = data.backpackMaxLevel.coerceIn(backpackMaxLevel, backpackMaxLevelWithScroll)
        data.autoMinerFortuneMaxLevel = data.autoMinerFortuneMaxLevel.coerceIn(autoMinerFortuneMaxLevel, autoMinerFortuneMaxLevelWithScroll)
        data.autoMinerEfficiencyMaxLevel = data.autoMinerEfficiencyMaxLevel.coerceIn(autoMinerEfficiencyMaxLevel, autoMinerEfficiencyMaxLevelWithScroll)
        data.autoMinerEnergyDrinkMaxLevel = data.autoMinerEnergyDrinkMaxLevel.coerceIn(autoMinerEnergyDrinkMaxLevel, autoMinerEnergyDrinkMaxLevelWithScroll)
        data.autoMinerBackpackMaxLevel = data.autoMinerBackpackMaxLevel.coerceIn(autoMinerBackpackMaxLevel, autoMinerBackpackMaxLevelWithScroll)
        data.autoMinerLuckMaxLevel = data.autoMinerLuckMaxLevel.coerceIn(autoMinerLuckMaxLevel, autoMinerLuckMaxLevelWithScroll)

        if (data.multiBreakLevel <= 0) data.multiBreakLevel = 1
        if (data.fortuneLevel <= 0) data.fortuneLevel = 1
        if (data.oreBoostLevel <= 0) data.oreBoostLevel = 1
        if (data.lightningLevel <= 0) data.lightningLevel = 1
        if (data.virtualJackhammerLevel <= 0) data.virtualJackhammerLevel = 1
        if (data.xpGainLevel <= 0) data.xpGainLevel = 1
        if (data.oreFrequencyLevel <= 0) data.oreFrequencyLevel = 1
        if (data.scrollFinderLevel <= 0) data.scrollFinderLevel = 1
        if (data.backpackLevel <= 0) data.backpackLevel = 1
        if (data.autoMinerFortuneLevel <= 0) data.autoMinerFortuneLevel = 1
        if (data.autoMinerEfficiencyLevel <= 0) data.autoMinerEfficiencyLevel = 1
        if (data.autoMinerEnergyDrinkLevel <= 0) data.autoMinerEnergyDrinkLevel = 1
        if (data.autoMinerBackpackLevel <= 0) data.autoMinerBackpackLevel = 1
        if (data.autoMinerLuckLevel <= 0) data.autoMinerLuckLevel = 1

        if (data.excavatorLevel <= 0) data.excavatorLevel = 1

        data.multiBreakLevel = data.multiBreakLevel.coerceAtMost(data.multiBreakMaxLevel)
        data.fortuneLevel = data.fortuneLevel.coerceAtMost(data.fortuneMaxLevel)
        data.oreBoostLevel = data.oreBoostLevel.coerceAtMost(data.oreBoostMaxLevel)
        data.excavatorLevel = data.excavatorLevel.coerceAtMost(data.excavatorMaxLevel)
        data.lightningLevel = data.lightningLevel.coerceAtMost(data.lightningMaxLevel)
        data.virtualJackhammerLevel = data.virtualJackhammerLevel.coerceAtMost(data.virtualJackhammerMaxLevel)
        data.excavatorEfficiencyLevel = data.excavatorEfficiencyLevel.coerceAtMost(data.excavatorEfficiencyMaxLevel)
        data.xpGainLevel = data.xpGainLevel.coerceAtMost(data.xpGainMaxLevel)
        data.oreFrequencyLevel = data.oreFrequencyLevel.coerceAtMost(data.oreFrequencyMaxLevel)
        data.scrollFinderLevel = data.scrollFinderLevel.coerceAtMost(data.scrollFinderMaxLevel)
        data.backpackLevel = data.backpackLevel.coerceAtMost(data.backpackMaxLevel)
        data.autoMinerFortuneLevel = data.autoMinerFortuneLevel.coerceAtMost(data.autoMinerFortuneMaxLevel)
        data.autoMinerEfficiencyLevel = data.autoMinerEfficiencyLevel.coerceAtMost(data.autoMinerEfficiencyMaxLevel)
        data.autoMinerEnergyDrinkLevel = data.autoMinerEnergyDrinkLevel.coerceAtMost(data.autoMinerEnergyDrinkMaxLevel)
        data.autoMinerBackpackLevel = data.autoMinerBackpackLevel.coerceAtMost(data.autoMinerBackpackMaxLevel)
        data.autoMinerLuckLevel = data.autoMinerLuckLevel.coerceAtMost(data.autoMinerLuckMaxLevel)
    }

    fun getRequiredLevelForNextRebirth(currentRebirth: Int): Int {
        val clampedRebirth = currentRebirth.coerceIn(0, MAX_REBIRTH_LEVEL)
        return BASE_REBIRTH_REQUIRED_LEVEL + (clampedRebirth * REBIRTH_REQUIRED_LEVEL_STEP)
    }

    fun getRankMaxLevel(data: PlayerData): Int = rankMaxLevel

    fun migrateLegacyScrollBonuses(data: PlayerData) {
        if (data.upgradeScrollBonuses.isEmpty()) return

        data.lightningMaxLevel = (data.lightningMaxLevel + (data.upgradeScrollBonuses[UpgradeScrollType.LIGHTNING.id] ?: 0.0).toInt()).coerceAtMost(lightningMaxLevelWithScroll)
        data.virtualJackhammerMaxLevel = (data.virtualJackhammerMaxLevel + (data.upgradeScrollBonuses[UpgradeScrollType.VIRTUAL_JACKHAMMER.id] ?: 0.0).toInt()).coerceAtMost(virtualJackhammerMaxLevelWithScroll)
        data.xpGainMaxLevel = (data.xpGainMaxLevel + (data.upgradeScrollBonuses[UpgradeScrollType.XP_GAIN.id] ?: 0.0).toInt()).coerceAtMost(xpGainMaxLevelWithScroll)
        data.oreFrequencyMaxLevel = (data.oreFrequencyMaxLevel + (data.upgradeScrollBonuses[UpgradeScrollType.ORE_FREQUENCY.id] ?: 0.0).toInt()).coerceAtMost(oreFrequencyMaxLevelWithScroll)
        data.upgradeScrollBonuses.clear()
    }
}
