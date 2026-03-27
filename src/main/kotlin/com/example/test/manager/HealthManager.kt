package com.example.test

import org.bukkit.entity.Player
import kotlin.math.min

object HealthManager {
    fun maxHeartsForLevel(level: Int): Int {
        val hearts = 10 + (level - 1)
        return min(20, hearts)
    }

    fun maxHealthForLevel(level: Int): Double = maxHeartsForLevel(level) * 2.0

    fun apply(player: Player, level: Int) {
        val value = maxHealthForLevel(level)
        player.maxHealth = value
        if (player.health > value) player.health = value
    }
}
