package com.example.test

import org.bukkit.Bukkit

object MobsArenaPlaceholderData {
    fun getActiveEventTimeRemainingMillis(): Long = BossbarManager.getActiveEventTimeRemainingMillis()

    fun getActiveEventTimeRemainingFormatted(): String {
        val totalSeconds = getActiveEventTimeRemainingMillis() / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun getRequirementProgressCurrent(): Long =
        when {
            BossbarManager.isPlayerCountEvent && !BossbarManager.isActive -> Bukkit.getOnlinePlayers().size.toLong()
            BossbarManager.isBlocksMinedEvent && !BossbarManager.isActive -> BossbarManager.blocksMinedGlobally
            else -> 0L
        }

    fun getRequirementProgressTarget(): Long =
        when {
            BossbarManager.isPlayerCountEvent && !BossbarManager.isActive -> BossbarManager.requirementPlayers.toLong()
            BossbarManager.isBlocksMinedEvent && !BossbarManager.isActive -> BossbarManager.requirementBlocks
            else -> 0L
        }

    fun getRequirementTypeLabel(): String =
        when {
            BossbarManager.isActive -> "active"
            BossbarManager.isPlayerCountEvent -> "players"
            BossbarManager.isBlocksMinedEvent -> "blocks"
            else -> "idle"
        }
}
