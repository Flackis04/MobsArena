package com.example.test

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.pow

private val upgradeGuiCommandCooldowns: MutableMap<java.util.UUID, Long> = mutableMapOf()
private const val UPGRADE_GUI_COMMAND_COOLDOWN_MS = 1000L
const val REBIRTH_MULTIPLIER_PER_REBIRTH = 0.25
const val ASCENSION_MINE_WEIGHT_MULTIPLIER_PER_ASCENSION = 1.12
const val ASCENSION_RANKUP_COST_MULTIPLIER_PER_ASCENSION = 1.5
const val ASCENSION_XP_COST_MULTIPLIER_PER_ASCENSION = 1.35
const val REBIRTH_PAYMENT_UNLOCK_SECONDS = 1L * 60L * 60L
private const val MAX_REBIRTH = LevelManager.MAX_REBIRTH_LEVEL

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
    resetScrollProgress(data)
    data.playtimeSecondsAtLastRebirth = data.playtimeSeconds
    data.paymentUnlockPlaytimeSeconds = data.playtimeSeconds + REBIRTH_PAYMENT_UNLOCK_SECONDS
    data.multiplier = getBasePlayerMultiplier(data)
    applyResetToOnlinePlayer(player, data, true, resetScrollProgress = true)
    SessionTimelineManager.record(player, "Ascended to ${formatAscensionLabel(nextAscension)}")
    TextUtil.showTitle(
        player,
        "&5&lAscension Unlocked",
        "&7Your rank prefix is now &d${formatDisplayedRank(data)}",
        10,
        70,
        20
    )
    player.sendMessage(TextUtil.colorize("&aAscended to &d${formatAscensionLabel(nextAscension)}&a. &cYour scroll progress was reset."))
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

    if (missing.isNotEmpty()) {
        player.sendMessage(TextUtil.colorize("&cYou do not meet the requirements to rebirth: &f${missing.joinToString("&7, &f")}"))
        return false
    }
    return true
}

fun checkAscensionRequirements(player: Player): Boolean {
    val data = DataStore.get(player.uniqueId)
    val missing = mutableListOf<String>()
    val requiredLevel = LevelManager.getRequiredLevelForNextAscension(data.ascension)
    if (data.rebirth < MAX_REBIRTH) missing += "Rebirth Z"
    if (data.rank < LevelManager.getRankMaxLevel(data)) missing += "Max Rank"
    if (data.level < requiredLevel) missing += "Level $requiredLevel"

    if (missing.isNotEmpty()) {
        player.sendMessage(TextUtil.colorize("&cYou do not meet the requirements to ascend: &f${missing.joinToString("&7, &f")}"))
        return false
    }
    return true
}

fun formatDisplayedRank(data: PlayerData, rank: Int = data.rank): String {
    val parts = mutableListOf<String>()
    if (data.ascension > 0) {
        parts += formatAscensionLabel(data.ascension)
    }
    if (data.ascension > 0 || data.rebirth > 0) {
        parts += formatDisplayIndexedLabel(data.rebirth, LevelManager.MAX_REBIRTH_LEVEL)
    }
    parts += formatDisplayIndexedLabel(rank, LevelManager.getRankMaxLevel(data))
    return parts.joinToString("")
}

