package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player

object DataIntegrityManager {
    private const val TICKS_PER_MINUTE = 20L * 60L
    private const val DISCORD_LINK_MULTIPLIER_BONUS = 0.25

    fun init() {
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable {
                Bukkit.getOnlinePlayers().forEach(::reconcilePlayer)
            },
            TICKS_PER_MINUTE,
            TICKS_PER_MINUTE
        )
    }

    fun reconcilePlayer(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val changed = reconcile(data)

        ExperienceManager.clamp(player)
        ExperienceManager.restoreStoredLevel(player)

        if (!changed) return

        if (KitManager.hasPickaxe(player)) {
            KitManager.refreshPickaxe(player)
        }
        ScoreboardManager.updateBoard(player)
    }

    fun reconcile(data: PlayerData): Boolean {
        var changed = false

        LevelManager.checkLevels(data)

        val expectedDiscordBonus = if (data.hasLinkedDiscord) DISCORD_LINK_MULTIPLIER_BONUS else 0.0
        if (data.discordMultiplierBonus != expectedDiscordBonus) {
            data.discordMultiplierBonus = expectedDiscordBonus
            changed = true
        }

        if (!data.hasDonorRank && data.donorRankMultiplier != 0.0) {
            data.donorRankMultiplier = 0.0
            changed = true
        }

        val maxRank = LevelManager.getRankMaxLevel(data)
        val correctedRank = data.rank.coerceIn(1, maxRank)
        if (data.rank != correctedRank) {
            data.rank = correctedRank
            changed = true
        }

        val startingUpgradeLevel = (1 + data.rebirth).coerceAtLeast(1)
        changed = ensureStartingUpgradeFloor(data, startingUpgradeLevel) || changed

        val expectedMultiplier = getExpectedTotalMultiplier(data)
        if (kotlin.math.abs(data.multiplier - expectedMultiplier) > 0.0001) {
            data.multiplier = expectedMultiplier
            changed = true
        }

        return changed
    }

    private fun ensureStartingUpgradeFloor(data: PlayerData, startingUpgradeLevel: Int): Boolean {
        var changed = false

        fun correct(current: Int, max: Int): Int = current.coerceIn(startingUpgradeLevel, max)

        val correctedMultiBreak = correct(data.multiBreakLevel, data.multiBreakMaxLevel)
        if (data.multiBreakLevel != correctedMultiBreak) {
            data.multiBreakLevel = correctedMultiBreak
            changed = true
        }

        val correctedFortune = correct(data.fortuneLevel, data.fortuneMaxLevel)
        if (data.fortuneLevel != correctedFortune) {
            data.fortuneLevel = correctedFortune
            changed = true
        }

        val correctedOreBoost = correct(data.oreBoostLevel, data.oreBoostMaxLevel)
        if (data.oreBoostLevel != correctedOreBoost) {
            data.oreBoostLevel = correctedOreBoost
            changed = true
        }

        val correctedExcavator = correct(data.excavatorLevel, data.excavatorMaxLevel)
        if (data.excavatorLevel != correctedExcavator) {
            data.excavatorLevel = correctedExcavator
            changed = true
        }

        val correctedLightning = correct(data.lightningLevel, data.lightningMaxLevel)
        if (data.lightningLevel != correctedLightning) {
            data.lightningLevel = correctedLightning
            changed = true
        }

        val correctedJackhammer = correct(data.virtualJackhammerLevel, data.virtualJackhammerMaxLevel)
        if (data.virtualJackhammerLevel != correctedJackhammer) {
            data.virtualJackhammerLevel = correctedJackhammer
            changed = true
        }

        val correctedExcavatorEfficiency = correct(data.excavatorEfficiencyLevel, data.excavatorEfficiencyMaxLevel)
        if (data.excavatorEfficiencyLevel != correctedExcavatorEfficiency) {
            data.excavatorEfficiencyLevel = correctedExcavatorEfficiency
            changed = true
        }

        val correctedXpGain = correct(data.xpGainLevel, data.xpGainMaxLevel)
        if (data.xpGainLevel != correctedXpGain) {
            data.xpGainLevel = correctedXpGain
            changed = true
        }

        val correctedOreFrequency = correct(data.oreFrequencyLevel, data.oreFrequencyMaxLevel)
        if (data.oreFrequencyLevel != correctedOreFrequency) {
            data.oreFrequencyLevel = correctedOreFrequency
            changed = true
        }

        val correctedScrollFinder = correct(data.scrollFinderLevel, data.scrollFinderMaxLevel)
        if (data.scrollFinderLevel != correctedScrollFinder) {
            data.scrollFinderLevel = correctedScrollFinder
            changed = true
        }

        return changed
    }

    private fun getExpectedTotalMultiplier(data: PlayerData): Double {
        val rankBonus = KitManager.getRankMultiplier(data.rank)
        return 1.0 +
            (data.rebirth * REBIRTH_MULTIPLIER_PER_REBIRTH) +
            rankBonus +
            data.discordMultiplierBonus +
            data.donorRankMultiplier
    }
}
