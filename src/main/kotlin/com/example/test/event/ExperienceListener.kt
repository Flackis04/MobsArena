package com.example.test

import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerRespawnEvent

class ExperienceListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        ExperienceManager.clamp(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        ExperienceManager.clamp(event.player)
    }

    @EventHandler
    fun onExperienceChange(event: PlayerExpChangeEvent) {
        if (event.amount < 0) {
            event.amount = 0
        }
    }

    @EventHandler
    fun onLevelChange(event: PlayerLevelChangeEvent) {
        val player = event.player
        val data = DataStore.get(player.uniqueId)
        data.level = event.newLevel

        ScoreboardManager.updateBoard(player)
        KitManager.refreshPickaxe(player)

        if (event.newLevel <= event.oldLevel) return

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f)

        val efficiencyLevel = ItemManager.getPickaxeEfficiencyLevel(event.newLevel, data.rebirth)
        if (event.newLevel % 8 == 0) {
            if (TutorialManager.isRunning(data)) return
            player.sendTitle(
                TextUtil.colorize("&b&lEfficiency Upgraded"),
                TextUtil.colorize("&7Your pickaxe is now &bEfficiency $efficiencyLevel"),
                10,
                50,
                10
            )
            return
        }

        val levelsLeft = 8 - (event.newLevel % 8)
        val levelLabel = if (levelsLeft == 1) "level" else "levels"
        TextUtil.sendActionBarFor(
            player,
            2.5,
            "&7$levelsLeft $levelLabel left for &bEfficiency ${efficiencyLevel+1}")
    }
}