fun formatStyledRank(data: PlayerData, rank: Int = data.rank): String {
    val progressionScore = (data.ascension * 12) + (data.rebirth * 2) + rank
    val frameColor = when {
        progressionScore >= 220 -> "<#FFF3B0>"
        progressionScore >= 140 -> "<#F7A8FF>"
        progressionScore >= 70 -> "<#7EE7FF>"
        else -> "<#9A9A9A>"
    }
    val dividerColor = when {
        progressionScore >= 220 -> "<#FFCF66>"
        progressionScore >= 140 -> "<#FF8AD8>"
        progressionScore >= 70 -> "<#68D7FF>"
        else -> "<#7A7A7A>"
    }
    val ascensionColor = when {
        data.ascension >= 10 -> "<#FFF6CC>"
        data.ascension >= 5 -> "<#FFB86C>"
        data.ascension >= 1 -> "<#FF8AE2>"
        else -> "<#FFFFFF>"
    }
    val rebirthColor = when {
        data.rebirth >= 20 -> "<#FFD166>"
        data.rebirth >= 12 -> "<#7FDBFF>"
        data.rebirth >= 6 -> "<#82F7C5>"
        else -> "<#B7B7B7>"
    }
    val rankColor = when {
        data.ascension >= 8 -> "<#FFF2A6>"
        data.rebirth >= 18 -> "<#FFA8F0>"
        else -> TierManager.getTier(rank)?.color ?: "&f"
    }

    val parts = mutableListOf<String>()
    if (data.ascension > 0) {
        parts += "$ascensionColor&l✦${formatAscensionLabel(data.ascension)}"
    }
    if (data.ascension > 0 || data.rebirth > 0) {
        parts += "$rebirthColor&l${formatDisplayIndexedLabel(data.rebirth, LevelManager.MAX_REBIRTH_LEVEL)}"
    }
    parts += "$rankColor&l${formatDisplayIndexedLabel(rank, LevelManager.getRankMaxLevel(data))}"

    return "$frameColor&l⟦${parts.joinToString("$dividerColor&l•")}$frameColor&l⟧"
}

fun formatAscensionLabel(ascension: Int): String =
    if (ascension <= 0) "A" else spreadsheetLetters(ascension)

private fun formatDisplayIndexedLabel(value: Int, maxValue: Int): String {
    if (value <= 0) return "A"
    return spreadsheetLetters(value)
}

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

fun getRebirthStartingUpgradeLevel(rebirth: Int): Int =
    (1 + (rebirth.coerceAtLeast(0) / 3)).coerceAtMost(10)

fun getAscensionRankupCostMultiplier(data: PlayerData): Double =
    if (data.ascension <= 0) 1.0 else ASCENSION_RANKUP_COST_MULTIPLIER_PER_ASCENSION.pow(data.ascension.toDouble())

fun getAscensionXpCostMultiplier(data: PlayerData): Double =
    if (data.ascension <= 0) 1.0 else ASCENSION_XP_COST_MULTIPLIER_PER_ASCENSION.pow(data.ascension.toDouble())

