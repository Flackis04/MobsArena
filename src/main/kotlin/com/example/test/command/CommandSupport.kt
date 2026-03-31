package com.example.test

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val upgradeGuiCommandCooldowns: MutableMap<java.util.UUID, Long> = mutableMapOf()
private const val UPGRADE_GUI_COMMAND_COOLDOWN_MS = 1000L
const val REBIRTH_MULTIPLIER_PER_REBIRTH = 0.25
const val ASCENSION_MINE_WEIGHT_MULTIPLIER_PER_ASCENSION = 1.5
const val REBIRTH_PAYMENT_UNLOCK_SECONDS = 1L * 60L * 60L
private val rebirthRequirementMaterials = MineManager.valuables.dropWhile { it != org.bukkit.Material.RESPAWN_ANCHOR }
private const val MAX_REBIRTH = 26

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
    val nextRebirth = data.rebirth + 1

    if (!checkRebirthRequirements(player)) return false
    data.rebirth += 1
    resetPlayerData(data, isRebirth = true, preserveAutoMinerUpgrades = true)
    data.playtimeSecondsAtLastRebirth = data.playtimeSeconds
    data.paymentUnlockPlaytimeSeconds = data.playtimeSeconds + REBIRTH_PAYMENT_UNLOCK_SECONDS
    data.multiplier = getBasePlayerMultiplier(data)
    applyResetToOnlinePlayer(player, data, true)
    SessionTimelineManager.record(
        player,
        "Rebirthed to ${formatRebirthRequirement(nextRebirth)}"
    )
    TextUtil.showTitle(
        player,
        "&d&lRebirth Unlocked",
        "&7Your permanent multiplier increased by &b${TextUtil.formatNum(REBIRTH_MULTIPLIER_PER_REBIRTH)}x",
        10,
        70,
        20
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

fun performAscension(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)
    val nextAscension = data.ascension + 1

    if (!checkAscensionRequirements(player)) return false
    data.ascension = nextAscension
    data.rebirth = 0
    resetPlayerData(data, isRebirth = true, preserveAutoMinerUpgrades = false)
    data.playtimeSecondsAtLastRebirth = data.playtimeSeconds
    data.paymentUnlockPlaytimeSeconds = data.playtimeSeconds + REBIRTH_PAYMENT_UNLOCK_SECONDS
    data.multiplier = getBasePlayerMultiplier(data)
    applyResetToOnlinePlayer(player, data, true)
    SessionTimelineManager.record(player, "Ascended to ${formatAscensionLabel(nextAscension)}")
    TextUtil.showTitle(
        player,
        "&5&lAscension Unlocked",
        "&7Your rank prefix is now &d${formatDisplayedRank(data)}",
        10,
        70,
        20
    )
    player.sendMessage(TextUtil.colorize("&aAscended to &d${formatAscensionLabel(nextAscension)}&a."))
    player.playSound(player.location, "entity.player.levelup", 1f, 0.6f)
    ScoreboardManager.updateBoard(player)
    return true
}

fun checkRebirthRequirements(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)
    if (data.rebirth >= MAX_REBIRTH) {
        player.sendMessage(TextUtil.colorize("&cYou have already reached max rebirth &fZ&c."))
        return false
    }

    val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
    val missing = mutableListOf<String>()
    if (data.rank < LevelManager.getRankMaxLevel(data)) missing += "Rank"
    if (data.level < requiredLevel) missing += "Level $requiredLevel"
    val requiredValuable = getRequiredRebirthValuable(data.rebirth)
    if (requiredValuable != null && data.getCollectedAmount(requiredValuable) < 1L) {
        missing += stripRebirthRequirementDisplayName(getRequiredRebirthValuableDisplayName(data.rebirth))
    }

    if (missing.isNotEmpty()) {
        player.sendMessage(TextUtil.colorize("&cYou do not meet the requirements to rebirth!"))
        return false
    }
    return true
}

fun checkAscensionRequirements(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)
    val missing = mutableListOf<String>()
    if (data.rebirth < MAX_REBIRTH) missing += "Rebirth Z"
    if (data.rank < LevelManager.getRankMaxLevel(data)) missing += "Max Rank"

    if (missing.isNotEmpty()) {
        player.sendMessage(TextUtil.colorize("&cYou do not meet the requirements to ascend!"))
        return false
    }
    return true
}

fun getRequiredRebirthValuable(rebirth: Int): org.bukkit.Material? =
    rebirthRequirementMaterials.getOrNull(rebirth)

fun formatDisplayedRank(data: PlayerData, rank: Int = data.rank): String {
    val ascensionPrefix = if (data.ascension > 0) formatAscensionLabel(data.ascension) else ""
    val rebirthTier = formatRebirthRequirement(data.rebirth)
    return "$ascensionPrefix$rebirthTier$rank"
}

fun formatAscensionLabel(ascension: Int): String =
    if (ascension <= 0) "A" else spreadsheetLetters(ascension)

