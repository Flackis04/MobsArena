package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class RankUpGui : Listener {
    private val activeViews = mutableMapOf<Player, ActiveView>()

    fun open(player: Player) = open(player, ViewMode.RANKUP)

    fun openRebirth(player: Player) = open(player, ViewMode.REBIRTH)

    private fun open(player: Player, mode: ViewMode) {
        val gui = Gui.gui()
            .title(Component.text(if (mode == ViewMode.REBIRTH) "Rebirth" else "Rankup"))
            .rows(3)
            .disableAllInteractions()
            .create()

        activeViews[player] = ActiveView(gui, mode)
        refreshGui(player)
        gui.setCloseGuiAction {
            activeViews.remove(player)
            GuiClickDebounce.clear(player)
        }

        gui.open(player)
    }

    private fun refreshGui(player: Player) {
        val activeView = activeViews[player] ?: return
        val gui = activeView.gui
        gui.updateItem(13, GuiItem(createRankupItem(player, activeView.mode)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val data = DataStore.get(player.uniqueId)
            if (activeView.mode == ViewMode.REBIRTH) {
                if (!checkRebirthRequirementsSilently(data)) {
                    activeView.rebirthConfirmationArmed = false
                    refreshGui(player)
                    return@GuiItem
                }
                activeView.rebirthConfirmationArmed = false
                if (performRebirth(player)) {
                    refreshGui(player)
                }
                return@GuiItem
            }

            if (data.rank >= LevelManager.getRankMaxLevel(data)) {
                if (!checkRebirthRequirementsSilently(data)) {
                    activeView.rebirthConfirmationArmed = false
                    refreshGui(player)
                    return@GuiItem
                }
                if (!activeView.rebirthConfirmationArmed) {
                    activeView.rebirthConfirmationArmed = true
                    player.sendMessage(TextUtil.colorize("&eClick rebirth again to confirm."))
                    player.playSound(player.location, "block.note_block.hat", 1f, 1f)
                    refreshGui(player)
                    return@GuiItem
                }
                if (performRebirth(player)) {
                    activeView.rebirthConfirmationArmed = false
                    refreshGui(player)
                    return@GuiItem
                }
                activeView.rebirthConfirmationArmed = false
                refreshGui(player)
                return@GuiItem
            }

            val cost = getRankupCost(data)
            if (data.balance < cost) {
                player.sendMessage(TextUtil.colorize("&cYou can't afford this rankup."))
                player.playSound(player.location, "block.note_block.bass", 1f, 1f)
                refreshGui(player)
                return@GuiItem
            }

            data.balance -= cost
            data.rank += 1
            data.multiplier += KitManager.KIT_SET_MULTIPLIER_PER_TIER
            activeView.rebirthConfirmationArmed = false
            KitManager.equipHead(player, data.rank)
            KitManager.equipArmor(player, data.rank)
            val dynamiteReward = getRankupDynamiteReward(data.rank)
            if (dynamiteReward > 0) {
                KitManager.giveDynamite(player, dynamiteReward)
            }

            player.sendMessage(TextUtil.colorize("&aRanked up to &bRank ${formatDisplayedRank(data, data.rank)}&a."))
            if (dynamiteReward > 0) {
                player.sendMessage(TextUtil.colorize("&aRankup reward: &cx$dynamiteReward Dynamite&a."))
            }
            player.playSound(player.location, "entity.player.levelup", 1f, 1f)
            ScoreboardManager.updateBoard(player)
            if (data.rank >= LevelManager.getRankMaxLevel(data)) {
                openRebirth(player)
                return@GuiItem
            }
            refreshGui(player)
        })
        gui.update()
    }

    private fun createRankupItem(player: Player, mode: ViewMode): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val item = ItemStack(Material.NETHER_STAR)
        val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
        val nextStartingUpgradeLevel = 1 + (data.rebirth + 1)
        val canRebirthToOne = data.rebirth == 0 &&
            data.rank >= LevelManager.getRankMaxLevel(data) &&
            data.level >= requiredLevel
        val isRebirthView = mode == ViewMode.REBIRTH
        val canRebirth = checkRebirthRequirementsSilently(data)
        val confirmRebirth = activeViews[player]?.rebirthConfirmationArmed == true

        item.editMeta { meta ->
            if (isRebirthView || data.rank >= LevelManager.getRankMaxLevel(data)) {
                meta.displayName(
                    TextUtil.toComponent(if (confirmRebirth) "&c&lConfirm Rebirth" else "&b&lRebirth")
                        .decoration(TextDecoration.ITALIC, false)
                )
                val lore = mutableListOf<Component>(
                    TextUtil.toComponent("&cResets your rank, level, coins, upgrades, inventory, and storage.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Required Rank: &b${formatDisplayedRank(data, LevelManager.getRankMaxLevel(data))}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Required Level: &b$requiredLevel").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&aPerks: &d+${TextUtil.formatNum(REBIRTH_MULTIPLIER_PER_REBIRTH)}x permanent multiplier").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&aPerks: &dPermanent /upgrades start at level &b$nextStartingUpgradeLevel").decoration(TextDecoration.ITALIC, false),
                )
                if (canRebirthToOne) {
                    lore += TextUtil.toComponent("&7Unlocks autominer, jackhammer upgrade, and thunder upgrade.").decoration(TextDecoration.ITALIC, false)
                    lore += TextUtil.toComponent(if (confirmRebirth) "&c&lClick again to confirm" else "&a&lClick to rebirth").decoration(TextDecoration.ITALIC, false)
                } else if (canRebirth) {
                    lore += TextUtil.toComponent(if (confirmRebirth) "&c&lClick again to confirm" else "&a&lClick to rebirth").decoration(TextDecoration.ITALIC, false)
                }
                meta.lore(lore)
                return@editMeta
            }

            val cost = getRankupCost(data)
            val affordColor = if (data.balance >= cost) "&a" else "&c"
            val nextRank = data.rank + 1
            val dynamiteReward = getRankupDynamiteReward(nextRank)

            meta.displayName(TextUtil.toComponent("&d&lRankup").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&b${formatDisplayedRank(data, data.rank)} -> ${formatDisplayedRank(data, nextRank)}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("${affordColor}Cost: &b${TextUtil.formatNum(cost)} ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Reward: &b+${TextUtil.formatNum(KitManager.KIT_SET_MULTIPLIER_PER_TIER)}x &7Multiplier").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Reward: &cx$dynamiteReward Dynamite").decoration(TextDecoration.ITALIC, false),
                )
            )
        }

        return item
    }

    private fun getRankupCost(data: PlayerData): Long {
        val baseCost = LevelManager.upgradeRankCosts[data.rank + 1] ?: 0L
        val rebirthMultiplier = 1.0 + (data.rebirth * 0.55)
        return kotlin.math.ceil(baseCost * rebirthMultiplier).toLong()
    }

    private fun formatDisplayedRank(data: PlayerData, rank: Int): String {
        if (data.rebirth <= 0) return rank.toString()
        val rebirthLetter = ('A'.code + ((data.rebirth - 1) % 26)).toChar()
        return "$rebirthLetter$rank"
    }

    private fun getRankupDynamiteReward(rank: Int): Int = (rank / 4).coerceAtLeast(0)

    private fun checkRebirthRequirementsSilently(data: PlayerData): Boolean {
        if (data.rebirth >= 26) return false
        val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
        return data.rank >= LevelManager.getRankMaxLevel(data) && data.level >= requiredLevel
    }

    private data class ActiveView(
        val gui: Gui,
        val mode: ViewMode,
        var rebirthConfirmationArmed: Boolean = false
    )

    private enum class ViewMode {
        RANKUP,
        REBIRTH
    }
}
