package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class StorageGui(
    private val player: Player,
    val page: Int = 0
) {

    companion object {
        const val SELL_ALL_SLOT = 4
        private const val ACTIVE_MULTIPLIERS_SLOT = 8
        private val VALUABLE_SLOTS = listOf(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29
        )
    }

    private val gui: Gui = Gui.gui()
        .title(TextUtil.toComponent(storageTitle()))
        .rows(6)
        .disableAllInteractions()
        .create()

    init {
        gui.setPlayerInventoryAction { event ->
            if (!GuiClickDebounce.tryAcquire(player)) {
                event.isCancelled = true
                return@setPlayerInventoryAction
            }
            val clickedItem = event.currentItem ?: return@setPlayerInventoryAction
            if (!event.isShiftClick || !isStorable(clickedItem)) return@setPlayerInventoryAction

            val storedAmount = StorageManager.addDrop(player, clickedItem.clone())
            event.isCancelled = true
            if (storedAmount <= 0) return@setPlayerInventoryAction

            if (storedAmount >= clickedItem.amount) {
                event.clickedInventory?.setItem(event.slot, null)
            } else {
                clickedItem.amount -= storedAmount
                event.clickedInventory?.setItem(event.slot, clickedItem)
            }
            render()
        }

        gui.setDragAction { event ->
            if (event.rawSlots.any { it < gui.inventory.size }) {
                event.isCancelled = true
                render()
            }
        }

        gui.setCloseGuiAction {
            GuiClickDebounce.clear(player)
            val data = DataStore.get(player.uniqueId)
            if (!data.hasClosed && data.hasSold) {
                data.hasClosed = true
                player.sendTitle(
                    TextUtil.colorize("&aReady for stronger upgrades?"),
                    TextUtil.colorize("&fShift-right-click &7your pickaxe to open &a/upgrades&7."),
                    10,
                    90,
                    10
                )
            }
        }

        render()
        gui.open(player)
    }

    fun render() {
        gui.inventory.clear()
        renderToolbar()

        val storedItems = StorageManager.getContents(player)
        for ((index, item) in storedItems.withIndex()) {
            if (index >= VALUABLE_SLOTS.size) break
            val slot = VALUABLE_SLOTS[index]
            val sellMaterial = if (item.type == Material.AIR || item.amount <= 0) {
                MineManager.valuableDrops[index]
            } else {
                item.type
            }

            val display = if (item.type == Material.AIR || item.amount <= 0) {
                createEmptyValuableItem(index)
            } else {
                createDisplayItem(item)
            }

            gui.setItem(slot, GuiItem(display) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                if (it.isShiftClick && it.isLeftClick) {
                    if (DataStore.get(player.uniqueId).getCollectedAmount(sellMaterial) > 0L) {
                        MasteryManager.openValuableGui(player, sellMaterial)
                    } else {
                        player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                    }
                    return@GuiItem
                }
                val result = StorageManager.sellType(player, sellMaterial)
                if (result.totalValue > 0L) {
                    player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1f)
                    player.sendMessage(
                        TextUtil.colorize(
                            "&aSold &7x${TextUtil.formatNum(result.totalItems)} &afor &b${TextUtil.formatNum(result.totalValue)} ${ItemManager.COIN_NAME_PLURAL}"
                        )
                    )
                    ScoreboardManager.updateBoard(player)
                } else {
                    player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                }
                render()
            })
        }
    }

    private fun renderToolbar() {
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }

        for (slot in 0 until 9) {
            gui.setItem(slot, GuiItem(filler))
        }

        val sellPreview = StorageManager.sellAllPreview(player)
        gui.setItem(
            SELL_ALL_SLOT,
            GuiItem(
                createToolbarItem(
                    Material.BEACON,
                    "&aSell Whole ${storageName()}",
                    listOf(
                        "&7Click here to sell all",
                        "&7your ${storageName().lowercase()} content.",
                        "",
                        "&7Stored items: &f${TextUtil.formatNum(sellPreview.totalItems)}",
                        "&7Value: &a${TextUtil.formatNum(sellPreview.totalValue)} ${ItemManager.COIN_NAME_PLURAL}"
                    )
                )
            ) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                if (CombatManager.isInCombat(player)) {
                    player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                    player.sendMessage(TextUtil.colorize("&cYou cannot sell all while in combat."))
                    render()
                    return@GuiItem
                }
                val result = StorageManager.sellAll(player)
                if (result.totalValue <= 0L) {
                    player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                } else {
                    player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1f)
                    player.sendMessage(
                        TextUtil.colorize(
                            "&aSold &7x${TextUtil.formatNum(result.totalItems)} items &afor &b${TextUtil.formatNum(result.totalValue)} ${ItemManager.COIN_NAME_PLURAL}"
                        )
                    )
                    ScoreboardManager.updateBoard(player)
                }
                render()
            }
        )

        gui.setItem(ACTIVE_MULTIPLIERS_SLOT, GuiItem(createActiveMultipliersItem()))
    }

    private fun createDisplayItem(item: ItemStack): ItemStack {
        val material = item.type
        val amount = item.amount
        val sellValue = StorageManager.getSellValue(player, material) ?: 0L
        val totalValue = (sellValue * amount * StorageManager.resolveSellMultiplier(player)).toLong()
        val template = MineManager.createValuableItem(material, 1)

        return ItemStack(material, amount.coerceIn(1, material.maxStackSize)).apply {
            editMeta { meta ->
                meta.displayName(template.itemMeta.displayName())
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Quantity: &f${TextUtil.formatNum(amount)}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Sell price: &a${TextUtil.formatNum(totalValue)} ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Left click: &aSell All").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Shift-left-click: &dView mastery").decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createEmptyValuableItem(index: Int): ItemStack {
        val material = MineManager.valuableDrops[index]
        val template = MineManager.createValuableItem(material, 1)
        return ItemStack(Material.STONE_BUTTON).apply {
            editMeta { meta ->
                meta.displayName(template.itemMeta.displayName())
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Quantity: &f0").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Sell price: &a0 ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Left click: &aSell All").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Shift-left-click: &dView mastery").decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createToolbarItem(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent(name).decoration(TextDecoration.ITALIC, false))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
                }
            }
        }
    }

    private fun createActiveMultipliersItem(): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val eventMultiplier = if (BossbarManager.hasActiveSellMultiplier()) BossbarManager.multiplier else 1.0
        val deathpackValueMultiplier = if (data.hasEnabledPvp) StorageManager.getDeathpackValueMultiplier(player) else 1.0
        val baseSellMultiplier = KitManager.getEffectiveSellMultiplier(player)
        val finalSellMultiplier = StorageManager.resolveSellMultiplier(player)
        val totalPreviewValue = StorageManager.sellAllPreview(player).totalValue

        val lore = mutableListOf(
            "&7Permanent multiplier: &b${TextUtil.formatNum(data.multiplier)}x"
        )
        if (data.hasEnabledPvp) {
            lore += "&7Deathpack value multiplier: &c+${TextUtil.formatNum(deathpackValueMultiplier - 1.0)}x"
        }
        if (BossbarManager.hasActiveSellMultiplier()) {
            lore += "&7Event sell multiplier: &d${TextUtil.formatNum(eventMultiplier)}x"
        }

        lore += ""
        lore += "&7Sell multiplier: &f${TextUtil.formatNum(baseSellMultiplier)}x"
        if (BossbarManager.hasActiveSellMultiplier()) {
            lore += "&7With active event: &f${TextUtil.formatNum(finalSellMultiplier)}x"
        }
        if (data.hasEnabledPvp) {
            lore += "&7Current ${storageName().lowercase()} value: &a${TextUtil.formatNum(totalPreviewValue)} ${ItemManager.COIN_NAME_PLURAL}"
        }

        return createToolbarItem(Material.NETHER_STAR, "&eActive Multipliers", lore)
    }

    private fun storageTitle(): String = "&8${storageName()} &7(${player.name})"

    private fun storageName(): String = if (DataStore.get(player.uniqueId).hasEnabledPvp) "Deathpack" else "Backpack"

    private fun isStorable(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        return item.type in MineManager.valuableDrops
    }
}