fun getAscensionMineRichnessMultiplier(data: PlayerData): Double =
    if (data.ascension <= 0) 1.0 else ASCENSION_MINE_WEIGHT_MULTIPLIER_PER_ASCENSION.pow(data.ascension.toDouble())

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
    val startingUpgradeLevel = if (isRebirth) getRebirthStartingUpgradeLevel(data.rebirth) else 1
    val autoMinerFortuneLevel = data.autoMinerFortuneLevel
    val autoMinerEfficiencyLevel = data.autoMinerEfficiencyLevel
    val autoMinerEnergyDrinkLevel = data.autoMinerEnergyDrinkLevel
    val autoMinerBackpackLevel = data.autoMinerBackpackLevel
    val autoMinerLuckLevel = data.autoMinerLuckLevel
    val autoMinerPayoutLevel = data.autoMinerPayoutLevel
    val swordLevel = data.swordLevel
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
    val sellMultiplierAddedLevels = (data.sellMultiplierMaxLevel - LevelManager.sellMultiplierMaxLevel).coerceAtLeast(0)
    val tokenFinderAddedLevels = (data.tokenFinderMaxLevel - LevelManager.tokenFinderMaxLevel).coerceAtLeast(0)
    val keyFinderAddedLevels = (data.keyFinderMaxLevel - LevelManager.keyFinderMaxLevel).coerceAtLeast(0)
    val jackpotAddedLevels = (data.jackpotMaxLevel - LevelManager.jackpotMaxLevel).coerceAtLeast(0)
    val comboAddedLevels = (data.comboMaxLevel - LevelManager.comboMaxLevel).coerceAtLeast(0)
    val procPowerAddedLevels = (data.procPowerMaxLevel - LevelManager.procPowerMaxLevel).coerceAtLeast(0)

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
    data.sellMultiplierLevel = startingUpgradeLevel
    data.tokenFinderLevel = startingUpgradeLevel
    data.keyFinderLevel = startingUpgradeLevel
    data.jackpotLevel = startingUpgradeLevel
    data.comboLevel = startingUpgradeLevel
    data.procPowerLevel = startingUpgradeLevel
    data.autoMinerFortuneLevel = if (preserveAutoMinerUpgrades) autoMinerFortuneLevel else 1
    data.autoMinerEfficiencyLevel = if (preserveAutoMinerUpgrades) autoMinerEfficiencyLevel else 1
    data.autoMinerEnergyDrinkLevel = if (preserveAutoMinerUpgrades) autoMinerEnergyDrinkLevel else 1
    data.autoMinerBackpackLevel = if (preserveAutoMinerUpgrades) autoMinerBackpackLevel else 1
    data.autoMinerLuckLevel = if (preserveAutoMinerUpgrades) autoMinerLuckLevel else 1
    data.autoMinerPayoutLevel = if (preserveAutoMinerUpgrades) autoMinerPayoutLevel else 1
    data.swordLevel = swordLevel
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
    data.sellMultiplierMaxLevel = (LevelManager.sellMultiplierMaxLevel + if (clearEarnedProgress) 0 else sellMultiplierAddedLevels).coerceAtMost(LevelManager.sellMultiplierMaxLevelWithScroll)
    data.tokenFinderMaxLevel = (LevelManager.tokenFinderMaxLevel + if (clearEarnedProgress) 0 else tokenFinderAddedLevels).coerceAtMost(LevelManager.tokenFinderMaxLevelWithScroll)
    data.keyFinderMaxLevel = (LevelManager.keyFinderMaxLevel + if (clearEarnedProgress) 0 else keyFinderAddedLevels).coerceAtMost(LevelManager.keyFinderMaxLevelWithScroll)
    data.jackpotMaxLevel = (LevelManager.jackpotMaxLevel + if (clearEarnedProgress) 0 else jackpotAddedLevels).coerceAtMost(LevelManager.jackpotMaxLevelWithScroll)
    data.comboMaxLevel = (LevelManager.comboMaxLevel + if (clearEarnedProgress) 0 else comboAddedLevels).coerceAtMost(LevelManager.comboMaxLevelWithScroll)
    data.procPowerMaxLevel = (LevelManager.procPowerMaxLevel + if (clearEarnedProgress) 0 else procPowerAddedLevels).coerceAtMost(LevelManager.procPowerMaxLevelWithScroll)
    data.autoMinerFortuneMaxLevel = LevelManager.autoMinerFortuneMaxLevel
    data.autoMinerEfficiencyMaxLevel = LevelManager.autoMinerEfficiencyMaxLevel
    data.autoMinerEnergyDrinkMaxLevel = LevelManager.autoMinerEnergyDrinkMaxLevel
    data.autoMinerBackpackMaxLevel = LevelManager.autoMinerBackpackMaxLevel
    data.autoMinerLuckMaxLevel = LevelManager.autoMinerLuckMaxLevel
    data.autoMinerPayoutMaxLevel = LevelManager.autoMinerPayoutMaxLevel
    data.swordMaxLevel = LevelManager.swordMaxLevel
    data.balance = 0
    data.tokens = 0L
    data.kills = 0L
    data.deaths = 0L
    data.level = 0
    if (!isRebirth) {
        data.blocksMined = 0L
    }
    data.playtimeSeconds = 0L
    data.experienceBuffer = 0.0
    data.hasEnabledPvp = false
    if (!isRebirth) {
        data.playtimeSecondsAtLastRebirth = 0L
    }
    data.paymentUnlockPlaytimeSeconds = 0L
    data.storageContents.clear()
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
        data.newPlayer = null
        data.hasTouched = false
        data.hasBroken = false
        data.hasClosed = false
        data.hasSold = false
        data.valuableBlocksBroken = 0
        data.hasSeenBackpackSellHint = false
        data.hasSeenUpgradeHint = false
        data.tutorialActive = false
        data.tutorialPendingUpgradeClose = false
        data.hasSeenMineHelp = false
        data.hasReceivedJoinLoadout = false
        data.hasClaimedBattlepass.clear()
        data.hasClaimedPlaytimeRewards.clear()
        data.battlepassClaimedRewards.clear()
        data.battlepassClaimedQuests.clear()
        data.battlepassPoints = 0L
        data.battlepassSwordKills = 0
        data.battlepassAxeKills = 0
        data.battlepassMaceKills = 0
        data.battlepassTotalKills = 0
        data.oreBoostActive = false
        data.excavatorActive = false
        data.lightningRodPlaced = false
        data.lightningRodCount = 0
        UpgradeToggleManager.resetToDefaults(data)
    }
    data.rebirth = 0
    data.animationsEnabled = true
    data.animationLinearMode = true
    data.animationExtraBlockDelayTicks = 0L
    data.animationDurationTicks = 8
    data.multiplier = getBasePlayerMultiplier(data)
}

