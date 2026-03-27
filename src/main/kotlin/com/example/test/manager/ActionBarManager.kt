package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object ActionBarManager {
    private val tasks = mutableMapOf<UUID, BukkitTask>()

    fun sendActionBarFor(player: Player, seconds: Double, message: String) {
        tasks.remove(player.uniqueId)?.cancel()
        val ticksTotal = (seconds * 20).toInt()
        var ticks = 0
        val task = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            if (ticks >= ticksTotal || !player.isOnline) {
                tasks.remove(player.uniqueId)?.cancel()
                return@Runnable
            }
            player.sendActionBar(TextUtil.toComponent(message))
            ticks += 2
        }, 0L, 2L)
        tasks[player.uniqueId] = task
    }
}
