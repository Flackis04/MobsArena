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

class LeaderboardsGui : Listener {
    private data class CategoryDefinition(
        val key: String,
        val title: String,
        val material: Material
    )

    private val categories = listOf(
        CategoryDefinition("balance", "&6&lCoins", Material.GOLD_INGOT),
        CategoryDefinition("rank", "&d&lRank", Material.NETHER_STAR),
        CategoryDefinition("tokens", "&b&lTokens", Material.PRISMARINE_CRYSTALS),
        CategoryDefinition("blocksMined", "&9&lBlocks Mined", Material.DIAMOND_PICKAXE),
        CategoryDefinition("level", "&a&lLevel", Material.EXPERIENCE_BOTTLE),
        CategoryDefinition("kills", "&c&lKills", Material.IRON_SWORD),
        CategoryDefinition("deaths", "&7&lDeaths", Material.SKELETON_SKULL),
        CategoryDefinition("playtime", "&3&lPlaytime", Material.CLOCK),
        CategoryDefinition("clans", "<#FF7AE0>&lClan Top", Material.PINK_BANNER)
    )
    private val categorySlots = listOf(10, 11, 12, 13, 14, 15, 16, 21, 22)
    private val activeViews = mutableMapOf<Player, Gui>()

    fun open(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Leaderboards"))
            .rows(4)
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

        gui.setItem(4, GuiItem(createHeaderItem()))
        categories.forEachIndexed { index, category ->
            val slot = categorySlots.getOrNull(index) ?: return@forEachIndexed
            gui.setItem(slot, GuiItem(createCategoryItem(player, category)))
        }
        gui.update()
    }

    private fun createHeaderItem(): ItemStack =
        ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&f&lLeaderboards").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Browse the top 10 for every tracked category.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Your current place is always shown at the bottom.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun createCategoryItem(player: Player, category: CategoryDefinition): ItemStack {
        val standing = if (category.key == "clans") {
            StatsManager.getClanLeaderboard(player)
        } else {
            StatsManager.getPlayerLeaderboard(category.key, player)
        }

        return ItemStack(category.material).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent(category.title).decoration(TextDecoration.ITALIC, false))
                val lore = mutableListOf<Component>()
                lore += TextUtil.toComponent("&7Top 10").decoration(TextDecoration.ITALIC, false)
                standing.entries.forEachIndexed { index, entry ->
                    val rankPrefix = when (index + 1) {
                        1 -> "<#FFD700>1."
                        2 -> "<#C0C0C0>2."
                        3 -> "<#CD7F32>3."
                        else -> "&8${index + 1}."
                    }
                    lore += TextUtil.toComponent("$rankPrefix &7${entry.name} - &b${entry.value}").decoration(TextDecoration.ITALIC, false)
                }
                if (standing.entries.isEmpty()) {
                    lore += TextUtil.toComponent("&8No entries yet.").decoration(TextDecoration.ITALIC, false)
                }
                if (standing.viewerPlace != null && standing.viewerValue != null) {
                    lore += TextUtil.toComponent("").decoration(TextDecoration.ITALIC, false)
                    lore += TextUtil.toComponent("&7Your Place").decoration(TextDecoration.ITALIC, false)
                    lore += TextUtil.toComponent("&8${standing.viewerPlace}. &7${player.name} - &b${standing.viewerValue}").decoration(TextDecoration.ITALIC, false)
                }
                meta.lore(lore)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }
}