private fun resetScrollProgress(data: PlayerData) {
    data.upgradeScrollBonuses.clear()
    data.multiBreakScrollBonus = 0.0
    data.multiBreakMaxLevel = LevelManager.multiBreakMaxLevel
    data.fortuneMaxLevel = LevelManager.fortuneMaxLevel
    data.oreBoostMaxLevel = LevelManager.oreBoostMaxLevel
    data.excavatorMaxLevel = LevelManager.excavatorMaxLevel
    data.lightningMaxLevel = LevelManager.lightningMaxLevel
    data.virtualJackhammerMaxLevel = LevelManager.virtualJackhammerMaxLevel
    data.excavatorEfficiencyMaxLevel = LevelManager.excavatorEfficiencyMaxLevel
    data.xpGainMaxLevel = LevelManager.xpGainMaxLevel
    data.oreFrequencyMaxLevel = LevelManager.oreFrequencyMaxLevel
    data.scrollFinderMaxLevel = LevelManager.scrollFinderMaxLevel
    data.sellMultiplierMaxLevel = LevelManager.sellMultiplierMaxLevel
    data.tokenFinderMaxLevel = LevelManager.tokenFinderMaxLevel
    data.keyFinderMaxLevel = LevelManager.keyFinderMaxLevel
    data.jackpotMaxLevel = LevelManager.jackpotMaxLevel
    data.comboMaxLevel = LevelManager.comboMaxLevel
    data.procPowerMaxLevel = LevelManager.procPowerMaxLevel
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
    data.sellMultiplierLevel = data.sellMultiplierLevel.coerceAtMost(data.sellMultiplierMaxLevel)
    data.tokenFinderLevel = data.tokenFinderLevel.coerceAtMost(data.tokenFinderMaxLevel)
    data.keyFinderLevel = data.keyFinderLevel.coerceAtMost(data.keyFinderMaxLevel)
    data.jackpotLevel = data.jackpotLevel.coerceAtMost(data.jackpotMaxLevel)
    data.comboLevel = data.comboLevel.coerceAtMost(data.comboMaxLevel)
    data.procPowerLevel = data.procPowerLevel.coerceAtMost(data.procPowerMaxLevel)
}

private fun getBasePlayerMultiplier(data: PlayerData): Double =
    1.0 + (data.rebirth * REBIRTH_MULTIPLIER_PER_REBIRTH) + data.discordMultiplierBonus + data.donorRankMultiplier

fun applyResetToOnlinePlayer(
    player: Player,
    data: PlayerData,
    isRebirth: Boolean = false,
    clearEarnedProgress: Boolean = false,
    resetScrollProgress: Boolean = false
) {
    KitManager.prepareForProgressReset(player)
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
            if (isRebirth && resetScrollProgress) "&dYour inventory, upgrades, rank, level, storage, coins, and scroll progress have been reset."
            else if (isRebirth) "&dYour inventory, upgrades, rank, level, storage, and coins have been reset."
            else if (clearEarnedProgress) "&cYour stats, money, upgrades, rebirths, masteries, and scroll progress have been reset."
            else "&cYour money and upgrades have been reset."
        )
    )
}
