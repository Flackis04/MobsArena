package com.example.test
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent

class DropListener : Listener {

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack

        if (
            ItemManager.isPickaxe(item) ||
            ItemManager.isBackpack(item) ||
            (KitManager.isMineMode(event.player.location) && KitManager.isRankArmorPiece(item))
        ) {
            event.isCancelled = true
        }
    }
}
