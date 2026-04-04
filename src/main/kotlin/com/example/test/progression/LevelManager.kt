package com.example.test

import kotlin.math.pow
import kotlin.math.round

object LevelManager {
    const val MAX_REBIRTH_LEVEL = 25
    private const val BASE_REBIRTH_REQUIRED_LEVEL = 250
    private const val REBIRTH_REQUIRED_LEVEL_LINEAR_STEP = 35
    private const val REBIRTH_REQUIRED_LEVEL_QUADRATIC_STEP = 4
    private const val BASE_ASCENSION_REQUIRED_LEVEL = 1_500
    private const val ASCENSION_REQUIRED_LEVEL_LINEAR_STEP = 250
    private const val ASCENSION_REQUIRED_LEVEL_QUADRATIC_STEP = 35

    const val multiBreakMaxAddedLevels = 15
    const val fortuneMaxAddedLevels = 10
    const val oreBoostMaxAddedLevels = 12
    const val excavatorMaxAddedLevels = 15
    const val lightningMaxAddedLevels = 125
    const val virtualJackhammerMaxAddedLevels = 125
    const val excavatorEfficiencyMaxAddedLevels = 5
    const val xpGainMaxAddedLevels = 125
    const val oreFrequencyMaxAddedLevels = 125
    const val scrollFinderMaxAddedLevels = 10
    const val sellMultiplierMaxAddedLevels = 10
    const val tokenFinderMaxAddedLevels = 10
    const val keyFinderMaxAddedLevels = 10
    const val jackpotMaxAddedLevels = 8
    const val comboMaxAddedLevels = 12
    const val procPowerMaxAddedLevels = 8

    var rankMaxLevel = 26
    var multiBreakMaxLevel = 75
    var multiBreakMaxLevelWithScroll = multiBreakMaxLevel + multiBreakMaxAddedLevels
    var fortuneMaxLevel = 50
    var fortuneMaxLevelWithScroll = fortuneMaxLevel + fortuneMaxAddedLevels
    var oreBoostMaxLevel = 75
    var oreBoostMaxLevelWithScroll = oreBoostMaxLevel + oreBoostMaxAddedLevels
    var excavatorMaxLevel = 100
    var excavatorMaxLevelWithScroll = excavatorMaxLevel + excavatorMaxAddedLevels
    var lightningMaxLevel = 75
    var lightningMaxLevelWithScroll = lightningMaxLevel + lightningMaxAddedLevels
    var virtualJackhammerMaxLevel = 250
    var virtualJackhammerMaxLevelWithScroll = virtualJackhammerMaxLevel + virtualJackhammerMaxAddedLevels
    var excavatorEfficiencyMaxLevel = 25
    var excavatorEfficiencyMaxLevelWithScroll = excavatorEfficiencyMaxLevel + excavatorEfficiencyMaxAddedLevels
    var xpGainMaxLevel = 250
    var xpGainMaxLevelWithScroll = xpGainMaxLevel + xpGainMaxAddedLevels
    var oreFrequencyMaxLevel = 50
    var oreFrequencyMaxLevelWithScroll = oreFrequencyMaxLevel + oreFrequencyMaxAddedLevels
    var scrollFinderMaxLevel = 100
    var scrollFinderMaxLevelWithScroll = scrollFinderMaxLevel + scrollFinderMaxAddedLevels
    var backpackMaxLevel = 250
    var backpackMaxLevelWithScroll = backpackMaxLevel
    var sellMultiplierMaxLevel = 75
    var sellMultiplierMaxLevelWithScroll = sellMultiplierMaxLevel + sellMultiplierMaxAddedLevels
    var tokenFinderMaxLevel = 75
    var tokenFinderMaxLevelWithScroll = tokenFinderMaxLevel + tokenFinderMaxAddedLevels
    var keyFinderMaxLevel = 75
    var keyFinderMaxLevelWithScroll = keyFinderMaxLevel + keyFinderMaxAddedLevels
    var jackpotMaxLevel = 60
    var jackpotMaxLevelWithScroll = jackpotMaxLevel + jackpotMaxAddedLevels
    var comboMaxLevel = 75
    var comboMaxLevelWithScroll = comboMaxLevel + comboMaxAddedLevels
    var procPowerMaxLevel = 50
    var procPowerMaxLevelWithScroll = procPowerMaxLevel + procPowerMaxAddedLevels

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
    var autoMinerPayoutMaxLevel = 50
    var autoMinerPayoutMaxLevelWithScroll = autoMinerPayoutMaxLevel
    var swordMaxLevel = 25
    var swordMaxLevelWithScroll = swordMaxLevel

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
    val upgradeSellMultiplierCosts = mutableMapOf<Int, Long>()
    val upgradeTokenFinderCosts = mutableMapOf<Int, Long>()
    val upgradeKeyFinderCosts = mutableMapOf<Int, Long>()
    val upgradeJackpotCosts = mutableMapOf<Int, Long>()
    val upgradeComboCosts = mutableMapOf<Int, Long>()
    val upgradeProcPowerCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerFortuneCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerEfficiencyCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerEnergyDrinkCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerBackpackCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerLuckCosts = mutableMapOf<Int, Long>()
    val upgradeAutoMinerPayoutCosts = mutableMapOf<Int, Long>()
    val upgradeSwordCosts = mutableMapOf<Int, Long>()


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
        upgradeSellMultiplierCosts.clear()
        upgradeTokenFinderCosts.clear()
        upgradeKeyFinderCosts.clear()
        upgradeJackpotCosts.clear()
        upgradeComboCosts.clear()
        upgradeProcPowerCosts.clear()
        upgradeAutoMinerFortuneCosts.clear()
        upgradeAutoMinerEfficiencyCosts.clear()
        upgradeAutoMinerEnergyDrinkCosts.clear()
        upgradeAutoMinerBackpackCosts.clear()
        upgradeAutoMinerLuckCosts.clear()
        upgradeAutoMinerPayoutCosts.clear()
        upgradeSwordCosts.clear()