fun getRequiredRebirthValuableDisplayName(rebirth: Int): String {
    val material = getRequiredRebirthValuable(rebirth) ?: return "&7None"
    val valuableIndex = MineManager.valuables.indexOf(material)
    return if (valuableIndex in MineManager.valuableNames.indices) {
        MineManager.valuableNames[valuableIndex]
    } else {
        "&f${material.name.replace('_', ' ')}"
    }
}

private fun stripRebirthRequirementDisplayName(text: String): String =
    TextUtil.colorize(text).replace(Regex("(?i)[§&][0-9A-FK-ORX]"), "").replace("Â", "").trim()

private fun spreadsheetLetters(value: Int): String {
    var remaining = value.coerceAtLeast(1)
    val builder = StringBuilder()
    while (remaining > 0) {
        remaining--
        builder.append(('A'.code + (remaining % 26)).toChar())
        remaining /= 26
    }
    return builder.reverse().toString()
}

fun formatRebirthRequirement(rebirth: Int): String =
    if (rebirth < 0) rebirth.toString() else spreadsheetLetters(rebirth + 1)

fun sendPermissionMessage(sender: CommandSender) {
    sender.sendMessage(TextUtil.colorize("&cYou don't have permission to run this command."))
}

fun sendPurchaseCongratulations(player: Player, purchaseName: String, rewardText: String) {
    Bukkit.broadcast(TextUtil.toComponent("&b${player.name} &7purchased &a$purchaseName&7!"))
    Bukkit.getOnlinePlayers().forEach {
        TextUtil.showTitle(
            it,
            "&a&lCongratulations!",
            "&b${player.name} &7bought &a$purchaseName",
            10,
            60,
            10
        )
    }
}

fun resetPlayerData(
    data: PlayerData,
    isRebirth: Boolean = false,
    clearEarnedProgress: Boolean = false,
    preserveAutoMinerUpgrades: Boolean = isRebirth && !clearEarnedProgress
) {
    val startingUpgradeLevel = if (isRebirth) 1 + data.rebirth else 1
    val autoMinerFortuneLevel = data.autoMinerFortuneLevel
    val autoMinerEfficiencyLevel = data.autoMinerEfficiencyLevel
    val autoMinerEnergyDrinkLevel = data.autoMinerEnergyDrinkLevel
    val autoMinerBackpackLevel = data.autoMinerBackpackLevel
    val autoMinerLuckLevel = data.autoMinerLuckLevel
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
    data.backpackLevel = startingUpgradeLevel
    data.autoMinerFortuneLevel = if (preserveAutoMinerUpgrades) autoMinerFortuneLevel else 1
    data.autoMinerEfficiencyLevel = if (preserveAutoMinerUpgrades) autoMinerEfficiencyLevel else 1
    data.autoMinerEnergyDrinkLevel = if (preserveAutoMinerUpgrades) autoMinerEnergyDrinkLevel else 1
    data.autoMinerBackpackLevel = if (preserveAutoMinerUpgrades) autoMinerBackpackLevel else 1
    data.autoMinerLuckLevel = if (preserveAutoMinerUpgrades) autoMinerLuckLevel else 1
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
    data.backpackMaxLevel = LevelManager.backpackMaxLevel
    data.autoMinerFortuneMaxLevel = LevelManager.autoMinerFortuneMaxLevel
    data.autoMinerEfficiencyMaxLevel = LevelManager.autoMinerEfficiencyMaxLevel
    data.autoMinerEnergyDrinkMaxLevel = LevelManager.autoMinerEnergyDrinkMaxLevel
    data.autoMinerBackpackMaxLevel = LevelManager.autoMinerBackpackMaxLevel
    data.autoMinerLuckMaxLevel = LevelManager.autoMinerLuckMaxLevel
    data.balance = 0
    data.level = 0
    data.experienceBuffer = 0.0
    data.hasEnabledPvp = false
    if (!isRebirth) {
        data.playtimeSecondsAtLastRebirth = 0L
    }
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
        data.discordMultiplierBonus = 0.0
        data.donorRankMultiplier = 0.0
        data.mineWeightBonusMultiplier = 1.0
        data.extraExperienceMultiplier = 1.0
        data.hasLinkedDiscord = false
        data.hasDonorRank = false
        data.flightUnlocked = false
        data.killStreak = 0
        data.victims.clear()
        data.trustedMinePlayers.clear()
        data.flight = false
        data.mineCenterX = Int.MIN_VALUE
        data.mineCenterZ = Int.MIN_VALUE
        data.ascension = 0
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

fun applyResetToOnlinePlayer(player: Player, data: PlayerData, isRebirth: Boolean = false, clearEarnedProgress: Boolean = false) {
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
            else if (clearEarnedProgress) "&cYour stats, money, upgrades, rebirths, masteries, and scroll progress have been reset."
            else "&cYour money and upgrades have been reset."
        )
    )
}
