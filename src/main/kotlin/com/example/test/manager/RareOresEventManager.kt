package com.example.test

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

object RareOresEventManager {
    private var active = false
    private var valuableWeightMultiplier = 1.0
    private var endTask: BukkitTask? = null

    fun init() {
        shutdown()
    }

    fun shutdown() {
        endTask?.cancel()
        endTask = null
        active = false
        valuableWeightMultiplier = 1.0
    }

    fun canStart(): Boolean = !active

    fun isActive(): Boolean = active

    fun getValuableWeightMultiplier(): Double = valuableWeightMultiplier

    fun cancelCurrentEvent() {
        if (!active) return
        active = false
        valuableWeightMultiplier = 1.0
        endTask?.cancel()
        endTask = null
        MineManager.endRareOresReset()
    }

    fun startFromEventManager(): Boolean {
        if (!canStart()) return false

        active = true
        valuableWeightMultiplier = BossbarManager.weightMultiplier
        val durationTicks = (BossbarManager.durationSeconds * 20L).coerceAtLeast(1L)
        MineManager.startRareOresReset(
            durationTicks = durationTicks,
            valuableWeightMultiplier = valuableWeightMultiplier
        )
        scheduleEventEnd(durationTicks)
        return true
    }

    fun restoreActiveEvent(remainingTicks: Long, weightMultiplier: Double): Boolean {
        if (!canStart()) return false

        active = true
        valuableWeightMultiplier = weightMultiplier
        MineManager.startRareOresReset(
            durationTicks = remainingTicks,
            valuableWeightMultiplier = valuableWeightMultiplier
        )
        scheduleEventEnd(remainingTicks)
        return true
    }

    private fun scheduleEventEnd(durationTicks: Long) {
        endTask?.cancel()
        endTask = Bukkit.getScheduler().runTaskLater(
            TestPlugin.instance,
            Runnable { endEvent() },
            durationTicks
        )
    }

    private fun endEvent() {
        if (!active) return

        active = false
        valuableWeightMultiplier = 1.0
        endTask?.cancel()
        endTask = null

        MineManager.endRareOresReset()
        BossbarManager.resetEvent()
    }
}
