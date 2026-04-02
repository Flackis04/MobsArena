package com.example.test

import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.Statistic
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.lang.reflect.Modifier
import java.time.Duration

class BaltopCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        sender.sendMessage(TextUtil.colorize("&b&lBaltop"))

        val topBalances = DataStore.all()
            .asSequence()
            .sortedByDescending { it.second.balance }
            .mapNotNull { (uuid, data) ->
                val name = Bukkit.getOfflinePlayer(uuid).name ?: return@mapNotNull null
                if (name == "<none>" || name == "Player") return@mapNotNull null
                name to data.balance
            }
            .take(10)
            .toList()

        if (topBalances.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&7No balance data found."))
            return true
        }

        topBalances.forEachIndexed { index, (name, balance) ->
            sender.sendMessage(
                TextUtil.colorize("&6${index + 1}. &f$name &7- &a$${TextUtil.formatNum(balance)}")
            )
        }
        return true
    }
}

class PayCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (args.size < 2) return false

        val target = Bukkit.getPlayerExact(args[0])
        if (target == null || !target.isOnline) {
            player.sendMessage(TextUtil.colorize("&cThat player must be online to receive payment."))
            return true
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(TextUtil.colorize("&cYou cannot pay yourself."))
            return true
        }

        val amount = args[1].toLongOrNull()
        if (amount == null || amount <= 0L) {
            player.sendMessage(TextUtil.colorize("&cAmount must be a positive whole number."))
            return true
        }

        val senderData = DataStore.get(player.uniqueId)
        if (senderData.balance < amount) {
            player.sendMessage(TextUtil.colorize("&cYou don't have enough ${ItemManager.COIN_NAME_PLURAL}!"))
            return true
        }

        val targetData = DataStore.get(target.uniqueId)
        targetData.playtimeSeconds = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
        // PLAY_ONE_MINUTE : Name is misleading, actually records ticks played.
        if (targetData.rebirth > 0) {
            val unlockPlaytime = when {
                targetData.paymentUnlockPlaytimeSeconds > 0L -> targetData.paymentUnlockPlaytimeSeconds
                targetData.playtimeSecondsAtLastRebirth > 0L -> targetData.playtimeSecondsAtLastRebirth + REBIRTH_PAYMENT_UNLOCK_SECONDS
                else -> 0L
            }
            val remainingPlaytime = (unlockPlaytime - targetData.playtimeSeconds).coerceAtLeast(0L)
            if (remainingPlaytime > 0L) {
                player.sendMessage(
                    TextUtil.colorize(
                        "&c${target.name} must play for &f${TextUtil.formatPlaytime(remainingPlaytime)} &cmore after rebirthing before receiving payments."
                    )
                )
                return true
            }
        }
        senderData.balance -= amount
        targetData.balance += amount

        ScoreboardManager.updateBoard(player)
        ScoreboardManager.updateBoard(target)
        player.sendMessage(TextUtil.colorize("&aPaid &f${target.name} &b${TextUtil.formatNum(amount)} ${ItemManager.COIN_NAME_PLURAL}&a."))
        target.sendMessage(TextUtil.colorize("&aReceived &b${TextUtil.formatNum(amount)} ${ItemManager.COIN_NAME_PLURAL} &afrom &f${player.name}&a."))
        return true
    }
}

class StatsCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        val player = sender as? Player ?: return true
        val target: OfflinePlayer =
            if (args.isNotEmpty()) Bukkit.getOfflinePlayer(args[0]) else player

        val data = DataStore.get(target.uniqueId)

        val status = if (target.isOnline) "&aOnline" else "&cOffline"
        val xpMulti = String.format("%.2f", ExperienceManager.getExperienceMultiplier(data.xpGainLevel, data.xpGainMaxLevel))
        val sellMulti = target.player?.let { String.format("%.2f", KitManager.getEffectiveSellMultiplier(it)) }
            ?: String.format("%.2f", data.multiplier)
        val targetLevel = data.level

        player.sendMessage(TextUtil.colorize("&8&m                                  "))
        player.sendMessage(TextUtil.colorize("&b&lStats &7for &f${target.name} &8($status&8)"))
        player.sendMessage(TextUtil.colorize("&8&m                                  "))

        player.sendMessage(TextUtil.colorize("&7Level: &b$targetLevel"))
        player.sendMessage(TextUtil.colorize("&7Coins: &6${data.balance}"))
        player.sendMessage(TextUtil.colorize("&7Multiplier: &d${sellMulti}x"))
        player.sendMessage(TextUtil.colorize("&7Rebirth: &e${data.rebirth}"))
        player.sendMessage(TextUtil.colorize("&7Ascension: &5${data.ascension}"))
        player.sendMessage(TextUtil.colorize("&8&lMining"))
        player.sendMessage(TextUtil.colorize("&7Blocks Mined: &f${data.blocksMined}"))
        player.sendMessage(TextUtil.colorize("&7Playtime: &f${formatTime(data.playtimeSeconds)}"))

        player.sendMessage(TextUtil.colorize("&8&lUpgrades"))
        player.sendMessage(TextUtil.colorize("&7Fortune: &a${data.fortuneLevel}"))
        player.sendMessage(TextUtil.colorize("&7Excavator: &a${data.excavatorLevel}"))
        player.sendMessage(TextUtil.colorize("&7Lightning: &a${data.lightningLevel}"))
        player.sendMessage(TextUtil.colorize("&7Jackhammer: &a${data.virtualJackhammerLevel}"))
        player.sendMessage(TextUtil.colorize("&7Excavator Efficiency: &a${data.excavatorEfficiencyLevel}"))
        player.sendMessage(TextUtil.colorize("&7Multi Break: &a${data.multiBreakLevel}"))
        player.sendMessage(TextUtil.colorize("&7Ore Boost: &a${data.oreBoostLevel}"))
        player.sendMessage(TextUtil.colorize("&7Ore Frequency: &a${data.oreFrequencyLevel}"))
        player.sendMessage(TextUtil.colorize("&7Scroll Finder: &a${data.scrollFinderLevel}"))
        player.sendMessage(TextUtil.colorize("&7XP Gain: &a${data.xpGainLevel} &7(&fx$xpMulti&7)"))
        player.sendMessage(TextUtil.colorize("&7Net Mine Weight: &b${String.format("%.2f", MineManager.getNetMineWeightMultiplier(target.uniqueId))}x"))
        if (data.rebirth >= 1) {
            player.sendMessage(TextUtil.colorize("&8&lAuto Miner"))
            player.sendMessage(TextUtil.colorize("&7AutoMiner Fortune: &a${data.autoMinerFortuneLevel}"))
            player.sendMessage(TextUtil.colorize("&7AutoMiner Efficiency: &a${data.autoMinerEfficiencyLevel}"))
            player.sendMessage(TextUtil.colorize("&7Energy Drink: &a${data.autoMinerEnergyDrinkLevel}"))
            player.sendMessage(TextUtil.colorize("&7Backpack: &a${data.autoMinerBackpackLevel}"))
            player.sendMessage(TextUtil.colorize("&7Luck: &a${data.autoMinerLuckLevel}"))
        }
        if (data.hasDonorRank) {
            player.sendMessage(TextUtil.colorize("&8&lDonor Rank"))
            player.sendMessage(TextUtil.colorize("&7Donor Multiplier: &d+${String.format("%.2f", data.donorRankMultiplier)}x"))
            player.sendMessage(TextUtil.colorize("&7Mine Weight: &b${String.format("%.2f", data.mineWeightBonusMultiplier)}x"))
            player.sendMessage(TextUtil.colorize("&7Extra XP: &a${String.format("%.2f", data.extraExperienceMultiplier)}x"))
        }

        player.sendMessage(TextUtil.colorize("&8&m                                      "))

        return true
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "${hours}h ${minutes}m ${secs}s"
    }
}

class SetDataCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.size < 3) return false

        val target = Bukkit.getOfflinePlayer(args[0])
        val fieldName = args[1]
        val rawValue = args.drop(2).joinToString(" ")
        val data = DataStore.get(target.uniqueId)
        val field = PlayerData::class.java.declaredFields.firstOrNull { candidate ->
            !Modifier.isStatic(candidate.modifiers) && candidate.name.equals(fieldName, ignoreCase = true)
        }

        if (field == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown field: &f$fieldName"))
            return true
        }

        val parsedValue = parsePlayerDataValue(field.type, rawValue)
        if (parsedValue == null) {
            sender.sendMessage(TextUtil.colorize("&cInvalid value &f$rawValue &cfor &f${field.name}&c."))
            return true
        }

        field.isAccessible = true
        field.set(data, parsedValue)

        sender.sendMessage(TextUtil.colorize("&aSet &f${target.name ?: args[0]}&a.&b${field.name} &ato &e$rawValue&a."))
        target.player?.let { onlineTarget ->
            ScoreboardManager.updateBoard(onlineTarget)
        }
        return true
    }

    private fun parsePlayerDataValue(type: Class<*>, rawValue: String): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> rawValue.toBooleanStrictOrNull()
            java.lang.Integer.TYPE -> rawValue.toIntOrNull()
            java.lang.Long.TYPE -> rawValue.toLongOrNull()
            java.lang.Double.TYPE -> rawValue.toDoubleOrNull()
            java.lang.Float.TYPE -> rawValue.toFloatOrNull()
            String::class.java -> rawValue
            else -> null
    }
}

class SetMasteryCommand : CommandExecutor {
    private val supportedMasteries = setOf(
        "oreBoost",
        "excavator",
        "lightning",
        "virtualJackhammer"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.size < 3) return false

