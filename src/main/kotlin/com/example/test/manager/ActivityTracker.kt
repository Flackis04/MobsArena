package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ActivityTracker : Listener {
    private const val AFK_TIMEOUT_MILLIS = 2 * 60 * 1000L
    private const val HOURLY_REWARD_TICKS = 20L * 60L * 60L
    private const val HOURLY_DYNAMITE_REWARD = 64

    private val lastActiveAt = ConcurrentHashMap<UUID, Long>()
    private val hourlyMineActivity = ConcurrentHashMap<UUID, Long>()

    fun init() {
        val now = System.currentTimeMillis()
        Bukkit.getOnlinePlayers().forEach { lastActiveAt[it.uniqueId] = now }
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { rewardTopMiner() },
            HOURLY_REWARD_TICKS,
            HOURLY_REWARD_TICKS
        )
    }

    fun markActive(player: Player) {
        lastActiveAt[player.uniqueId] = System.currentTimeMillis()
    }

    fun isAfk(player: Player): Boolean {
        val lastSeen = lastActiveAt[player.uniqueId] ?: return false
        return System.currentTimeMillis() - lastSeen > AFK_TIMEOUT_MILLIS
    }

    fun getActivePlayers(): List<Player> = Bukkit.getOnlinePlayers().filterNot(::isAfk)

    private fun addMineActivity(player: Player, amount: Long) {
        if (amount <= 0L || !MineManager.containsMine(player.location)) return
        hourlyMineActivity.merge(player.uniqueId, amount, Long::plus)
    }

    private fun rewardTopMiner() {
        val winnerEntry = hourlyMineActivity
            .asSequence()
            .mapNotNull { (uuid, score) ->
                val player = Bukkit.getPlayer(uuid) ?: return@mapNotNull null
                if (score <= 0L) return@mapNotNull null
                player to score
            }
            .maxByOrNull { it.second }

        hourlyMineActivity.clear()

        val (winner, _) = winnerEntry ?: return
        KitManager.giveDynamite(winner, HOURLY_DYNAMITE_REWARD)
        TextUtil.showTitle(winner, "&cMost Active Miner", "&a+64 Dynamite", 10, 70, 20)
        winner.sendMessage(TextUtil.colorize("&aYou were the most active player in the mine this hour and received &cx64 Dynamite&a."))
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        markActive(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastActiveAt.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to
        val from = event.from
        if (to.blockX == from.blockX && to.blockY == from.blockY && to.blockZ == from.blockZ) return
        markActive(event.player)
        addMineActivity(event.player, 1L)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        markActive(event.player)
        addMineActivity(event.player, 1L)
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        lastActiveAt[event.player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        markActive(event.player)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        markActive(event.player)
        addMineActivity(event.player, 5L)
    }
}
