package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class AutoMinerBackpackGui {
    companion object {
        private const val COLLECT_ALL_SLOT = 4
        private val VALUABLE_SLOTS = listOf(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31
        )
        private val activeViews = mutableMapOf<UUID, Gui>()

        fun refreshIfOpen(player: Player) {
            val gui = activeViews[player.uniqueId] ?: return
            if (player.openInventory.topInventory.holder != gui) return
            render(player, gui)
            gui.update()
        }

        private fun render(player: Player, gui: Gui) {
            gui.inventory.clear()
            renderToolbar(player, gui)

            val storedItems = AutoMinerManager.getContents(player)
            for ((index, item) in storedItems.withIndex()) {
                if (index >= VALUABLE_SLOTS.size) break
                val slot = VALUABLE_SLOTS[index]
                val material = if (item.type == Material.AIR || item.amount <= 0) {
                    MineManager.valuableDrops[index]
                } else {
                    item.type
                }
                val display = if (item.type == Material.AIR || item.amount <= 0) {
                    createEmptyValuableItem(player, index)
                } else {
                    createDisplayItem(player, item)
                }
                gui.setItem(slot, GuiItem(display) { event ->
                    if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                    if (event.isShiftClick && event.isLeftClick) {
                        if (DataStore.get(player.uniqueId).getCollectedAmount(material) > 0L) {
                            MasteryManager.openValuableGui(player, material)
                        } else {
                            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                        }
                    }
                })
            }
        }

        private fun renderToolbar(player: Player, gui: Gui) {
            val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                editMeta { meta ->
                    meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
                }
            }

            for (slot in 0 until 9) {
                gui.setItem(slot, GuiItem(filler))
            }

            val preview = AutoMinerManager.getStoredItemCount(player)
            val capacity = AutoMinerManager.getBackpackCapacity(player)
            gui.setItem(COLLECT_ALL_SLOT, GuiItem(createToolbarItem(
                Material.BEACON,
                "&aCollect All",
                listOf(
                    "&7Transfer all autominer loot",
                    "&7into your main backpack.",
                    "",
                    "&7Stored items: &f${TextUtil.formatNum(preview)}",
                    "&7Capacity: &b${TextUtil.formatNum(capacity)}",
                    "&aClick to collect"
                )
            )) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                val result = AutoMinerManager.collectAll(player)
                if (result.totalItems <= 0L) {
                    player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                } else {
                    SessionTimelineManager.record(
                        player,
                        "Collected ${TextUtil.formatNum(result.totalItems)} items from autominer backpack (${result.payoutMultiplier}x payout)"
                    )
                    player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1f)
                    TextUtil.showTitle(
                        player,
                        if (result.payoutLucky) "&6Lucky Payout!" else "&7No Bonus",
                        if (result.payoutLucky) "&fAuto Miner payout doubled to &62x" else "&7Auto Miner payout stayed at &f1x",
                        10,
                        35,
                        10
                    )
                    player.sendMessage(
                        TextUtil.colorize(
                            "&aCollected &7x${TextUtil.formatNum(result.totalItems)} items &ainto your backpack${if (result.payoutLucky) " &6(2x Lucky Payout)" else ""}."
                        )
                    )
                }
                render(player, gui)
                gui.update()
            })
        }

        private fun createDisplayItem(player: Player, item: ItemStack): ItemStack {
            val discovered = DataStore.get(player.uniqueId).getCollectedAmount(item.type) > 0L
            return ItemStack(item.type, item.amount.coerceAtLeast(1)).apply {
                editMeta { meta ->
                    meta.displayName(MineManager.getValuableDisplayName(item.type, discovered))
                    meta.lore(
                        listOf(
                            TextUtil.toComponent("&7Quantity: &f${TextUtil.formatNum(item.amount.toLong())}").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&aGenerated by your autominer").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&7Shift-left-click: &dView mastery").decoration(TextDecoration.ITALIC, false)
                        )
                    )
                }
            }
        }

        private fun createEmptyValuableItem(player: Player, index: Int): ItemStack {
            val material = MineManager.valuableDrops[index]
            val discovered = DataStore.get(player.uniqueId).getCollectedAmount(material) > 0L
            return ItemStack(if (discovered) Material.STONE_BUTTON else Material.POLISHED_BLACKSTONE_BUTTON).apply {
                editMeta { meta ->
                    meta.displayName(MineManager.getValuableDisplayName(material, discovered))
                    meta.lore(
                        if (discovered) {
                            listOf(
                                TextUtil.toComponent("&7Quantity: &f0").decoration(TextDecoration.ITALIC, false),
                                TextUtil.toComponent("&7Waiting for your autominer...").decoration(TextDecoration.ITALIC, false),
                                TextUtil.toComponent("&7Shift-left-click: &dView mastery").decoration(TextDecoration.ITALIC, false)
                            )
                        } else {
                            listOf(
                                TextUtil.toComponent("&7Not discovered yet").decoration(TextDecoration.ITALIC, false)
                            )
                        }
                    )
                }
            }
        }

        private fun createToolbarItem(material: Material, name: String, lore: List<String>): ItemStack {
            return ItemStack(material).apply {
                editMeta { meta ->
                    meta.displayName(TextUtil.toComponent(name).decoration(TextDecoration.ITALIC, false))
                    meta.lore(lore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
                }
            }
        }
    }

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8AutoMiner Backpack"))
            .rows(6)
            .disableAllInteractions()
            .create()

        activeViews[player.uniqueId] = gui
        AutoMinerManager.openBackpack(player)
        gui.setCloseGuiAction {
            activeViews.remove(player.uniqueId)
            AutoMinerManager.closeBackpack(player)
            GuiClickDebounce.clear(player)
        }
        render(player, gui)
        gui.open(player)
    }
}