        val target = Bukkit.getOfflinePlayer(args[0])
        val requestedKey = args[1]
        val masteryKey = supportedMasteries.firstOrNull { it.equals(requestedKey, ignoreCase = true) }
        if (masteryKey == null) {
            sender.sendMessage(
                TextUtil.colorize(
                    "&cUnknown mastery key. Supported: &f${supportedMasteries.joinToString(", ")}"
                )
            )
            return true
        }

        val level = args[2].toIntOrNull()
        if (level == null || level !in 0..7) {
            sender.sendMessage(TextUtil.colorize("&cMastery level must be between &f0 &cand &f7&c."))
            return true
        }

        val data = DataStore.get(target.uniqueId)
        data.masteryLevels[masteryKey] = level
        sender.sendMessage(
            TextUtil.colorize(
                "&aSet &f${target.name ?: args[0]}&a mastery &b$masteryKey &ato &e$level&a."
            )
        )
        target.player?.let { onlineTarget ->
            ScoreboardManager.updateBoard(onlineTarget)
        }
        return true
    }
}

class MaxStatsCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        val target = when {
            args.isNotEmpty() -> Bukkit.getOfflinePlayer(args[0])
            sender is Player -> sender
            else -> {
                sender.sendMessage(TextUtil.colorize("&cUsage: /maxstats <player>"))
                return true
            }
        }

        val data = DataStore.get(target.uniqueId)
        maxOutPlayerData(data)

        target.player?.let { onlineTarget ->
            onlineTarget.totalExperience = 0
            onlineTarget.exp = 0f
            ExperienceManager.restoreStoredLevel(onlineTarget)
            ExperienceManager.clamp(onlineTarget)
            HealthManager.apply(onlineTarget, data.level)
            KitManager.refreshPickaxe(onlineTarget)
            MineManager.refreshMineFor(onlineTarget.uniqueId)
            ScoreboardManager.updateBoard(onlineTarget)
        }

        sender.sendMessage(TextUtil.colorize("&aMaxed all bounded stats for &f${target.name ?: args.firstOrNull() ?: "that player"}&a."))
        target.player?.sendMessage(TextUtil.colorize("&aYour stats have been maxed out."))
        return true
    }

    private fun maxOutPlayerData(data: PlayerData) {
        data.rank = LevelManager.rankMaxLevel
        data.rebirth = LevelManager.MAX_REBIRTH_LEVEL
        data.level = LevelManager.getRequiredLevelForNextRebirth(LevelManager.MAX_REBIRTH_LEVEL)
        data.balance = 1_000_000_000_000L
        data.blocksMined = 12_500_000L
        data.playtimeSeconds = 604_800L
        data.playtimeSecondsAtLastRebirth = data.playtimeSeconds
        data.kills = 10_000L
        data.deaths = 10_000L

        data.multiBreakMaxLevel = LevelManager.multiBreakMaxLevelWithScroll
        data.multiBreakLevel = data.multiBreakMaxLevel
        data.fortuneMaxLevel = LevelManager.fortuneMaxLevelWithScroll
        data.fortuneLevel = data.fortuneMaxLevel
        data.oreBoostMaxLevel = LevelManager.oreBoostMaxLevelWithScroll
        data.oreBoostLevel = data.oreBoostMaxLevel
        data.excavatorMaxLevel = LevelManager.excavatorMaxLevelWithScroll
        data.excavatorLevel = data.excavatorMaxLevel
        data.lightningMaxLevel = LevelManager.lightningMaxLevelWithScroll
        data.lightningLevel = data.lightningMaxLevel
        data.virtualJackhammerMaxLevel = LevelManager.virtualJackhammerMaxLevelWithScroll
        data.virtualJackhammerLevel = data.virtualJackhammerMaxLevel
        data.excavatorEfficiencyMaxLevel = LevelManager.excavatorEfficiencyMaxLevelWithScroll
        data.excavatorEfficiencyLevel = data.excavatorEfficiencyMaxLevel
        data.xpGainMaxLevel = LevelManager.xpGainMaxLevelWithScroll
        data.xpGainLevel = data.xpGainMaxLevel
        data.oreFrequencyMaxLevel = LevelManager.oreFrequencyMaxLevelWithScroll
        data.oreFrequencyLevel = data.oreFrequencyMaxLevel
        data.scrollFinderMaxLevel = LevelManager.scrollFinderMaxLevelWithScroll
        data.scrollFinderLevel = data.scrollFinderMaxLevel
        data.backpackMaxLevel = LevelManager.backpackMaxLevelWithScroll
        data.backpackLevel = data.backpackMaxLevel

        data.autoMinerFortuneMaxLevel = LevelManager.autoMinerFortuneMaxLevelWithScroll
        data.autoMinerFortuneLevel = data.autoMinerFortuneMaxLevel
        data.autoMinerEfficiencyMaxLevel = LevelManager.autoMinerEfficiencyMaxLevelWithScroll
        data.autoMinerEfficiencyLevel = data.autoMinerEfficiencyMaxLevel
        data.autoMinerEnergyDrinkMaxLevel = LevelManager.autoMinerEnergyDrinkMaxLevelWithScroll
        data.autoMinerEnergyDrinkLevel = data.autoMinerEnergyDrinkMaxLevel
        data.autoMinerBackpackMaxLevel = LevelManager.autoMinerBackpackMaxLevelWithScroll
        data.autoMinerBackpackLevel = data.autoMinerBackpackMaxLevel
        data.autoMinerLuckMaxLevel = LevelManager.autoMinerLuckMaxLevelWithScroll
        data.autoMinerLuckLevel = data.autoMinerLuckMaxLevel

        data.masteryLevels["oreBoost"] = 7
        data.masteryLevels["excavator"] = 7
        data.masteryLevels["lightning"] = 7
        data.masteryLevels["virtualJackhammer"] = 7
        data.upgradeScrollBonuses.clear()
        data.multiBreakScrollBonus = 0.0

        MineManager.valuables.forEach { material ->
            data.valuableCollected[material.name] = 1_000L
        }

        data.lightningRodPlaced = true
        data.lightningRodCount = 100
        data.newPlayer = false
        data.tutorialActive = false
        data.tutorialPendingUpgradeClose = false
        data.hasBroken = true
        data.hasSold = true
        data.hasSeenUpgradeHint = true
        data.hasSeenBackpackSellHint = true
        data.valuableBlocksBroken = 10
        data.multiplier = 1.0 +
            (data.rebirth * REBIRTH_MULTIPLIER_PER_REBIRTH) +
            data.discordMultiplierBonus +
            data.donorRankMultiplier
        LevelManager.checkLevels(data)
    }
}

class ResetValuableMasteriesCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        var resetPlayers = 0
        for ((_, data) in DataStore.all()) {
            val hadValuableProgress = data.valuableCollected.isNotEmpty()
            val removedLevels = data.masteryLevels.keys.removeIf { it.startsWith("valuable:") }
            if (!hadValuableProgress && !removedLevels) continue

            data.valuableCollected.clear()
            resetPlayers++
        }

        Bukkit.getOnlinePlayers().forEach { player ->
            ScoreboardManager.updateBoard(player)
        }

        sender.sendMessage(
            TextUtil.colorize("&aReset valuable masteries for &f$resetPlayers &aplayer${if (resetPlayers == 1) "" else "s"}&a.")
        )
        return true
    }
}

class MobsHelpCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        player.sendMessage(TextUtil.colorize("&8&m------------------------------------------------"))
        player.sendMessage(TextUtil.colorize("&d&lMobs Arena Help"))
        player.sendMessage(TextUtil.colorize("&7Useful commands for getting around, upgrading, and progressing."))
        player.sendMessage(TextUtil.colorize("&8&m------------------------------------------------"))
        player.sendMessage(TextUtil.colorize("&a/spawn &8- &7Teleport to the main mine spawn area."))
        player.sendMessage(TextUtil.colorize("&a/mine &8- &7Teleport to the center of your personal mine."))
        player.sendMessage(TextUtil.colorize("&a/stats [player] &8- &7View your stats or inspect another player's progress."))
        player.sendMessage(TextUtil.colorize("&a/blackmarket &8- &7Open the black market shop for rotating items and deals."))
        player.sendMessage(TextUtil.colorize("&a/rankup &8- &7Buy the next rank to unlock stronger gear and rewards."))
        player.sendMessage(TextUtil.colorize("&a/rebirth &8- &7Reset your progress for rebirth perks and multiplier gains."))
        player.sendMessage(TextUtil.colorize("&a/upgrades &8- &7Open your permanent upgrades menu."))
        player.sendMessage(TextUtil.colorize("&a/autominer &8- &7Open your autominer and manage its backpack and upgrades."))
        player.sendMessage(TextUtil.colorize("&a/clan &8- &7Create a clan, invite members, and buy clan upgrades."))
        player.sendMessage(TextUtil.colorize("&a/trust &8- &7Grant mine access to specific players."))
        player.sendMessage(TextUtil.colorize("&a/store &8- &7Open the server store menu for premium perks and bundles."))
        player.sendMessage(TextUtil.colorize("&8&m------------------------------------------------"))
        return true
    }
}

class KitCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cYou must specify a kit number."))
            return true
        }
        val kit = args[0].toIntOrNull() ?: return true
        val target: Player? = when {
            args.size >= 2 -> Bukkit.getPlayer(args[1])
            sender is Player -> sender
            else -> null
        }
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cYou must specify a player when running this command from console."))
            return true
        }
        KitManager.giveTierKit(target, kit)
        return true
    }
}

class HeadHunterCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        HeadHunterManager.runHeadHunter(player)
        return true
    }
}

class MinerCommand(private val gui: OreIndexGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.open(player)
        return true
    }
}

class SpawnCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        player.teleport(MineManager.getSpawnLocation())
        return true
    }
}

class MineCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        val location = if (args.isEmpty()) {
            MineManager.ensureMineFor(player)
            MineManager.getPlayerMineCenterLocation(player)
        } else {
            val target = Bukkit.getPlayerExact(args[0])
            if (target == null || !target.isOnline) {
                player.sendMessage(TextUtil.colorize("&cThat player's mine is only available while they are online."))
                return true
            }
            MineManager.ensureMineFor(target)
            if (!MineAccessManager.canAccessMine(target.uniqueId, player)) {
                player.sendMessage(TextUtil.colorize("&cYou do not have access to ${target.name}'s mine."))
                return true
            }
            MineManager.getPlayerMineCenterLocation(target)
        } ?: return true
        player.teleport(location.add(0.0, 1.0, 0.0))
        TutorialManager.showMineHelpOnce(player)
        return true
    }
}

class AutoMinerCommand(private val gui: AutoMinerGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (DataStore.get(player.uniqueId).rebirth < 1) {
            player.showTitle(
                Title.title(
                    TextUtil.toComponent("&cRebirth B Required"),
                    TextUtil.toComponent("&7You need rebirth B before using autominers."),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                )
            )
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return true
        }
        gui.open(player)
        return true
    }
}

class ShopCommand(private val gui: BlackMarketGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.openMain(player)
        player.playSound(player.location, "entity.villager.celebrate", 1f, 1f)
        return true
    }
}

class PermUpgradesCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        tryOpenPermUpgradeGui(player)
        return true
    }
}

class BattlepassCommand(private val gui: BattlepassGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.open(player)
        return true
    }
}

class NewPlayerCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val target: Player? = when {
            args.isNotEmpty() -> Bukkit.getPlayer(args[0])
            sender is Player -> sender
            else -> null
        }
        if (target == null) return true
        val bool = args.getOrNull(1)?.toBooleanStrictOrNull() ?: true
        val data = DataStore.get(target.uniqueId)
        if (bool) {
            data.newPlayer = null
            data.hasTouched = false
            data.hasBroken = false
            data.hasClosed = false
            data.hasSold = false
            data.valuableBlocksBroken = 0
            data.hasSeenUpgradeHint = false
            data.hasReceivedJoinLoadout = false
            target.sendMessage(TextUtil.colorize("&7Set newplayer to $bool. &c Don't forget to unset!"))
        } else {
            target.sendMessage(TextUtil.colorize("&7Set newplayer to $bool"))
        }
        return true
    }
}

class FlyCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        val data = DataStore.get(player.uniqueId)
        if (!data.flightUnlocked) {
            player.sendMessage(TextUtil.colorize("&cYou must link your Discord account to use /togglefly."))
            return true
        }

        data.flight = !data.flight
        player.allowFlight = data.flight
        if (!data.flight) {
            player.isFlying = false
        }

        player.sendMessage(
            TextUtil.colorize(
                if (data.flight) "&aFlight enabled." else "&cFlight disabled."
            )
        )
        return true
    }
}

class AnimationCommand(private val gui: AnimationGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.open(player)
        return true
    }
}

class RebirthCommand(private val gui: RankUpGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.openRebirth(player)
        return true
    }
}

class ResetCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.reset")) {
            sendPermissionMessage(sender)
            return true
        }

        val ownerTriggeredFullWipe = sender is Player && ScoreboardManager.debugPrimaryGroup(sender).equals("owner", ignoreCase = true)

        val targetName = args.getOrNull(0) ?: if (sender is Player) sender.name else null
        if (targetName == null) {
            sender.sendMessage(TextUtil.colorize("&cYou must specify a player when running this command from console."))
            return true
        }

        if (targetName == "all") {
            DataStore.all().forEach { (uuid, data) ->
                resetPlayerData(data, clearEarnedProgress = ownerTriggeredFullWipe)

                val p = Bukkit.getPlayer(uuid)
                if (p != null) {
                    applyResetToOnlinePlayer(p, data, clearEarnedProgress = ownerTriggeredFullWipe)
                }
            }
            sender.sendMessage(
                TextUtil.colorize(
                    if (ownerTriggeredFullWipe) "&cAll stats reset for &eALL PLAYERS&c."
                    else "&cMoney and upgrades reset for &eALL PLAYERS&c."
                )
            )
            return true
        }

        val offlineTarget = Bukkit.getOfflinePlayer(targetName)
        val data = DataStore.get(offlineTarget.uniqueId)
        val clearEarnedProgress = ownerTriggeredFullWipe || (sender is Player && sender.uniqueId == offlineTarget.uniqueId)
        resetPlayerData(data, clearEarnedProgress = clearEarnedProgress)

        val target = offlineTarget.player
        if (target != null) {
            applyResetToOnlinePlayer(target, data, clearEarnedProgress = clearEarnedProgress)
        }

        sender.sendMessage(
            TextUtil.colorize(
                if (clearEarnedProgress) "&cAll stats reset for &e$targetName&c."
                else "&cMoney and upgrades reset for &e$targetName&c."
            )
        )
        return true
    }
}

class GiveCoinsCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.givecoins")) {
            sendPermissionMessage(sender)
            return true
        }

        val targetName = args.getOrNull(0)
        if (targetName == null) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /give <player> <amount> [bank]"))
            return true
        }

        val offline = Bukkit.getOfflinePlayer(targetName)
        val target = Bukkit.getPlayer(targetName)
        val amount = args.getOrNull(1)?.toLongOrNull() ?: 1L
        val bank = args.getOrNull(2)?.toBooleanStrictOrNull() ?: false

        if (bank) {
            val stacks = amount / 64
            val remainder = amount % 64
            repeat(stacks.toInt()) {
                target?.inventory?.addItem(ItemManager.coin.clone().apply { this.amount = 64 })
            }
            if (remainder > 0) {
                target?.inventory?.addItem(ItemManager.coin.clone().apply { this.amount = remainder.toInt() })
            }

            sender.sendMessage(TextUtil.colorize("&aGave &e$amount ${ItemManager.COIN_NAME_PLURAL}&a to ${offline.name}."))
            return true

        }

        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cTarget must be online to receive items."))
            return true
        }

        val data = DataStore.get(offline.uniqueId)
        data.balance += amount
        ScoreboardManager.updateBoard(target)
        sender.sendMessage(TextUtil.colorize("&aAdded &e$amount ${ItemManager.COIN_NAME_PLURAL} &ato ${offline.name}."))
        target.sendMessage(TextUtil.colorize("&aYou received &e$amount ${ItemManager.COIN_NAME_PLURAL}&a."))
        return true
    }
}

class GiveDynamiteCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        val targetName = args.getOrNull(0)
        if (targetName == null) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /givedynamite <player> [amount]"))
            return true
        }

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cTarget must be online to receive dynamite."))
            return true
        }

        val amount = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        target.inventory.addItem(ItemManager.dynamite.clone().apply { this.amount = amount.coerceAtMost(64) })

        sender.sendMessage(TextUtil.colorize("&aGave &cx$amount Dynamite &ato ${target.name}."))
        target.sendMessage(TextUtil.colorize("&aYou received &cx$amount Dynamite&a."))
        return true
    }
}

class EventCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        if (BossbarManager.isActive || RareOresEventManager.isActive()) {
            sender.sendMessage(TextUtil.colorize("&cAn event is already running. End it before starting or rerolling another one."))
            return true
        }

        if (args.firstOrNull()?.equals("start", ignoreCase = true) == true) {
            BossbarManager.blocksMinedGlobally = BossbarManager.requirementBlocks
            BossbarManager.updateBlocksMinedEvent()
            sender.sendMessage(TextUtil.colorize("&aForced the current event to start."))
            return true
        }

        val forcedType = BossbarManager.forceEventType(args.firstOrNull())
        if (forcedType.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /event [random|goldrush|minemadness|rareores|start]"))
            return true
        }

        sender.sendMessage(TextUtil.colorize("&aCurrent event changed to &b$forcedType&a. Use &b/event start&a to activate it instantly."))
        return true
    }
}

class StartEventCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        if (args.isNotEmpty()) {
            val forcedType = BossbarManager.forceEventType(args[0])
            if (forcedType.isEmpty()) {
                sender.sendMessage(TextUtil.colorize("&cUsage: /startevent [goldrush|rareores] [normal|rare|mythic|god|secret]"))
                return true
            }
            val forcedRarity = BossbarManager.forceEventRarity(args.getOrNull(1) ?: "normal")
            if (forcedRarity.isEmpty()) {
                sender.sendMessage(TextUtil.colorize("&cUsage: /startevent [goldrush|rareores] [normal|rare|mythic|god|secret]"))
                return true
            }

            if (RareOresEventManager.isActive()) {
                RareOresEventManager.cancelCurrentEvent()
            }
            if (BossbarManager.isActive) {
                BossbarManager.cancelCurrentEvent()
            }

            BossbarManager.forceEventType(forcedType)
            BossbarManager.forceEventRarity(forcedRarity)
            BossbarManager.blocksMinedGlobally = BossbarManager.requirementBlocks
            BossbarManager.updateBlocksMinedEvent()
            sender.sendMessage(TextUtil.colorize("&aForced event &b$forcedType &awith rarity &e${forcedRarity.replaceFirstChar { it.uppercase() }}&a to start."))
            return true
        }

        if (RareOresEventManager.isActive()) {
            RareOresEventManager.cancelCurrentEvent()
        }
        if (BossbarManager.isActive) {
            BossbarManager.cancelCurrentEvent()
        }

        BossbarManager.blocksMinedGlobally = BossbarManager.requirementBlocks
        BossbarManager.updateBlocksMinedEvent()
        sender.sendMessage(TextUtil.colorize("&aForced the current event to start."))
        return true
    }
}

