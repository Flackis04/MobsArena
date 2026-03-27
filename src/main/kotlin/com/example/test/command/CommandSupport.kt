package com.example.test

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val upgradeGuiCommandCooldowns: MutableMap<java.util.UUID, Long> = mutableMapOf()
private const val UPGRADE_GUI_COMMAND_COOLDOWN_MS = 1000L
const val REBIRTH_MULTIPLIER_PER_REBIRTH = 0.25

fun tryOpenPermUpgradeGui(player: Player): Boolean {
    val now = System.currentTimeMillis()
    val lastOpenedAt = upgradeGuiCommandCooldowns[player.uniqueId] ?: 0L
    if (now - lastOpenedAt < UPGRADE_GUI_COMMAND_COOLDOWN_MS) {
        return false
    }

    upgradeGuiCommandCooldowns[player.uniqueId] = now
    openPermUpgradeGui(player)
    return true
}

fun performRebirth(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)

    if (!checkRebirthRequirements(player)) return false
    data.rebirth += 1
    resetPlayerData(data, true)
    data.multiplier = getBasePlayerMultiplier(data)
    applyResetToOnlinePlayer(player, data, true)
    player.sendTitle(
        TextUtil.colorize("&d&lRebirth Unlocked"),
        TextUtil.colorize("&7Your permanent multiplier increased by &b${TextUtil.formatNum(REBIRTH_MULTIPLIER_PER_REBIRTH)}x"),
        10, 70, 20
    )
    player.sendMessage(
        TextUtil.colorize(
            "&aRebirth unlocked. Permanent multiplier increased by &b+${TextUtil.formatNum(REBIRTH_MULTIPLIER_PER_REBIRTH)}x&a."
        )
    )
    player.playSound(player.location, "entity.player.levelup", 1f, 0.8f)
    ScoreboardManager.updateBoard(player)
    return true
}

fun checkRebirthRequirements(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)
    if (data.rebirth >= 26) {
        player.sendMessage(TextUtil.colorize("&cYou have already reached max rebirth &fZ&c."))
        return false
    }

    val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
    val missing = mutableListOf<String>()
    if (data.rank < LevelManager.getRankMaxLevel(data)) missing += "Rank"
    if (data.level < requiredLevel) missing += "Level $requiredLevel"

    if (missing.isNotEmpty()) {
        player.sendMessage(TextUtil.colorize("&cYou do not meet the requirements to rebirth!"))
        return false
    }
    return true
}

fun sendPermissionMessage(sender: CommandSender) {
    sender.sendMessage(TextUtil.colorize("&cYou don't have permission to run this command."))
}

fun sendPurchaseCongratulations(player: Player, purchaseName: String, rewardText: String) {
    Bukkit.broadcast(TextUtil.toComponent("&b${player.name} &7purchased &a$purchaseName&7!"))
    Bukkit.getOnlinePlayers().forEach {
        it.sendTitle(
            TextUtil.colorize("&a&lCongratulations!"),
            TextUtil.colorize("&b${player.name} &7bought &a$purchaseName"),
            10,
            60,
            10
        )
    }
}

