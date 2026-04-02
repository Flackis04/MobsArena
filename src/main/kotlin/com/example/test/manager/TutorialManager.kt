package com.example.test

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType

object TutorialManager {
    private data class PendingTitle(
        val title: String,
        val subtitle: String,
        val fadeIn: Int,
        val stay: Int,
        val fadeOut: Int
    )

    data class TutorialBossbar(
        val title: String,
        val progress: Double,
        val color: BarColor,
        val style: BarStyle
    )

    private val pendingTitles = mutableMapOf<java.util.UUID, PendingTitle>()

    fun isRunning(data: PlayerData): Boolean =
        data.tutorialActive

    fun newPlayer(player: Player) {
        val data = DataStore.get(player.uniqueId)
        Bukkit.broadcast(TextUtil.toComponent("&d A new player joined! Welcome ${player.name} to the server!"))
        WelcomeRewardManager.registerNewPlayer(player)
        MineManager.ensureMineFor(player, teleportToMineTop = false)
        KitManager.giveStarterSpawnLoadout(player)
        KitManager.giveDynamite(player, 5)
        KitManager.giveChargedDynamite(player, 1)
        data.newPlayer = false
        data.hasReceivedJoinLoadout = true
        data.hasSeenMineHelp = false
        data.hasBroken = false
        data.hasSold = false
        data.hasSeenUpgradeHint = false
        data.valuableBlocksBroken = 0
        data.hasSeenBackpackSellHint = false
        data.tutorialActive = true
        data.tutorialPendingUpgradeClose = false
        DataStore.save()
        player.teleport(MineManager.getSpawnLocation())
        showOrQueueTitle(player, "&aWelcome", "&7Mine is generating.", 10, 50, 10)
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            val mineLocation = MineManager.getPlayerMineCenterLocation(player)?.add(0.0, 1.0, 0.0)
                ?: MineManager.getSpawnLocation()
            player.teleport(mineLocation)
            showOrQueueTitle(player, "&aMine Ready", "&7Mine 5 ores.", 10, 40, 10)
            BossbarManager.refreshPlayer(player)
        }, 70L)
    }

    fun handleValuableBreak(player: Player) {
        val data = DataStore.get(player.uniqueId)
        data.valuableBlocksBroken += 1
        if (data.tutorialActive && !data.hasBroken) {
            BossbarManager.refreshPlayer(player)
        }

        if (data.tutorialActive && !data.hasBroken && data.valuableBlocksBroken >= 5) {
            data.hasBroken = true
            showOrQueueTitle(player, "&6Sell Backpack", "&7Open backpack and sell.", 10, 35, 10)
            BossbarManager.refreshPlayer(player)
        }

        if (!data.hasSold && !data.hasSeenBackpackSellHint && data.valuableBlocksBroken >= 5) {
            data.hasSeenBackpackSellHint = true
        }
    }

    fun handleBackpackSold(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (data.hasSold) return
        if (!data.tutorialActive || !data.hasBroken) return
        data.hasSold = true
        showOrQueueTitle(player, "&dUpgrade Multi Break", "&7Shift-Right-Click your pickaxe to open upgrades.", 10, 35, 10)
        BossbarManager.refreshPlayer(player)
    }

    fun handleMultiBreakPurchased(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (!data.tutorialActive || !data.hasSold) return
        data.tutorialPendingUpgradeClose = true
    }

    fun handleUpgradeGuiClosed(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (!data.tutorialActive || !data.tutorialPendingUpgradeClose) return
        data.tutorialPendingUpgradeClose = false
        data.hasSeenUpgradeHint = true
        data.tutorialActive = false
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            TextUtil.showTitle(player, "&bNeed Help?", "&7Check features in &f/spawn&7.", 10, 40, 10)
        }, 1L)
        BossbarManager.refreshPlayer(player)
    }

    fun handleInventoryClosed(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (data.tutorialActive && data.hasBroken && data.hasSold && !data.hasSeenUpgradeHint && !data.tutorialPendingUpgradeClose) {
            pendingTitles.remove(player.uniqueId)
            Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
                if (!player.isOnline) return@Runnable
                showOrQueueTitle(
                    player,
                    "&dUpgrade Multi Break",
                    "&7Shift-Right-Click your pickaxe to open upgrades.",
                    10,
                    35,
                    10
                )
            }, 1L)
            return
        }

        val pending = pendingTitles.remove(player.uniqueId) ?: return
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            TextUtil.showTitle(player, pending.title, pending.subtitle, pending.fadeIn, pending.stay, pending.fadeOut)
        }, 1L)
    }

    fun isTutorialMode(player: Player): Boolean =
        DataStore.get(player.uniqueId).tutorialActive

    fun hasFinishedTutorial(playerId: java.util.UUID): Boolean {
        val data = DataStore.get(playerId)
        return !data.tutorialActive && data.hasSeenUpgradeHint
    }

    fun getBossbar(player: Player): TutorialBossbar? {
        val data = DataStore.get(player.uniqueId)
        if (!data.tutorialActive) return null
        return when {
            !data.hasBroken -> TutorialBossbar(
                title = "&aTutorial 1/3 Mine 5 Ores &7- &f${data.valuableBlocksBroken.coerceAtMost(5)}/5",
                progress = (data.valuableBlocksBroken.toDouble() / 5.0).coerceIn(0.01, 1.0),
                color = BarColor.GREEN,
                style = BarStyle.SEGMENTED_10
            )
            !data.hasSold -> TutorialBossbar(
                title = "&6Tutorial 2/3 Sell Backpack &7- &fOpen backpack",
                progress = 0.5,
                color = BarColor.YELLOW,
                style = BarStyle.SOLID
            )
            else -> TutorialBossbar(
                title = "&dTutorial 3/3 Upgrade Multi Break &7- &fOpen upgrades",
                progress = 0.85,
                color = BarColor.PURPLE,
                style = BarStyle.SOLID
            )
        }
    }

    fun showMineHelpOnce(player: Player) {
        val data = DataStore.get(player.uniqueId)
        if (data.hasSeenMineHelp) return
        data.hasSeenMineHelp = true
    }

    private fun showOrQueueTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        if (player.openInventory.topInventory.type != InventoryType.CRAFTING) {
            pendingTitles[player.uniqueId] = PendingTitle(title, subtitle, fadeIn, stay, fadeOut)
            return
        }
        TextUtil.showTitle(player, title, subtitle, fadeIn, stay, fadeOut)
    }
}
