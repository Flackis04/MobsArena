package com.example.test

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

object RareOresEventManager {
    private const val EVENT_DURATION_TICKS = 20L * 60L

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

    fun restartActiveTimer() {
        if (!active) return
        scheduleEventEnd()
        MineManager.setResetCountdownTicks(EVENT_DURATION_TICKS)
    }

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
        MineManager.startRareOresReset(
            durationTicks = EVENT_DURATION_TICKS,
            valuableWeightMultiplier = valuableWeightMultiplier
        )
        scheduleEventEnd()
        return true
    }

    private fun scheduleEventEnd() {
        endTask?.cancel()
        endTask = Bukkit.getScheduler().runTaskLater(
            TestPlugin.instance,
            Runnable { endEvent() },
            EVENT_DURATION_TICKS
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