        generateCostTable(upgradeRankCosts, 250, 260.0, 1.58, rankMaxLevel + MAX_REBIRTH_LEVEL)
        generateCostTable(upgradeMultiBreakCosts, 3, 20.0, 1.4, multiBreakMaxLevelWithScroll)
        generateCostTable(upgradeOreBoostCosts, 12, 32.0, 1.65, oreBoostMaxLevelWithScroll)
        generateCostTable(upgradeFortuneCosts, 60, 150.0, 1.85, fortuneMaxLevelWithScroll + MAX_REBIRTH_LEVEL)
        generateCostTable(upgradeExcavatorCosts, 30, 38.0, 1.9, excavatorMaxLevelWithScroll)
        generateCostTable(upgradeLightningCosts, 1_000, 3_000.0, 1.95, lightningMaxLevelWithScroll)
        generateCostTable(upgradeVirtualJackhammerCosts, 4_000, 10_000.0, 2.1, virtualJackhammerMaxLevelWithScroll)
        generateCostTable(upgradeExcavatorEfficiencyCosts, 450, 22_000.0, 2.1, excavatorEfficiencyMaxLevelWithScroll)
        generateCostTable(upgradeXpGainCosts, 10, 20.0, 1.3, xpGainMaxLevelWithScroll)
        generateCostTable(upgradeOreFrequencyCosts, 1_000, 2_500.0, 1.9, oreFrequencyMaxLevelWithScroll)
        generateCostTable(upgradeScrollFinderCosts, 2_000, 5_000.0, 2.0, scrollFinderMaxLevelWithScroll)
        generateCostTable(upgradeBackpackCosts, 300, 1_000.0, 1.7, backpackMaxLevelWithScroll)
        generateCostTable(upgradeSellMultiplierCosts, 150, 320.0, 1.75, sellMultiplierMaxLevelWithScroll)
        generateCostTable(upgradeTokenFinderCosts, 220, 380.0, 1.85, tokenFinderMaxLevelWithScroll)
        generateCostTable(upgradeKeyFinderCosts, 1_000, 2_500.0, 1.95, keyFinderMaxLevelWithScroll)
        generateCostTable(upgradeJackpotCosts, 350, 900.0, 1.95, jackpotMaxLevelWithScroll)
        generateCostTable(upgradeComboCosts, 250, 520.0, 1.8, comboMaxLevelWithScroll)
        generateCostTable(upgradeProcPowerCosts, 600, 1_600.0, 2.0, procPowerMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerFortuneCosts, 1_000, 3_000.0, 2.2, autoMinerFortuneMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerEfficiencyCosts, 1_500, 2_500.0, 2.0, autoMinerEfficiencyMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerEnergyDrinkCosts, 2_000, 3_500.0, 2.1, autoMinerEnergyDrinkMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerBackpackCosts, 700, 2_000.0, 1.58, autoMinerBackpackMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerLuckCosts, 900, 2_200.0, 1.75, autoMinerLuckMaxLevelWithScroll)
        generateCostTable(upgradeAutoMinerPayoutCosts, 50_000, 35_000.0, 2.45, autoMinerPayoutMaxLevelWithScroll)
        generateCostTable(upgradeSwordCosts, 150, 240.0, 1.92, swordMaxLevelWithScroll)
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
        if (data.sellMultiplierMaxLevel <= 0) data.sellMultiplierMaxLevel = sellMultiplierMaxLevel
        if (data.tokenFinderMaxLevel <= 0) data.tokenFinderMaxLevel = tokenFinderMaxLevel
        if (data.keyFinderMaxLevel <= 0) data.keyFinderMaxLevel = keyFinderMaxLevel
        if (data.jackpotMaxLevel <= 0) data.jackpotMaxLevel = jackpotMaxLevel
        if (data.comboMaxLevel <= 0) data.comboMaxLevel = comboMaxLevel
        if (data.procPowerMaxLevel <= 0) data.procPowerMaxLevel = procPowerMaxLevel
        if (data.autoMinerFortuneMaxLevel <= 0) data.autoMinerFortuneMaxLevel = autoMinerFortuneMaxLevel
        if (data.autoMinerEfficiencyMaxLevel <= 0) data.autoMinerEfficiencyMaxLevel = autoMinerEfficiencyMaxLevel
        if (data.autoMinerEnergyDrinkMaxLevel <= 0) data.autoMinerEnergyDrinkMaxLevel = autoMinerEnergyDrinkMaxLevel
        if (data.autoMinerBackpackMaxLevel <= 0) data.autoMinerBackpackMaxLevel = autoMinerBackpackMaxLevel
        if (data.autoMinerLuckMaxLevel <= 0) data.autoMinerLuckMaxLevel = autoMinerLuckMaxLevel
        if (data.autoMinerPayoutMaxLevel <= 0) data.autoMinerPayoutMaxLevel = autoMinerPayoutMaxLevel
        if (data.swordMaxLevel <= 0) data.swordMaxLevel = swordMaxLevel

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
        data.sellMultiplierMaxLevel = data.sellMultiplierMaxLevel.coerceIn(sellMultiplierMaxLevel, sellMultiplierMaxLevelWithScroll)
        data.tokenFinderMaxLevel = data.tokenFinderMaxLevel.coerceIn(tokenFinderMaxLevel, tokenFinderMaxLevelWithScroll)
        data.keyFinderMaxLevel = data.keyFinderMaxLevel.coerceIn(keyFinderMaxLevel, keyFinderMaxLevelWithScroll)
        data.jackpotMaxLevel = data.jackpotMaxLevel.coerceIn(jackpotMaxLevel, jackpotMaxLevelWithScroll)
        data.comboMaxLevel = data.comboMaxLevel.coerceIn(comboMaxLevel, comboMaxLevelWithScroll)
        data.procPowerMaxLevel = data.procPowerMaxLevel.coerceIn(procPowerMaxLevel, procPowerMaxLevelWithScroll)
        data.autoMinerFortuneMaxLevel = data.autoMinerFortuneMaxLevel.coerceIn(autoMinerFortuneMaxLevel, autoMinerFortuneMaxLevelWithScroll)
        data.autoMinerEfficiencyMaxLevel = data.autoMinerEfficiencyMaxLevel.coerceIn(autoMinerEfficiencyMaxLevel, autoMinerEfficiencyMaxLevelWithScroll)
        data.autoMinerEnergyDrinkMaxLevel = data.autoMinerEnergyDrinkMaxLevel.coerceIn(autoMinerEnergyDrinkMaxLevel, autoMinerEnergyDrinkMaxLevelWithScroll)
        data.autoMinerBackpackMaxLevel = data.autoMinerBackpackMaxLevel.coerceIn(autoMinerBackpackMaxLevel, autoMinerBackpackMaxLevelWithScroll)
        data.autoMinerLuckMaxLevel = data.autoMinerLuckMaxLevel.coerceIn(autoMinerLuckMaxLevel, autoMinerLuckMaxLevelWithScroll)
        data.autoMinerPayoutMaxLevel = data.autoMinerPayoutMaxLevel.coerceIn(autoMinerPayoutMaxLevel, autoMinerPayoutMaxLevelWithScroll)
        data.swordMaxLevel = data.swordMaxLevel.coerceIn(swordMaxLevel, swordMaxLevelWithScroll)

