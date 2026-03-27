package com.example.test

import org.bukkit.entity.Player
import java.util.UUID

object GuiClickDebounce {
    private const val DEBOUNCE_MS = 100L
    private val lastClickAt = mutableMapOf<UUID, Long>()

    fun tryAcquire(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastClickAt[player.uniqueId] ?: 0L
        if (now - previous < DEBOUNCE_MS) return false
        lastClickAt[player.uniqueId] = now
        return true
    }

    fun clear(player: Player) {
        lastClickAt.remove(player.uniqueId)
    }
}
