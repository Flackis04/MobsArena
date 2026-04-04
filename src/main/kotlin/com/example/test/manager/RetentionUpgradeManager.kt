package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

object RetentionUpgradeManager {
    private const val COMBO_TIMEOUT_MILLIS = 3_500L
    private const val COMBO_IDLE_REMINDER_TICKS = 20L

    private data class ComboState(
        var streak: Int,
        var lastMineAtMillis: Long
    )

    private val comboStates = mutableMapOf<java.util.UUID, ComboState>()
    private val pendingActivationMessages = mutableMapOf<java.util.UUID, MutableList<String>>()
    private val comboReminderTasks = mutableMapOf<java.util.UUID, BukkitTask>()

    fun recordMineSwing(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "combo") || data.comboLevel <= 1) {
            reset(player.uniqueId)
            return
        }

        val now = System.currentTimeMillis()
        val state = comboStates[player.uniqueId]
        val nextStreak = if (state == null || now - state.lastMineAtMillis > COMBO_TIMEOUT_MILLIS) {
            1
        } else {
            (state.streak + 1).coerceAtMost(UpgradeFormulas.getComboMaxStreak(data.comboLevel, data.comboMaxLevel))
        }
        comboStates[player.uniqueId] = ComboState(nextStreak, now)
        scheduleComboReminder(player)

    }

    fun getComboMultiplier(player: Player): Double {
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "combo") || data.comboLevel <= 1) return 1.0
        val state = comboStates[player.uniqueId] ?: return 1.0
        if (System.currentTimeMillis() - state.lastMineAtMillis > COMBO_TIMEOUT_MILLIS) {
            comboStates.remove(player.uniqueId)
            return 1.0
        }
        return UpgradeFormulas.getComboBonusMultiplier(data.comboLevel, state.streak, data.comboMaxLevel)
    }

    fun tryAwardTokens(player: Player, materials: Collection<Material>): Long {
        if (materials.isEmpty()) return 0L
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "tokenFinder") || data.tokenFinderLevel <= 1) return 0L

        val chance = UpgradeFormulas.getTokenFinderChance(data.tokenFinderLevel, data.tokenFinderMaxLevel)
        val amountRoll = UpgradeFormulas.getTokenFinderAmount(data.tokenFinderLevel, data.tokenFinderMaxLevel)
        var totalAwarded = 0L

        for (material in materials) {
            if (material !in MineManager.mineableBlocks) continue
            if (Random.nextDouble() > chance) continue
            totalAwarded += Random.nextInt(1, amountRoll + 1).toLong()
        }

        if (totalAwarded <= 0L) return 0L
        data.tokens += totalAwarded
        ScoreboardManager.updateBoard(player)
        player.playSound(player.location, "entity.experience_orb.pickup", 0.35f, 1.55f)
        return totalAwarded
    }

    fun tryAwardKeys(player: Player, materials: Collection<Material>): List<KeyRarity> {
        if (materials.isEmpty()) return emptyList()
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "keyFinder") || data.keyFinderLevel <= 1) return emptyList()

        val chance = UpgradeFormulas.getKeyFinderChance(data.keyFinderLevel, data.keyFinderMaxLevel)
        val awarded = mutableListOf<KeyRarity>()

        for (material in materials) {
            if (material !in MineManager.mineableBlocks) continue
            if (Random.nextDouble() > chance) continue
            awarded += rollKeyRarity(data.keyFinderLevel, data.keyFinderMaxLevel)
        }

        if (awarded.isEmpty()) return emptyList()

        awarded.forEach { rarity ->
            Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "excellentcrates key give ${player.name} ${rarity.crateId}"
            )
        }
        player.playSound(player.location, "entity.player.levelup", 0.45f, 1.9f)
        return awarded
    }

    fun tryApplyJackpot(player: Player, originLoc: org.bukkit.Location): List<ItemStack> {
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "jackpot") || data.jackpotLevel <= 1) return emptyList()

        val chance = UpgradeFormulas.getJackpotChance(data.jackpotLevel, data.jackpotMaxLevel)
        if (Random.nextDouble() > chance) return emptyList()

        val rewards = rollJackpotRewards()
        rewards.forEach { reward ->
            val leftover = player.inventory.addItem(reward.clone())
            if (leftover.isNotEmpty()) {
                leftover.values.forEach { originLoc.world?.dropItemNaturally(originLoc, it) }
            }
        }
        queueActivationMessage(player.uniqueId, "&dJackpot! &fContraband drop")
        player.playSound(player.location, "entity.player.levelup", 0.45f, 1.45f)
        return rewards
    }

    fun reset(playerId: java.util.UUID) {
        comboStates.remove(playerId)
        pendingActivationMessages.remove(playerId)
        comboReminderTasks.remove(playerId)?.cancel()
    }

    fun getComboStatus(player: Player): Pair<Int, Double>? {
        val data = DataStore.get(player.uniqueId)
        if (!UpgradeToggleManager.isEnabled(data, "combo") || data.comboLevel <= 1) return null
        val state = comboStates[player.uniqueId] ?: return null
        if (System.currentTimeMillis() - state.lastMineAtMillis > COMBO_TIMEOUT_MILLIS) {
            comboStates.remove(player.uniqueId)
            return null
        }
        return state.streak to UpgradeFormulas.getComboBonusMultiplier(data.comboLevel, state.streak, data.comboMaxLevel)
    }

    fun queueActivationMessage(playerId: java.util.UUID, message: String) {
        pendingActivationMessages.getOrPut(playerId) { mutableListOf() } += message
    }

    fun consumeActivationMessages(playerId: java.util.UUID): List<String> =
        pendingActivationMessages.remove(playerId).orEmpty()

    private fun scheduleComboReminder(player: Player) {
        comboReminderTasks.remove(player.uniqueId)?.cancel()
        val expectedTimestamp = comboStates[player.uniqueId]?.lastMineAtMillis ?: return
        comboReminderTasks[player.uniqueId] = org.bukkit.Bukkit.getScheduler().runTaskLater(
            TestPlugin.instance,
            Runnable {
                comboReminderTasks.remove(player.uniqueId)
                if (!player.isOnline) return@Runnable
                val state = comboStates[player.uniqueId] ?: return@Runnable
                if (state.lastMineAtMillis != expectedTimestamp) return@Runnable
                val comboStatus = getComboStatus(player) ?: return@Runnable
                ActionBarManager.sendActionBarFor(
                    player,
                    1.0,
                    "&6${comboStatus.first}x combo &8| &f${String.format("%.2f", comboStatus.second)}x payout"
                )
            },
            COMBO_IDLE_REMINDER_TICKS
        )
    }

    private fun rollKeyRarity(level: Int, maxLevel: Int): KeyRarity {
        val progress = if (maxLevel <= 1) 1.0 else (level - 1).toDouble() / (maxLevel - 1).toDouble()
        val commonWeight = (82.0 - (progress * 18.0)).coerceAtLeast(58.0)
        val uncommonWeight = 13.0 + (progress * 8.0)
        val rareWeight = 4.0 + (progress * 5.0)
        val epicWeight = 0.85 + (progress * 1.6)
        val legendaryWeight = 0.08 + (progress * 0.42)
        val totalWeight = commonWeight + uncommonWeight + rareWeight + epicWeight + legendaryWeight
        val roll = Random.nextDouble(totalWeight)

        return when {
            roll < commonWeight -> KeyRarity.COMMON
            roll < commonWeight + uncommonWeight -> KeyRarity.UNCOMMON
            roll < commonWeight + uncommonWeight + rareWeight -> KeyRarity.RARE
            roll < commonWeight + uncommonWeight + rareWeight + epicWeight -> KeyRarity.EPIC
            else -> KeyRarity.LEGENDARY
        }
    }

    private fun rollJackpotRewards(): List<ItemStack> {
        val roll = Random.nextDouble()
        return when {
            roll < 0.72 -> listOf(ItemManager.makeJackpotXpBottle(ItemManager.JackpotXpBottleTier.CRUDE))
            roll < 0.99 -> listOf(ItemManager.makeJackpotXpBottle(ItemManager.JackpotXpBottleTier.CHARGED))
            else -> listOf(ItemManager.makeJackpotXpBottle(ItemManager.JackpotXpBottleTier.CELESTIAL))
        }
    }
}