class HeadDisplayCommands : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (!player.hasPermission("command.dev")) {
            sendPermissionMessage(player)
            return true
        }
        when (command.name.lowercase()) {
            "placeheadsonwall" -> {
                var y = 159.0
                val offset = 2.0
                val startZ = -8.5
                var z = startZ
                for (i in 1..TierManager.tiers.size) {
                    z += offset
                    if ((i - 1) % 10 == 0) {
                        y -= offset
                        z = startZ
                    }
                    player.performCommand("hologram copy axolotl $i")
                    player.performCommand("hologram edit $i moveTo 18.75 $y $z")
                    player.performCommand("hologram edit $i item")
                }
            }
            "removeheadsonwall" -> {
                for (i in 1..TierManager.tiers.size) {
                    player.performCommand("hologram remove $i")
                }
            }
        }
        return true
    }
}

class MineResetCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (!player.hasPermission("command.dev")) {
            sendPermissionMessage(player)
            return true
        }
        MineManager.setMine()
        return true
    }
}

class MoveLBCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (!player.hasPermission("command.dev")) {
            sendPermissionMessage(player)
            return true
        }
        val loc = Location(player.world, player.x, player.y, player.z, player.yaw, player.pitch)

        StatsManager.setWallOfFameLocation(loc)
        StatsManager.refreshWallOfFame(loc)
        return true
    }
}

class TabDebugCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        if (args.isNotEmpty()) {
            val target = Bukkit.getPlayerExact(args[0])
            if (target == null) {
                sender.sendMessage(TextUtil.colorize("&cPlayer not found."))
                return true
            }

            val data = DataStore.get(target.uniqueId)
            sender.sendMessage(TextUtil.colorize("&b&lName Debug &7for &f${target.name}"))
            sender.sendMessage(TextUtil.colorize("&7Group: &b${ScoreboardManager.debugPrimaryGroup(target)}"))
            sender.sendMessage(TextUtil.colorize("&7In-game rank: &d${data.rank}"))
            sender.sendMessage(TextUtil.colorize("&7Expected team id: &e${ScoreboardManager.debugNameTeamId(target)}"))
            sender.sendMessage(
                TextUtil.colorize(
                    "&7Rendered prefix: &r${ScoreboardManager.debugRenderedNamePrefix(target)}&f<name>"
                )
            )
            sender.sendMessage(
                TextUtil.colorize(
                    "&7Rendered suffix: &f<name>${ScoreboardManager.debugRenderedNameSuffix(target)}"
                )
            )

            val viewer = sender as? Player
            if (viewer != null) {
                sender.sendMessage(
                    TextUtil.colorize(
                        "&7Your scoreboard team state: &a${ScoreboardManager.debugViewerTeamState(viewer, target)}"
                    )
                )
            } else {
                sender.sendMessage(TextUtil.colorize("&7Viewer scoreboard team state: &8console has no scoreboard"))
            }
            return true
        }

        sender.sendMessage(TextUtil.colorize("&b&lTab Debug"))
        Bukkit.getOnlinePlayers()
            .sortedBy { ScoreboardManager.debugTabOrder(it) }
            .forEach { player ->
                val data = DataStore.get(player.uniqueId)
                val group = ScoreboardManager.debugPrimaryGroup(player)
                val order = ScoreboardManager.debugTabOrder(player)
                sender.sendMessage(
                    TextUtil.colorize(
                        "&7${player.name} &8| &fgroup: &b$group &8| &frank: &d${data.rank} &8| &forder: &a$order"
                    )
                )
            }
        return true
    }
}

class OnMinerPurchaseCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.isEmpty()) return false

        val target = Bukkit.getPlayerExact(args[0])
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."))
            return true
        }

        val data = DataStore.get(target.uniqueId)
        data.hasDonorRank = true
        data.donorRankMultiplier = 1.0
        data.multiplier += data.donorRankMultiplier
        ScoreboardManager.updateBoard(target)
        SessionTimelineManager.record(target, "Received Miner Purchase perks (+1.0x multiplier, 1.1x proc chance)")

        sender.sendMessage(TextUtil.colorize("&aAdded &b+1.0x &amultiplier to &f${target.name}&a."))
        target.sendMessage(TextUtil.colorize("&aYour miner purchase added &b+1.0x &amultiplier and &b+10% &aproc chance."))
        sendPurchaseCongratulations(target, "Miner Purchase", "+1.0x multiplier | +10% proc chance")
        return true
    }
}

class OnExcavatorPurchaseCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.isEmpty()) return false

        val target = Bukkit.getPlayerExact(args[0])
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."))
            return true
        }

        val data = DataStore.get(target.uniqueId)
        data.hasDonorRank = true
        data.donorRankMultiplier = 2.5
        data.multiplier += data.donorRankMultiplier
        data.mineWeightBonusMultiplier = 2.5
        data.extraExperienceMultiplier *= 3.0
        MineManager.refreshMineFor(target.uniqueId)
        ScoreboardManager.updateBoard(target)
        SessionTimelineManager.record(target, "Received Excavator Purchase perks (+2.5x multiplier, 2.5x mine weight, +2x XP, 1.25x proc chance)")

        sender.sendMessage(TextUtil.colorize("&aAdded &b+2.5x &amultiplier, &b2.5x &amine weight, and &b+2x &aXP gain to &f${target.name}&a."))
        target.sendMessage(TextUtil.colorize("&aYour excavator purchase added &b+2.5x &amultiplier, &b2.5x &avaluable weight in the mine area, &b+2x &aXP gain, and &b+25% &aproc chance."))
        sendPurchaseCongratulations(target, "Excavator Purchase", "+2.5x multiplier | 2.5x mine weight | +2x XP | +25% proc chance")
        return true
    }
}

class OnNukerPurchaseCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.isEmpty()) return false

        val target = Bukkit.getPlayerExact(args[0])
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found."))
            return true
        }

        val data = DataStore.get(target.uniqueId)
        data.hasDonorRank = true
        data.donorRankMultiplier = 5.0
        data.multiplier += data.donorRankMultiplier
        data.mineWeightBonusMultiplier = 5.0
        data.extraExperienceMultiplier *= 5.0
        MineManager.refreshMineFor(target.uniqueId)
        ScoreboardManager.updateBoard(target)
        SessionTimelineManager.record(target, "Received Nuker Purchase perks (+5.0x multiplier, 5.0x mine weight, +4x XP, 1.5x proc chance)")

        sender.sendMessage(TextUtil.colorize("&aAdded &b+5.0x &amultiplier, &b5.0x &amine weight, and &b+4x &aXP gain to &f${target.name}&a."))
        target.sendMessage(TextUtil.colorize("&aYour nuker purchase added &b+5.0x &amultiplier, &b5.0x &avaluable weight in the mine area, &b+4x &aXP gain, and &b+50% &aproc chance."))
        sendPurchaseCongratulations(target, "Nuker Purchase", "+5.0x multiplier | 5.0x mine weight | +4x XP | +50% proc chance")
        return true
    }
}

class RankUpCommand(private val gui: RankUpGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.open(player)
        return true
    }
}

class StoreCommand(private val gui: StoreGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.open(player)
        return true
    }
}

class DiscordLinkRewardsCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        if (args.isEmpty()) return true

        val player = Bukkit.getPlayerExact(args[0]) ?: return true

        val playerdata = DataStore.get(player.uniqueId)
        if (!playerdata.hasLinkedDiscord) {
            playerdata.hasLinkedDiscord = true
            playerdata.discordMultiplierBonus = 0.25
            playerdata.multiplier += playerdata.discordMultiplierBonus
        }
        playerdata.balance += 2500
        playerdata.flightUnlocked = true
        playerdata.flight = true

        player.allowFlight = true

        player.showTitle(
            Title.title(
                TextUtil.toComponent("&9Discord Linked!"),
                TextUtil.toComponent("&a+0.25x Multiplier &7| &e+2500 Coins &7| &bFlight Unlocked"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofSeconds(1))
            )
        )

        player.sendMessage(TextUtil.colorize("&aCongrats on linking your Discord account!"))
        player.sendMessage(
            TextUtil.colorize("&7You received &b+0.25x multiplier&7, &e2500 coins&7, and &b/fly&7 access.")
        )

        player.playSound(player.location, "entity.player.levelup", 1f, 1f)
        ScoreboardManager.updateBoard(player)

        return true
    }
}

class GiveScrollCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }

        val player = sender as? Player ?: return true
        if (args.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cUsage: /givescroll <rarity> [amount]"))
            return true
        }

        val rarity = ScrollRarity.entries.firstOrNull { it.id.equals(args[0], ignoreCase = true) }
        if (rarity == null) {
            player.sendMessage(TextUtil.colorize("&cUnknown rarity. Use: &fnormal, rare, mythic, god, secret"))
            return true
        }

        val amount = args.getOrNull(1)?.toIntOrNull()
        if (args.size >= 2 && (amount == null || amount <= 0)) {
            player.sendMessage(TextUtil.colorize("&cAmount must be a positive whole number."))
            return true
        }

        val totalAmount = amount ?: 1
        val scroll = ItemManager.makeUpgradeScroll(rarity).apply { this.amount = totalAmount.coerceAtMost(maxStackSize) }
        player.inventory.addItem(scroll)
        player.sendMessage(TextUtil.colorize("&aGiven &f$totalAmount ${rarity.displayName} Upgrade Scroll${if (totalAmount == 1) "" else "s"}&a."))
        return true
    }
}

class AscendCommand(private val gui: RankUpGui) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        gui.openAscend(player)
        return true
    }
}
