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

class PrefsGui(
    private val optionsGui: OptionsGui,
    private val animationGui: AnimationGui
) : Listener {
    private val activeViews = mutableMapOf<Player, Gui>()

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Preferences"))
            .rows(3)
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
        gui.setItem(11, GuiItem(createOptionsButton(player)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            optionsGui.open(player)
        })
        gui.setItem(15, GuiItem(createAnimationsButton(player)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            animationGui.open(player)
        })
        gui.update()
    }

    private fun createHeaderItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val enabledCount = UpgradeToggleManager.definitions.count { UpgradeToggleManager.isEnabled(player, it.key) }
        return ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&f&lPreferences").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Choose what you want to configure.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Animations: ${if (data.animationsEnabled) "&aEnabled" else "&cDisabled"}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Rankup toggles enabled: &f$enabledCount/${UpgradeToggleManager.definitions.size}").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    private fun createOptionsButton(player: Player): ItemStack {
        val enabledCount = UpgradeToggleManager.definitions.count { UpgradeToggleManager.isEnabled(player, it.key) }
        return ItemStack(Material.COMPARATOR).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&a&lOptions").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Open your rankup upgrade toggle menu.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Enabled: &f$enabledCount/${UpgradeToggleManager.definitions.size}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&eClick to open").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    private fun createAnimationsButton(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        return ItemStack(Material.AMETHYST_SHARD).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&b&lAnimations").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Open your block break animation settings.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Status: ${if (data.animationsEnabled) "&aEnabled" else "&cDisabled"}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&eClick to open").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }
}
