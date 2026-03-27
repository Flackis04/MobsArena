package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object WelcomeRewardManager {
    private const val REWARD_AMOUNT = 1_000L
    private const val NEW_PLAYER_WINDOW_MS = 2 * 60 * 1000L

    private val pendingNewPlayers = mutableMapOf<UUID, Long>()
    private val rewardedGreeters = mutableMapOf<UUID, MutableSet<UUID>>()

    fun registerNewPlayer(player: Player) {
        cleanupExpired()
        pendingNewPlayers[player.uniqueId] = System.currentTimeMillis() + NEW_PLAYER_WINDOW_MS
        rewardedGreeters[player.uniqueId] = mutableSetOf()
    }

    fun tryRewardGreeter(player: Player, message: String) {
        if (!message.contains("welcome", ignoreCase = true)) return

        cleanupExpired()

        val newcomerId = pendingNewPlayers.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .firstOrNull { it != player.uniqueId && rewardedGreeters[it]?.contains(player.uniqueId) != true }
            ?: return

        rewardedGreeters.getOrPut(newcomerId) { mutableSetOf() }.add(player.uniqueId)

        val data = DataStore.get(player.uniqueId)
        data.balance += REWARD_AMOUNT

        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            player.sendMessage(TextUtil.colorize("&aYou earned &b${TextUtil.formatNum(REWARD_AMOUNT)} ${ItemManager.COIN_NAME_PLURAL} &afor welcoming a new player."))
            ScoreboardManager.updateBoard(player)
        })
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = pendingNewPlayers.filterValues { it < now }.keys
        expired.forEach {
            pendingNewPlayers.remove(it)
            rewardedGreeters.remove(it)
        }
    }
}
