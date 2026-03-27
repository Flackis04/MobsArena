package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class OreIndexGui : Listener {

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(Component.text("Ore Index"))
            .rows(6)
            .disableAllInteractions()
            .create()

        setValuableSlot(player, gui, 10, Material.RAW_GOLD, "<#FFD700>")
        setValuableSlot(player, gui, 11, Material.DIAMOND, "<#00FFFF>")
        setValuableSlot(player, gui, 12, Material.EMERALD, "<#50C878>")
        setValuableSlot(player, gui, 13, Material.AMETHYST_BLOCK, "<#A06CD5>")
        setValuableSlot(player, gui, 14, Material.GOLD_BLOCK, "<#FFD700>")
        setValuableSlot(player, gui, 15, Material.REDSTONE_BLOCK, "<#FF3030>")
        setValuableSlot(player, gui, 16, Material.LAPIS_BLOCK, "<#4D8DFF>")
        setValuableSlot(player, gui, 19, Material.DIAMOND_BLOCK, "<#00FFFF>")
        setValuableSlot(player, gui, 20, Material.EMERALD_BLOCK, "<#50C878>")
        setValuableSlot(player, gui, 21, Material.ANCIENT_DEBRIS, "<#5A3A2E>")
        setValuableSlot(player, gui, 22, Material.NETHERITE_BLOCK, "<#4C515A>")
        setValuableSlot(player, gui, 23, Material.OBSIDIAN, "<#551A8B>")
        setValuableSlot(player, gui, 24, Material.CRYING_OBSIDIAN, "<#FF00FF>")
        setValuableSlot(player, gui, 25, Material.MAGMA_BLOCK, "<#FF6A00>")
        setValuableSlot(player, gui, 28, Material.RESPAWN_ANCHOR, "<#FFFF66>")
        setValuableSlot(player, gui, 29, Material.SCULK, "<#4A6A5A>")
        setValuableSlot(player, gui, 30, Material.SCULK_CATALYST, "<#6EC6FF>")
        setValuableSlot(player, gui, 31, Material.BEACON, "<#87F5FF>")
        setValuableSlot(player, gui, 32, Material.OCHRE_FROGLIGHT, "<#D89A4A>")
        setValuableSlot(player, gui, 33, Material.VERDANT_FROGLIGHT, "<#6FD66F>")
        setValuableSlot(player, gui, 34, Material.PEARLESCENT_FROGLIGHT, "<#FFB7FF>")

        gui.open(player)
    }

    private fun setValuableSlot(player: Player, gui: Gui, slot: Int, item: Material, color: String) {
        val data = DataStore.get(player.uniqueId)
        val totalCollected = data.getCollectedAmount(item)
        val masteryLevel = if (totalCollected > 0L) MasteryManager.getValuableMasteryLevel(data, item) else 0
        val sellValue = if (totalCollected > 0L) MasteryManager.getValuableSellValue(data, item) else StorageManager.getBaseSellValue(item) ?: 0L
        val nextMasteryRequirement = if (masteryLevel < 7) {
            MasteryManager.getRequiredValuableBlocksMined(item, masteryLevel + 1)
        } else {
            0L
        }
        val maxMasteryRequirement = MasteryManager.getRequiredValuableBlocksMined(item, 7)
        val stack = if (totalCollected > 0L) ItemStack(item) else ItemStack(Material.STONE_BUTTON)
        stack.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent("${color}&l${item.name.replace('_', ' ')}")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false)
            )
            val lore = mutableListOf(
                TextUtil.toComponent("&7Collected Ever: &b${TextUtil.formatNum(totalCollected)}")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false),
                TextUtil.toComponent("&7Sell Value: &a${TextUtil.formatNum(sellValue)} ${ItemManager.COIN_NAME_PLURAL}")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false),
                TextUtil.toComponent("&7Mastery 7 requirement: &e${TextUtil.formatNum(maxMasteryRequirement)}")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false)
            )
            if (totalCollected > 0L) {
                lore += TextUtil.toComponent("&7Mastery Level: &d${masteryLevel}/7")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false)
                if (masteryLevel < 7) {
                    lore += TextUtil.toComponent("&7Next mastery: &b${TextUtil.formatNum(totalCollected)}&7/&b${TextUtil.formatNum(nextMasteryRequirement)}")
                        .asComponent()
                        .decoration(TextDecoration.ITALIC, false)
                }
                lore += TextUtil.toComponent("&7Shift-left-click: &dView mastery")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false)
            } else {
                lore += TextUtil.toComponent("&7Mine this valuable once to unlock mastery")
                    .asComponent()
                    .decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(lore)
        }

        gui.setItem(slot, GuiItem(stack) { event ->
            event.isCancelled = true
            if (totalCollected > 0L && event.isShiftClick && event.isLeftClick) {
                MasteryManager.openValuableGui(player, item)
            }
        })
    }
}
