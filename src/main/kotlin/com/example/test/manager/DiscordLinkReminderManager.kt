package com.example.test

import org.bukkit.Bukkit

object DiscordLinkReminderManager {
    private const val REMINDER_INTERVAL_TICKS = 20L * 60L

    fun init() {
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { remindUnlinkedPlayers() },
            REMINDER_INTERVAL_TICKS,
            REMINDER_INTERVAL_TICKS
        )
    }

    private fun remindUnlinkedPlayers() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val data = DataStore.get(player.uniqueId)
            if (data.hasLinkedDiscord||data.playtimeSeconds > 60*60*2) return@forEach

            player.sendMessage(TextUtil.colorize(" &9&m                               "))
            player.sendMessage("")
            player.sendMessage(TextUtil.colorize("    &9&l★ LINK YOUR DISCORD ★"))
            player.sendMessage("")
            player.sendMessage(TextUtil.colorize("    &7Link your Discord account to get"))
            player.sendMessage(TextUtil.colorize("    &a/togglefly command"))
            player.sendMessage("")
            player.sendMessage(TextUtil.colorize("    &7Type &b/link &7to get started."))
            player.sendMessage("")
            player.sendMessage(TextUtil.colorize(" &9&m                               "))
        }
    }
}
