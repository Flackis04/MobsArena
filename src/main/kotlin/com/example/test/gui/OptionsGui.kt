package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class OptionsGui : Listener {
    private val activeViews = mutableMapOf<Player, Gui>()
    private val optionSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30)

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Upgrade Options"))
            .rows(5)
            .disableAllInteractions()
            .create()

        activeViews[player] = gui
        gui.setCloseGuiAction {
            activeViews.remove(player)
            GuiClickDebounce.clear(player)
        }
        render(player)
        gui.open(player)
    }

    private fun render(player: Player) {
        val gui = activeViews[player] ?: return
        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }

        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }

        gui.setItem(4, GuiItem(createHeaderItem(player)))
        gui.setItem(40, GuiItem(createResetItem()) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val data = DataStore.get(player.uniqueId)
            UpgradeToggleManager.resetToDefaults(data)
            data.oreBoostActive = false
            data.excavatorActive = false
            RetentionUpgradeManager.reset(player.uniqueId)
            player.playSound(player.location, "entity.player.levelup", 0.9f, 1.15f)
            ScoreboardManager.updateBoard(player)
            render(player)
        })

        UpgradeToggleManager.definitions.forEachIndexed { index, definition ->
            val slot = optionSlots.getOrNull(index) ?: return@forEachIndexed
            gui.setItem(slot, GuiItem(createToggleItem(player, definition)) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                val data = DataStore.get(player.uniqueId)
                val enabled = !UpgradeToggleManager.isEnabled(data, definition.key)
                UpgradeToggleManager.setEnabled(data, definition.key, enabled)
                if (!enabled && definition.key == "oreBoost") {
                    data.oreBoostActive = false
                }
                if (!enabled && definition.key == "excavator") {
                    data.excavatorActive = false
                }
                if (!enabled && definition.key == "combo") {
                    RetentionUpgradeManager.reset(player.uniqueId)
                }
                player.playSound(
                    player.location,
                    if (enabled) "entity.player.levelup" else "ui.button.click",
                    0.8f,
                    if (enabled) 1.15f else 0.85f
                )
                ScoreboardManager.updateBoard(player)
                render(player)
            })
        }

        gui.update()
    }

    private fun createHeaderItem(player: Player): ItemStack {
        val enabledCount = UpgradeToggleManager.definitions.count { UpgradeToggleManager.isEnabled(player, it.key) }
        return ItemStack(Material.COMPARATOR).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&f&lUpgrade Options").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Toggle permanent upgrade effects on or off.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Enabled: &f$enabledCount/${UpgradeToggleManager.definitions.size}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Mine Richness changes apply on your next mine reset.").decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createResetItem(): ItemStack =
        ItemStack(Material.LIME_BANNER).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&a&lEnable All").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Turn every permanent upgrade effect back on.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&eClick to reset your options").decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }

    private fun createToggleItem(player: Player, definition: UpgradeToggleDefinition): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val enabled = UpgradeToggleManager.isEnabled(data, definition.key)
        val item = ItemStack(definition.material)
        val levelLine = getLevelLine(data, definition.key)

        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent(
                    if (enabled) "&a${definition.displayName}" else "&c${definition.displayName}"
                ).decoration(TextDecoration.ITALIC, false)
            )
            val lore = mutableListOf(
                "&7${definition.description}",
                "",
                levelLine,
                "&7Status: ${if (enabled) "&aEnabled" else "&cDisabled"}"
            )
            if (definition.key == "oreFrequency") {
                lore += "&8Applies on next mine reset."
            }
            lore += ""
            lore += "&eClick to toggle"
            meta.lore(lore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
        }

        return item
    }

    private fun getLevelLine(data: PlayerData, key: String): String =
        when (key) {
            "multiBreak" -> "&7Level: &f${data.multiBreakLevel}/${data.multiBreakMaxLevel}"
            "oreBoost" -> "&7Level: &f${data.oreBoostLevel}/${data.oreBoostMaxLevel}"
            "fortune" -> "&7Level: &f${data.fortuneLevel}/${data.fortuneMaxLevel}"
            "excavator" -> "&7Level: &f${data.excavatorLevel}/${data.excavatorMaxLevel}"
            "lightning" -> "&7Level: &f${data.lightningLevel}/${data.lightningMaxLevel}"
            "virtualJackhammer" -> "&7Level: &f${data.virtualJackhammerLevel}/${data.virtualJackhammerMaxLevel}"
            "excavatorEfficiency" -> "&7Level: &f${data.excavatorEfficiencyLevel}/${data.excavatorEfficiencyMaxLevel}"
            "xpGain" -> "&7Level: &f${data.xpGainLevel}/${data.xpGainMaxLevel}"
            "oreFrequency" -> "&7Level: &f${data.oreFrequencyLevel}/${data.oreFrequencyMaxLevel}"
            "scrollFinder" -> "&7Level: &f${data.scrollFinderLevel}/${data.scrollFinderMaxLevel}"
            "backpack" -> "&7Level: &f${data.backpackLevel}/${data.backpackMaxLevel}"
            "sellMultiplier" -> "&7Level: &f${data.sellMultiplierLevel}/${data.sellMultiplierMaxLevel}"
            "tokenFinder" -> "&7Level: &f${data.tokenFinderLevel}/${data.tokenFinderMaxLevel}"
            "keyFinder" -> "&7Level: &f${data.keyFinderLevel}/${data.keyFinderMaxLevel}"
            "jackpot" -> "&7Level: &f${data.jackpotLevel}/${data.jackpotMaxLevel}"
            "combo" -> "&7Level: &f${data.comboLevel}/${data.comboMaxLevel}"
            "procPower" -> "&7Level: &f${data.procPowerLevel}/${data.procPowerMaxLevel}"
            else -> "&7Level: &f-"
        }
}
