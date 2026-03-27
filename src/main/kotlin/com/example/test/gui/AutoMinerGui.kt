package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class AutoMinerGui : Listener {

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(Component.text("Auto Miner"))
            .rows(3)
            .disableAllInteractions()
            .create()

        gui.setItem(11, GuiItem(createInfoItem(
            Material.HOPPER,
            "&7&lAuto Miner",
            listOf(
                "&7Generates random mine valuables every second.",
                "&7Loot waits inside the autominer backpack.",
                "&7Online uses full efficiency.",
                "&7Offline uses Energy Drink %."
            )
        )))

        gui.setItem(13, GuiItem(createInfoItem(
            Material.CHEST,
            "&e&lBackpack",
            listOf(
                "&7Open the autominer backpack.",
                "&7Collect generated valuables into your backpack.",
                "&aClick to open"
            )
        )) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            AutoMinerBackpackGui().open(player)
        })

        gui.setItem(15, GuiItem(createInfoItem(
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            "&b&lUpgrades",
            listOf(
                "&7Fortune, efficiency, luck, energy drink,",
                "&7and backpack capacity.",
                "&aClick to open"
            )
        )) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            openAutoMinerUpgradeGui(player)
        })

        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }
        gui.open(player)
    }

    private fun createInfoItem(material: Material, title: String, lore: List<String>): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent(title).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
            }
        }
    }
}
