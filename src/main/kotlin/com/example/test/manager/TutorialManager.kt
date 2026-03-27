package com.example.test

import org.bukkit.Bukkit
import org.bukkit.entity.Player

object TutorialManager {
    fun isRunning(data: PlayerData): Boolean =
        !data.hasTouched || !data.hasBroken || !data.hasSeenUpgradeHint

    fun newPlayer(player: Player) {
        val data = DataStore.get(player.uniqueId)
        Bukkit.broadcast(TextUtil.toComponent("&d A new player joined! Welcome ${player.name} to the server!"))
        WelcomeRewardManager.registerNewPlayer(player)
        MineManager.ensureMineFor(player, teleportToMineTop = false)
        KitManager.giveStarterSpawnLoadout(player)
        KitManager.giveDynamite(player, 3)
        data.newPlayer = false
        data.hasReceivedJoinLoadout = true
        data.hasSeenMineHelp = false
        DataStore.save()
        player.teleport(MineManager.getSpawnLocation())
        player.sendTitle(
            TextUtil.colorize("&aWelcome to Mobs Arena"),
            TextUtil.colorize("&7Mine, upgrade, and build your fortune."),
            10,
            60,
            10
        )
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            if (MineManager.containsMineArea(player.location)) return@Runnable
            player.sendTitle(
                TextUtil.colorize("&ePress the Big NPC"),
                TextUtil.colorize("&7Claim your mine and start earning."),
                10,
                50,
                10
            )
        }, 100L)
        data.noobProtection = false
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            data.noobProtection = false
        }, 20L * 60L * 5L)
    }

    fun handleValuableBreak(player: Player) {
        val data = DataStore.get(player.uniqueId)
        data.valuableBlocksBroken += 1

        if (!data.hasBroken && data.valuableBlocksBroken >= 5) {
            player.sendTitle(
                TextUtil.colorize("&aNice start!"),
                TextUtil.colorize("&7Sell your ores by &fRight-Clicking &7your &6Backpack&7."),
                10,
                60,
                10
            )
            player.playSound(
                player.location,
                SoundManager.EXPERIENCE_PICKUP_SOUND,
                SoundManager.DEFAULT_VOLUME,
                SoundManager.FIRST_VALUABLE_BREAK_PITCH
            )
            data.hasBroken = true
        }

        if (!data.hasSold && !data.hasSeenBackpackSellHint && data.valuableBlocksBroken >= 5) {
            data.hasSeenBackpackSellHint = true
        }
    }

    fun showMineHelpOnce(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (data.hasSeenMineHelp) return
        data.hasSeenMineHelp = true
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            player.sendTitle(
                TextUtil.colorize("&bMine ores, rank up, get rich"),
                TextUtil.colorize("&7Everything starts in your personal mine."),
                10,
                70,
                10
            )
        }, 20L)
    }
}