        if (data.multiBreakLevel <= 0) data.multiBreakLevel = 1
        if (data.fortuneLevel <= 0) data.fortuneLevel = 1
        if (data.oreBoostLevel <= 0) data.oreBoostLevel = 1
        if (data.lightningLevel <= 0) data.lightningLevel = 1
        if (data.virtualJackhammerLevel <= 0) data.virtualJackhammerLevel = 1
        if (data.xpGainLevel <= 0) data.xpGainLevel = 1
        if (data.oreFrequencyLevel <= 0) data.oreFrequencyLevel = 1
        if (data.scrollFinderLevel <= 0) data.scrollFinderLevel = 1
        if (data.backpackLevel <= 0) data.backpackLevel = 1
        if (data.sellMultiplierLevel <= 0) data.sellMultiplierLevel = 1
        if (data.tokenFinderLevel <= 0) data.tokenFinderLevel = 1
        if (data.keyFinderLevel <= 0) data.keyFinderLevel = 1
        if (data.jackpotLevel <= 0) data.jackpotLevel = 1
        if (data.comboLevel <= 0) data.comboLevel = 1
        if (data.procPowerLevel <= 0) data.procPowerLevel = 1
        if (data.autoMinerFortuneLevel <= 0) data.autoMinerFortuneLevel = 1
        if (data.autoMinerEfficiencyLevel <= 0) data.autoMinerEfficiencyLevel = 1
        if (data.autoMinerEnergyDrinkLevel <= 0) data.autoMinerEnergyDrinkLevel = 1
        if (data.autoMinerBackpackLevel <= 0) data.autoMinerBackpackLevel = 1
        if (data.autoMinerLuckLevel <= 0) data.autoMinerLuckLevel = 1
        if (data.autoMinerPayoutLevel <= 0) data.autoMinerPayoutLevel = 1
        if (data.swordLevel <= 0) data.swordLevel = 1

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
        data.sellMultiplierLevel = data.sellMultiplierLevel.coerceAtMost(data.sellMultiplierMaxLevel)
        data.tokenFinderLevel = data.tokenFinderLevel.coerceAtMost(data.tokenFinderMaxLevel)
        data.keyFinderLevel = data.keyFinderLevel.coerceAtMost(data.keyFinderMaxLevel)
        data.jackpotLevel = data.jackpotLevel.coerceAtMost(data.jackpotMaxLevel)
        data.comboLevel = data.comboLevel.coerceAtMost(data.comboMaxLevel)
        data.procPowerLevel = data.procPowerLevel.coerceAtMost(data.procPowerMaxLevel)
        data.autoMinerFortuneLevel = data.autoMinerFortuneLevel.coerceAtMost(data.autoMinerFortuneMaxLevel)
        data.autoMinerEfficiencyLevel = data.autoMinerEfficiencyLevel.coerceAtMost(data.autoMinerEfficiencyMaxLevel)
        data.autoMinerEnergyDrinkLevel = data.autoMinerEnergyDrinkLevel.coerceAtMost(data.autoMinerEnergyDrinkMaxLevel)
        data.autoMinerBackpackLevel = data.autoMinerBackpackLevel.coerceAtMost(data.autoMinerBackpackMaxLevel)
        data.autoMinerLuckLevel = data.autoMinerLuckLevel.coerceAtMost(data.autoMinerLuckMaxLevel)
        data.autoMinerPayoutLevel = data.autoMinerPayoutLevel.coerceAtMost(data.autoMinerPayoutMaxLevel)
        data.swordLevel = data.swordLevel.coerceAtMost(data.swordMaxLevel)
    }

    fun getRequiredLevelForNextRebirth(currentRebirth: Int): Int {
        val clampedRebirth = currentRebirth.coerceIn(0, MAX_REBIRTH_LEVEL)
        return BASE_REBIRTH_REQUIRED_LEVEL +
            (clampedRebirth * REBIRTH_REQUIRED_LEVEL_LINEAR_STEP) +
            (clampedRebirth * clampedRebirth * REBIRTH_REQUIRED_LEVEL_QUADRATIC_STEP)
    }

    fun getRequiredLevelForNextAscension(currentAscension: Int): Int {
        val clampedAscension = currentAscension.coerceAtLeast(0)
        return BASE_ASCENSION_REQUIRED_LEVEL +
            (clampedAscension * ASCENSION_REQUIRED_LEVEL_LINEAR_STEP) +
            (clampedAscension * clampedAscension * ASCENSION_REQUIRED_LEVEL_QUADRATIC_STEP)
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
