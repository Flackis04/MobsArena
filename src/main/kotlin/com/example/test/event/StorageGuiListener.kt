package com.example.test

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class StorageGuiListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!isTriumphGuiOpen(event.view.topInventory.holder)) return
        if (event.rawSlot < event.view.topInventory.size) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (!isTriumphGuiOpen(event.view.topInventory.holder)) return
        if (event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    private fun isTriumphGuiOpen(holder: Any?): Boolean =
        holder?.javaClass?.name?.startsWith("dev.triumphteam.gui") == true
}
