package com.example.test

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class ChatListener : Listener {
    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        event.isCancelled = true
        val data = DataStore.get(player.uniqueId)
        val tierColor = TierManager.getTier(data.rank)?.color ?: "&f"
        val message = LegacyComponentSerializer.legacySection().serialize(event.message())
        WelcomeRewardManager.tryRewardGreeter(player, message)
        val prefix = getLuckPermsPrefix(player)
        val formatted = "$prefix${tierColor}&l${formatDisplayedRank(data)} &f${player.name}&8 >> &r&f${message}"
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            player.world.players.forEach { it.sendMessage(TextUtil.colorize(formatted)) }
        })
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val msg = event.deathMessage() ?: return
        val legacy = LegacyComponentSerializer.legacySection().serialize(msg)
        event.deathMessage(TextUtil.toComponent("<#fc1919>$legacy"))
    }

    private fun getLuckPermsPrefix(player: Player): String {
        return runCatching {
            val metaData = LuckPermsProvider.get().getPlayerAdapter(Player::class.java).getMetaData(player)
            val rawPrefix = metaData.prefix ?: return ""
            val cleanedPrefix = rawPrefix.replace(Regex("^prefix\\.\\d+\\."), "")
            if (cleanedPrefix.isBlank()) "" else "$cleanedPrefix "
        }.getOrDefault("")
    }

    private fun formatDisplayedRank(data: PlayerData): String {
        if (data.rebirth <= 0) return data.rank.toString()
        val rebirthLetter = ('A'.code + ((data.rebirth - 1) % 26)).toChar()
        return "$rebirthLetter${data.rank}"
    }
}
