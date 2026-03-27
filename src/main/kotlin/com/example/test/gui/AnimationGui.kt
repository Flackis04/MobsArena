package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class AnimationGui : Listener {
    private companion object {
        const val TOGGLE_SLOT = 10
        const val EXTRA_BLOCK_DELAY_SLOT = 12
        const val DURATION_SLOT = 14
        const val MODE_SLOT = 16
    }

    private val activeViews = mutableMapOf<Player, Gui>()

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(Component.text("Animation Settings"))
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

    private fun createToggleItem(player: Player): ItemStack {
        val enabled = DataStore.get(player.uniqueId).animationsEnabled
        val item = ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE)

        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent(
                    if (enabled) "&a&lAnimations: ON" else "&c&lAnimations: OFF"
                ).decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Toggle block break animations for your mining.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Current setting: ${if (enabled) "&aEnabled" else "&cDisabled"}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&eClick to switch").decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        return item
    }

    private fun createExtraBlockDelayItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val item = ItemStack(Material.CLOCK)

        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("&e&lExtra Block Delay: &f${data.animationExtraBlockDelayTicks}t").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Delay between follow-up multi-break blocks.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Applies after the first block breaks.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Range: &f${AnimationManager.MIN_EXTRA_BLOCK_DELAY_TICKS}t-${AnimationManager.MAX_EXTRA_BLOCK_DELAY_TICKS}t").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&aLeft-click: &fIncrease").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&cRight-click: &fDecrease").decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        return item
    }

    private fun createDurationItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val item = ItemStack(Material.AMETHYST_SHARD)

        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("&b&lDuration: &f${data.animationDurationTicks}t").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7How long the block break animation runs.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Range: &f${AnimationManager.MIN_BLOCK_BREAK_DURATION_TICKS}t-${AnimationManager.MAX_BLOCK_BREAK_DURATION_TICKS}t").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&aLeft-click: &fIncrease").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&cRight-click: &fDecrease").decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        return item
    }

    private fun createModeItem(player: Player): ItemStack {
        val linearMode = DataStore.get(player.uniqueId).animationLinearMode
        val item = ItemStack(if (linearMode) Material.SMOOTH_STONE else Material.MAGMA_CREAM)

        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent(
                    if (linearMode) "&f&lAnimation Type: &aLinear" else "&f&lAnimation Type: &6Classic"
                ).decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Linear is the default straight interpolation.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Classic uses the previous eased block-break curve.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&eClick to switch").decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        return item
    }

    private fun render(player: Player) {
        val gui = activeViews[player] ?: return

        gui.updateItem(TOGGLE_SLOT, GuiItem(createToggleItem(player)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val data = DataStore.get(player.uniqueId)
            data.animationsEnabled = !data.animationsEnabled
            val enabled = data.animationsEnabled
            player.playSound(
                player.location,
                if (enabled) "entity.player.levelup" else "block.note_block.bass",
                1f,
                if (enabled) 1.2f else 0.8f
            )
            player.sendMessage(
                TextUtil.colorize(
                    if (enabled) "&aBlock break animations enabled." else "&cBlock break animations disabled."
                )
            )
            render(player)
        })

        gui.updateItem(EXTRA_BLOCK_DELAY_SLOT, GuiItem(createExtraBlockDelayItem(player)) { event ->
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val delta = when {
                event.isLeftClick -> 1L
                event.isRightClick -> -1L
                else -> return@GuiItem
            }
            val data = DataStore.get(player.uniqueId)
            data.animationExtraBlockDelayTicks =
                AnimationManager.clampExtraBlockDelayTicks(data.animationExtraBlockDelayTicks + delta)
            player.playSound(player.location, "ui.button.click", 1f, 1f)
            render(player)
        })

        gui.updateItem(DURATION_SLOT, GuiItem(createDurationItem(player)) { event ->
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val delta = when {
                event.isLeftClick -> 1
                event.isRightClick -> -1
                else -> return@GuiItem
            }
            val data = DataStore.get(player.uniqueId)
            data.animationDurationTicks =
                AnimationManager.clampDurationTicks(data.animationDurationTicks + delta)
            player.playSound(player.location, "ui.button.click", 1f, 1f)
            render(player)
        })

        gui.updateItem(MODE_SLOT, GuiItem(createModeItem(player)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val data = DataStore.get(player.uniqueId)
            data.animationLinearMode = !data.animationLinearMode
            player.playSound(player.location, "ui.button.click", 1f, if (data.animationLinearMode) 1.15f else 0.85f)
            render(player)
        })
        gui.update()
    }
}
