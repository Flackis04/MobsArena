package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import kotlin.math.ceil

object CombatManager : Listener {
    private const val COMBAT_MILLIS = 15_000L
    private val combatUntil = mutableMapOf<UUID, Long>()
    private val lastAttacker = mutableMapOf<UUID, UUID>()

    fun init() {
        Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            val now = System.currentTimeMillis()
            DataStore.all().forEach { (_, data) ->
                data.victims.clear()
            }

            Bukkit.getOnlinePlayers().forEach { player ->
                val remaining = getRemainingMillis(player.uniqueId, now)
                if (remaining <= 0L) {
                    clearCombat(player)
                    return@forEach
                }

                val secondsLeft = ceil(remaining / 1000.0).toInt()
                player.sendActionBar(TextUtil.toComponent("&cCombat: ${secondsLeft}s"))
            }
        }, 20L, 20L)
    }

    fun tagPlayers(attacker: Player, victim: Player) {
        val expiresAt = System.currentTimeMillis() + COMBAT_MILLIS
        combatUntil[attacker.uniqueId] = expiresAt
        combatUntil[victim.uniqueId] = expiresAt
        lastAttacker[attacker.uniqueId] = victim.uniqueId
        lastAttacker[victim.uniqueId] = attacker.uniqueId
        disableFlight(attacker)
        disableFlight(victim)
    }

    fun clearCombat(player: Player) {
        combatUntil.remove(player.uniqueId)
        lastAttacker.remove(player.uniqueId)
        restoreFlight(player)
    }

    fun isInCombat(player: Player): Boolean = getRemainingMillis(player.uniqueId, System.currentTimeMillis()) > 0L

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!isInCombat(event.player)) return
        event.isCancelled = true
        val remaining = ceil(getRemainingMillis(event.player.uniqueId, System.currentTimeMillis()) / 1000.0).toInt()
        event.player.sendMessage(TextUtil.colorize("&cYou cannot use commands while in combat (&f${remaining}s&c)."))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val quitter = event.player
        val remaining = getRemainingMillis(quitter.uniqueId, System.currentTimeMillis())
        if (remaining <= 0L) {
            clearCombat(quitter)
            return
        }

        val attackerId = lastAttacker[quitter.uniqueId]
        val attacker = attackerId?.let(Bukkit::getPlayer)
        if (attacker != null && attacker.isOnline) {
            handlePlayerKill(attacker, quitter)
            attacker.sendMessage(TextUtil.colorize("&c${quitter.name} combat logged."))
            Bukkit.broadcast(TextUtil.toComponent("&c${quitter.name} &7combat logged against &c${attacker.name}&7."))
        }

        clearCombat(quitter)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        clearCombat(event.player)
    }

    private fun getRemainingMillis(playerId: UUID, now: Long): Long =
        (combatUntil[playerId] ?: 0L) - now

    private fun disableFlight(player: Player) {
        if (player.isFlying) {
            player.isFlying = false
        }
        player.allowFlight = false
    }

    private fun restoreFlight(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (data.flightUnlocked && data.flight) {
            player.allowFlight = true
        }
    }
}