fun resetPlayerData(data: PlayerData, isRebirth: Boolean = false, clearEarnedProgress: Boolean = false) {
    val startingUpgradeLevel = if (isRebirth) 1 + data.rebirth else 1
    val multiBreakAddedLevels = (data.multiBreakMaxLevel - LevelManager.multiBreakMaxLevel).coerceAtLeast(0)
    val fortuneAddedLevels = (data.fortuneMaxLevel - LevelManager.fortuneMaxLevel).coerceAtLeast(0)
    val oreBoostAddedLevels = (data.oreBoostMaxLevel - LevelManager.oreBoostMaxLevel).coerceAtLeast(0)
    val excavatorAddedLevels = (data.excavatorMaxLevel - LevelManager.excavatorMaxLevel).coerceAtLeast(0)
    val lightningAddedLevels = (data.lightningMaxLevel - LevelManager.lightningMaxLevel).coerceAtLeast(0)
    val virtualJackhammerAddedLevels = (data.virtualJackhammerMaxLevel - LevelManager.virtualJackhammerMaxLevel).coerceAtLeast(0)
    val excavatorEfficiencyAddedLevels = (data.excavatorEfficiencyMaxLevel - LevelManager.excavatorEfficiencyMaxLevel).coerceAtLeast(0)
    val xpGainAddedLevels = (data.xpGainMaxLevel - LevelManager.xpGainMaxLevel).coerceAtLeast(0)
    val oreFrequencyAddedLevels = (data.oreFrequencyMaxLevel - LevelManager.oreFrequencyMaxLevel).coerceAtLeast(0)
    val scrollFinderAddedLevels = (data.scrollFinderMaxLevel - LevelManager.scrollFinderMaxLevel).coerceAtLeast(0)

    data.hasReceivedJoinLoadout = false
    data.multiplier = getBasePlayerMultiplier(data)
    data.rank = 1
    data.multiBreakLevel = startingUpgradeLevel
    data.fortuneLevel = startingUpgradeLevel
    data.oreBoostLevel = startingUpgradeLevel
    data.excavatorLevel = startingUpgradeLevel
    data.lightningLevel = startingUpgradeLevel
    data.virtualJackhammerLevel = startingUpgradeLevel
    data.excavatorEfficiencyLevel = startingUpgradeLevel
    data.xpGainLevel = startingUpgradeLevel
    data.oreFrequencyLevel = startingUpgradeLevel
    data.scrollFinderLevel = startingUpgradeLevel
    data.autoMinerFortuneLevel = 1
    data.autoMinerEfficiencyLevel = 1
    data.autoMinerEnergyDrinkLevel = 1
    data.autoMinerBackpackLevel = 1
    data.autoMinerLuckLevel = 1
    data.multiBreakMaxLevel = (LevelManager.multiBreakMaxLevel + if (clearEarnedProgress) 0 else multiBreakAddedLevels).coerceAtMost(LevelManager.multiBreakMaxLevelWithScroll)
    data.fortuneMaxLevel = (LevelManager.fortuneMaxLevel + if (clearEarnedProgress) 0 else fortuneAddedLevels).coerceAtMost(LevelManager.fortuneMaxLevelWithScroll)
    data.oreBoostMaxLevel = (LevelManager.oreBoostMaxLevel + if (clearEarnedProgress) 0 else oreBoostAddedLevels).coerceAtMost(LevelManager.oreBoostMaxLevelWithScroll)
    data.excavatorMaxLevel = (LevelManager.excavatorMaxLevel + if (clearEarnedProgress) 0 else excavatorAddedLevels).coerceAtMost(LevelManager.excavatorMaxLevelWithScroll)
    data.lightningMaxLevel = (LevelManager.lightningMaxLevel + if (clearEarnedProgress) 0 else lightningAddedLevels).coerceAtMost(LevelManager.lightningMaxLevelWithScroll)
    data.virtualJackhammerMaxLevel = (LevelManager.virtualJackhammerMaxLevel + if (clearEarnedProgress) 0 else virtualJackhammerAddedLevels).coerceAtMost(LevelManager.virtualJackhammerMaxLevelWithScroll)
    data.excavatorEfficiencyMaxLevel = (LevelManager.excavatorEfficiencyMaxLevel + if (clearEarnedProgress) 0 else excavatorEfficiencyAddedLevels).coerceAtMost(LevelManager.excavatorEfficiencyMaxLevelWithScroll)
    data.xpGainMaxLevel = (LevelManager.xpGainMaxLevel + if (clearEarnedProgress) 0 else xpGainAddedLevels).coerceAtMost(LevelManager.xpGainMaxLevelWithScroll)
    data.oreFrequencyMaxLevel = (LevelManager.oreFrequencyMaxLevel + if (clearEarnedProgress) 0 else oreFrequencyAddedLevels).coerceAtMost(LevelManager.oreFrequencyMaxLevelWithScroll)
    data.scrollFinderMaxLevel = (LevelManager.scrollFinderMaxLevel + if (clearEarnedProgress) 0 else scrollFinderAddedLevels).coerceAtMost(LevelManager.scrollFinderMaxLevelWithScroll)
    data.autoMinerFortuneMaxLevel = LevelManager.autoMinerFortuneMaxLevel
    data.autoMinerEfficiencyMaxLevel = LevelManager.autoMinerEfficiencyMaxLevel
    data.autoMinerEnergyDrinkMaxLevel = LevelManager.autoMinerEnergyDrinkMaxLevel
    data.autoMinerBackpackMaxLevel = LevelManager.autoMinerBackpackMaxLevel
    data.autoMinerLuckMaxLevel = LevelManager.autoMinerLuckMaxLevel
    data.balance = 0
    data.level = 0
    data.experienceBuffer = 0.0
    data.hasEnabledPvp = false
    data.storageContents.clear()
    data.deathStorageContents.clear()
    data.autoMinerStorageContents.clear()
    data.mineWeightBonusMultiplier = if (data.hasDonorRank) data.mineWeightBonusMultiplier else 1.0
    data.extraExperienceMultiplier = if (data.hasDonorRank) data.extraExperienceMultiplier else 1.0

    if (isRebirth) {
        return
    }

    data.newPlayer = null
    data.hasTouched = false
    data.hasBroken = false
    data.hasClosed = false
    data.hasSold = false
    data.valuableBlocksBroken = 0
    data.hasSeenBackpackSellHint = false
    data.hasSeenUpgradeHint = false
    if (clearEarnedProgress) {
        data.upgradeScrollBonuses.clear()
        data.multiBreakScrollBonus = 0.0
        data.masteryLevels.clear()
        data.masteryActivations.clear()
        data.valuableCollected.clear()
    }
    data.rebirth = 0
    data.animationsEnabled = true
    data.animationLinearMode = true
    data.animationExtraBlockDelayTicks = 0L
    data.animationDurationTicks = 8
    data.multiplier = getBasePlayerMultiplier(data)
}

private fun getBasePlayerMultiplier(data: PlayerData): Double =
    1.0 + (data.rebirth * REBIRTH_MULTIPLIER_PER_REBIRTH) + data.discordMultiplierBonus + data.donorRankMultiplier

fun applyResetToOnlinePlayer(player: Player, data: PlayerData, isRebirth: Boolean = false) {
    player.totalExperience = 0
    player.level = 0
    player.exp = 0f
    data.level = 0
    player.inventory.clear()
    if (!isRebirth) {
        player.enderChest.clear()
    }
    KitManager.giveStarterSpawnLoadout(player)
    ScoreboardManager.updateBoard(player)
    player.sendMessage(
        TextUtil.colorize(
            if (isRebirth) "&dYour inventory, upgrades, rank, level, storage, and coins have been reset."
            else "&cYour money and upgrades have been reset."
        )
    )
}
