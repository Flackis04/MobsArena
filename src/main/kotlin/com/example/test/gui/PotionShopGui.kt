package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class PotionShopGui : Listener {
    private val offers = listOf(
        Triple(10, PotionsManager.BuffType.MINE_RICHNESS, Material.EMERALD),
        Triple(12, PotionsManager.BuffType.XP, Material.EXPERIENCE_BOTTLE),
        Triple(14, PotionsManager.BuffType.PROC, Material.NETHER_STAR),
        Triple(16, PotionsManager.BuffType.FORTUNE, Material.DIAMOND)
    )

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Potion Shop"))
            .rows(3)
            .disableAllInteractions()
            .create()

        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }
        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }

        gui.setItem(4, GuiItem(createHeader(player)))
        offers.forEach { (slot, buffType, material) ->
            gui.setItem(slot, GuiItem(createOffer(player, buffType, material)) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                if (PotionsManager.buyBuffItem(player, buffType)) {
                    open(player)
                }
            })
        }

        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }
        gui.open(player)
    }

    private fun createHeader(player: Player): ItemStack =
        ItemStack(Material.BREWING_STAND).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&f&lPotion Shop").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Buy timed buffs with tokens.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Your tokens: &b${TextUtil.formatNum(DataStore.get(player.uniqueId).tokens)}").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun createOffer(player: Player, buffType: PotionsManager.BuffType, material: Material): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val multiplierText = when (buffType) {
            PotionsManager.BuffType.MINE_RICHNESS -> "&bMine richness: &f${String.format("%.2f", buffType.multiplier)}x"
            PotionsManager.BuffType.XP -> "&aXP gain: &f${String.format("%.2f", buffType.multiplier)}x"
            PotionsManager.BuffType.PROC -> "&dProc chance: &f+${String.format("%.0f", (buffType.multiplier - 1.0) * 100)}%"
            PotionsManager.BuffType.FORTUNE -> "&6Fortune payout: &f${String.format("%.2f", buffType.multiplier)}x"
        }
        val remaining = PotionsManager.getRemainingMillis(data, buffType)
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&e${buffType.displayName}").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Duration: &f${buffType.durationMinutes} minutes").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent(multiplierText).decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Cost: &b${TextUtil.formatNum(buffType.tokenCost)} tokens").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Active: &f${if (remaining > 0L) PotionsManager.formatDuration(remaining) else "Inactive"}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&eClick to buy potion item").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }
}
