package com.example.test

import org.bukkit.Material
import org.bukkit.entity.Player
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
        if (data.comboLevel <= 1) return

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
        if (data.comboLevel <= 1) return 1.0
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
        if (data.tokenFinderLevel <= 1) return 0L

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

    fun tryApplyJackpot(player: Player, baseAmount: Int): Int {
        if (baseAmount <= 0) return 0
        val data = DataStore.get(player.uniqueId)
        if (data.jackpotLevel <= 1) return baseAmount

        val chance = UpgradeFormulas.getJackpotChance(data.jackpotLevel, data.jackpotMaxLevel)
        if (Random.nextDouble() > chance) return baseAmount

        val jackpotMultiplier = UpgradeFormulas.getJackpotMultiplier(data.jackpotLevel, data.jackpotMaxLevel)
        val totalAmount = BlockRemovalManager.rollScaledAmount(baseAmount, jackpotMultiplier)
        queueActivationMessage(player.uniqueId, "&dJackpot! &f${String.format("%.2f", jackpotMultiplier)}x")
        player.playSound(player.location, "entity.player.levelup", 0.45f, 1.45f)
        return totalAmount
    }

    fun reset(playerId: java.util.UUID) {
        comboStates.remove(playerId)
        pendingActivationMessages.remove(playerId)
        comboReminderTasks.remove(playerId)?.cancel()
    }

    fun getComboStatus(player: Player): Pair<Int, Double>? {
        val data = DataStore.get(player.uniqueId)
        if (data.comboLevel <= 1) return null
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
}
