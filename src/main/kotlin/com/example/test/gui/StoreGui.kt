package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class StoreGui : Listener {
    private data class StorePackage(
        val displayName: String,
        val material: Material,
        val lore: List<String>,
        val storeUrl: String
    )

    private val packages = listOf(
        StorePackage(
            displayName = "&8[&aMiner&8]",
            material = Material.NETHERITE_PICKAXE,
            lore = listOf(
                "&7Entry premium rank for faster progression.",
                "",
                "&fPrice: &a$4.99",
                "&fWhat you get",
                "&cSTEAL &bfrom others",
                "&b+10% proc chance on proc upgrades",
                "&b+1.0x permanent multiplier",
                "",
                "&aBest for players who want strong value early.",
            ),
            storeUrl = "https://mobs-arena.tebex.io/package/7179746"
        ),
        StorePackage(
            displayName = "&8[&eExcavator&8]",
            material = Material.BEACON,
            lore = listOf(
                "&7Mid-tier rank focused on mine power and proc consistency.",
                "",
                "&fPrice: &a$19.99",
                "&fWhat you get",
                "&cSTEAL &bfrom others",
                "&b+1.5x mine richness",
                "&b+25% proc chance on proc upgrades",
                "&b+2.5x permanent multiplier",
                "&b+2x XP gain",
                "",
                "&eBuilt for players who want noticeably faster mining.",
            ),
            storeUrl = "https://mobs-arena.tebex.io/package/7352461"
        ),
        StorePackage(
            displayName = "&8[&6Nuker&8]",
            material = Material.TNT,
            lore = listOf(
                "&7Top-tier rank with the strongest mining bonuses.",
                "",
                "&fPrice: &a$49.99",
                "&fWhat you get",
                "&cSTEAL &bfrom others",
                "&b+2.5x mine richness",
                "&b+50% proc chance on proc upgrades",
                "&b+5.0x permanent multiplier",
                "&b+4x XP gain",
                "",
                "&6For players who want the highest-end boost package.",
            ),
            storeUrl = "https://mobs-arena.tebex.io/package/7352466"
        )
    )

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Store"))
            .rows(3)
            .disableAllInteractions()
            .create()

        listOf(11, 13, 15).forEachIndexed { index, slot ->
            val storePackage = packages[index]
            gui.setItem(slot, GuiItem(createPackageItem(storePackage)) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                sendStorePrompt(player, storePackage.storeUrl)
            })
        }

        gui.setCloseGuiAction {
            GuiClickDebounce.clear(player)
        }
        gui.open(player)
    }

    private fun createPackageItem(storePackage: StorePackage): ItemStack {
        val item = ItemStack(storePackage.material)
        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(storePackage.displayName).decoration(TextDecoration.ITALIC, false))
            meta.lore(
                (formatPackageLore(storePackage.lore) + "&aClick to continue")
                    .map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
            )
        }
        return item
    }

    private fun formatPackageLore(lines: List<String>): List<String> {
        var bulletMode = false
        return lines.map { line ->
            when {
                line == "&7Includes:" -> {
                    bulletMode = true
                    line
                }
                bulletMode && line.isBlank() -> {
                    bulletMode = false
                    line
                }
                bulletMode -> "&8• $line"
                else -> line
            }
        }
    }

    private fun sendStorePrompt(player: Player, storeUrl: String) {
        val clickEvent = ClickEvent.openUrl(storeUrl)
        val hoverEvent = HoverEvent.showText(Component.text(storeUrl, NamedTextColor.GREEN))

        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text("Your package is ready for purchase", NamedTextColor.GREEN)
                .clickEvent(clickEvent)
                .hoverEvent(hoverEvent)
                .decoration(TextDecoration.BOLD, true)
        )
        player.sendMessage(
            Component.text("Click here to continue to our store", NamedTextColor.GRAY)
                .clickEvent(clickEvent)
                .hoverEvent(hoverEvent)
                .decoration(TextDecoration.ITALIC, false)
        )
        player.sendMessage(Component.empty())
    }
}
